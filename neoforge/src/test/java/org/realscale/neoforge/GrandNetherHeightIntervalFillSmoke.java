package org.realscale.neoforge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Test.29: exhaustive strict comparison with the frozen Test.28 297x19
 * integer-height scan, with a separate fractional interval oracle.
 *
 * Benchmark is a reproducible *table-construction microbenchmark*, NOT
 * evidence of Minecraft chunk/s, TPS, GC or end-to-end seed throughput.
 */
public final class GrandNetherHeightIntervalFillSmoke {
    private GrandNetherHeightIntervalFillSmoke(){}
    private record Component(double min,double max,int bit){}
    private static volatile int sink;
    private static long checked,randomChecks;

    private static Field field(Class<?> type,String name)throws Exception {
        Field f=type.getDeclaredField(name);f.setAccessible(true);return f;
    }
    private static Object get(Object owner,String name)throws Exception {
        return field(owner.getClass(),name).get(owner);
    }
    private static void yes(boolean ok,String message) {
        if(!ok)throw new AssertionError(message);
    }
    private static Component segment(Object tube,int bit)throws Exception {
        return new Component(
                field(tube.getClass(),"minY").getDouble(tube),
                field(tube.getClass(),"maxY").getDouble(tube),bit);
    }
    private static Component room(GrandNetherCaveNetwork.Hall h,int bit) {
        return new Component(h.y()-h.radiusY(),h.y()+h.radiusY(),bit);
    }
    private static Component[] extract(Object cell)throws Exception {
        var components=new ArrayList<Component>(19);
        var halls=(GrandNetherCaveNetwork.Hall[][])get(cell,"halls");
        var sides=(GrandNetherCaveNetwork.Hall[][])get(cell,"satellites");
        var tubes=(Object[][])get(cell,"corridors");
        var branches=(Object[][])get(cell,"branches");
        for(int layer=0;layer<2;layer++) {
            int shift=layer*9;
            components.add(room(halls[layer][0],1<<shift));
            for(int direction=0;direction<4;direction++)
                components.add(segment(tubes[layer][direction],
                        1<<(shift+1+direction)));
            for(int side=0;side<2;side++) {
                components.add(room(sides[layer][side],1<<(shift+5+side*2)));
                components.add(segment(branches[layer][side],
                        1<<(shift+6+side*2)));
            }
        }
        components.add(segment(get(cell,"interlayer"),1<<18));
        yes(components.size()==19,"Expected 19 individual reach components");
        return components.toArray(Component[]::new);
    }

    // Frozen Test.28 rule: check every component at every source integer Y.
    private static void oldScan(Component[] components,int[] bits) {
        Arrays.fill(bits,0);
        for(int y=GrandNetherCaveNetwork.MIN_Y;
                y<=GrandNetherCaveNetwork.MAX_Y;y++) {
            double lowY=(double)y-2.0,highY=(double)y+2.0;
            int mask=0;
            for(Component c:components)
                if(highY>=c.min() && lowY<=c.max())mask|=c.bit();
            bits[y-GrandNetherCaveNetwork.MIN_Y]=mask;
        }
    }
    public static void main(String[] args)throws Exception {
        Class<?> net=GrandNetherCaveNetwork.class;
        ThreadLocal<?> local=(ThreadLocal<?>)field(net,"CACHED_HALLS").get(null);
        Object regions=local.get();
        Method getRegion=regions.getClass().getDeclaredMethod("get",int.class,int.class);
        getRegion.setAccessible(true);
        Class<?> cellType=Class.forName(net.getName()+"$CellCache");
        Method populate=net.getDeclaredMethod("populateHeightComponents",
                cellType,int[].class);
        populate.setAccessible(true);
        Method fill=net.getDeclaredMethod("markHeightInterval",int[].class,
                double.class,double.class,int.class);
        fill.setAccessible(true);

        int graphCells=0;
        for(int gx=-5;gx<=5;gx++)for(int gz=-5;gz<=5;gz++) {
            Object cell=getRegion.invoke(regions,gx,gz);
            Component[] components=extract(cell);
            int[] prior=new int[297],candidate=new int[297];
            oldScan(components,prior);
            populate.invoke(null,cell,candidate);
            yes(Arrays.equals(prior,candidate),
                    "Whole-height old/new table differs in 256-cell "+gx+","+gz);
            graphCells++;
            checked+=297L*19;
        }

        // Fractional reach/tangent/negative and near-range bounds validate
        // the conservative floor/ceil expansion independent of the current
        // all-integer seeded cave coordinates.
        Random rng=new Random(0x29bead24L);
        int[] tested=new int[297];
        for(int n=0;n<4500;n++) {
            Arrays.fill(tested,0);
            double lo=(rng.nextInt(1500)-600)+rng.nextDouble();
            double hi=lo+rng.nextDouble()*240;
            if(n%11==0)hi=lo; // exact tangent/single-point bounds
            if(n%17==0) {lo=144.0-2.0;hi=lo;}
            if(n%19==0) {lo=440.0+2.0;hi=lo;}
            fill.invoke(null,tested,lo,hi,1);
            for(int y=144;y<=440;y++) {
                int expected=((double)y+2.0>=lo && (double)y-2.0<=hi)?1:0;
                yes(tested[y-144]==expected,
                        "Fractional interval branch changed at Y="+y
                        +" min="+lo+" max="+hi);
                randomChecks++;
            }
        }

        // Construction-only A/B benchmark. Same Java worker/JIT/session,
        // identical component graphs and reused preallocated arrays.
        // Warm both branches before timing and report raw ns, NOT speedup
        // percentages or end-to-end real Minecraft new-chunk claims.
        // The production RegionCache has only four re-used snapshots:
        // old references collected for 121 regions would be stale after
        // eviction. Time one live cell with its own *matching* geometry
        // repeatedly, not different frozen data from an earlier occupant.
        Object benchCell=getRegion.invoke(regions,0,0);
        Component[] benchGraph=extract(benchCell);
        int[] benchBuffer=new int[297];
        for(int warm=0;warm<240;warm++) {
            oldScan(benchGraph,benchBuffer);
            Arrays.fill(benchBuffer,0);
            populate.invoke(null,benchCell,benchBuffer);
        }
        long oldNanos=0,newNanos=0;
        for(int pass=0;pass<1800;pass++) {
            long start=System.nanoTime();
            oldScan(benchGraph,benchBuffer);
            oldNanos+=System.nanoTime()-start;
            Arrays.fill(benchBuffer,0);
            start=System.nanoTime();
            populate.invoke(null,benchCell,benchBuffer);
            newNanos+=System.nanoTime()-start;
            sink^=benchBuffer[pass%297];
        }
        yes(checked>600_000 && randomChecks>1_300_000,
                "Insufficient graph or fractional-boundary coverage");
        System.out.println("NETHER_HEIGHT_INTERVAL_FILL_RAW_MASK_PASS graphCells="
                +graphCells+" originalComparisons="+checked
                +" fractionalBoundaryChecks="+randomChecks
                +" frozenScan_ns="+oldNanos+" candidateIntervalFill_ns="
                +newNanos+" microbenchmarkOnly=true");
    }
}
