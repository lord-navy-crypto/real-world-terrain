package org.realscale.neoforge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Test.31: independent reconstruction of every bit of the source-column
 * giant-cave height envelope, with raw-double-bit full-graph checks for
 * safely excluded seeded warps. Existing small-cave and entrance logic
 * lives in the unchanged caller and is never skipped.
 */
public final class GrandOverworldHeightMaskParitySmoke {
    private GrandOverworldHeightMaskParitySmoke(){}
    private record Bound(double min,double max,long bit){}
    private static long tableChecks,maskChecks,pruned,positive,edges,zeros,
            fullStrengthSamples;
    private static final double[] WARP={-1.0,-0.63,0.0,0.48,1.0};
    private static Field field(Class<?> c,String name)throws Exception {
        Field f=c.getDeclaredField(name);f.setAccessible(true);return f;
    }
    private static Object get(Object o,String name)throws Exception {
        return field(o.getClass(),name).get(o);
    }
    private static void yes(boolean ok,String reason){
        if(!ok)throw new AssertionError(reason);
    }
    private static Bound hall(GrandOverworldCaveNetwork.Hall h,long bit){
        return new Bound(h.y()-h.radiusY(),h.y()+h.radiusY(),bit);
    }
    private static Bound tube(Object t,long bit)throws Exception {
        if(t==null)return null;
        Field lo=field(t.getClass(),"minY");
        Field hi=field(t.getClass(),"maxY");
        return new Bound(lo.getDouble(t),hi.getDouble(t),bit);
    }
    private static Bound[] geometry(Object c)throws Exception {
        List<Bound> bs=new ArrayList<>();
        var halls=(GrandOverworldCaveNetwork.Hall[][])get(c,"halls");
        var galleries=(GrandOverworldCaveNetwork.Hall[][])get(c,"galleries");
        var corridors=(Object[][])get(c,"corridorTubes");
        var sides=(Object[][])get(c,"sideTubes");
        var shafts=(Object[])get(c,"shaftTubes");
        var arteries=(Object[][][])get(c,"arteryTubes");
        for(int layer=0;layer<4;layer++) {
            int shift=layer*9;
            bs.add(hall(halls[layer][0],1L<<shift));
            for(int dir=0;dir<4;dir++)
                bs.add(tube(corridors[layer][dir],1L<<(shift+1+dir)));
            for(int gal=0;gal<2;gal++){
                bs.add(hall(galleries[layer][gal],1L<<(shift+5+2*gal)));
                bs.add(tube(sides[layer][gal],1L<<(shift+6+2*gal)));
            }
        }
        for(int layer=0;layer<3;layer++)
            bs.add(tube(shafts[layer],1L<<(36+layer)));
        for(int tier=0;tier<2;tier++)for(int axis=0;axis<2;axis++)
            for(int group=0;group<2;group++)
                bs.add(tube(arteries[tier][axis][group],
                        1L<<(39+tier*4+axis*2+group)));
        yes(bs.size()==47,"Expected 47 component slots");
        return bs.toArray(Bound[]::new);
    }
    private static long frozenHeight(Bound[] bounds,int y){
        long bits=0L;
        double minY=(double)y-2.0, maxY=(double)y+2.0;
        for(Bound b:bounds)
            if(b!=null && maxY>=b.min() && minY<=b.max())bits|=b.bit();
        return bits;
    }
    private static void check(int x,int y,int z,boolean density)throws Exception {
        long horizontal=y>GrandOverworldCaveNetwork.MAX_GEOMETRY_Y+2
                ? 0L:GrandOverworldCaveNetwork.componentMaskForColumn(x,z);
        long observed=GrandOverworldCaveNetwork.componentMaskForColumnAtHeight(x,y,z);
        long expected=horizontal;
        if(horizontal>0 && y>=GrandOverworldCaveNetwork.MIN_Y
                && y<=GrandOverworldCaveNetwork.MAX_GEOMETRY_Y+2) {
            var thread=(ThreadLocal<?>)field(
                    GrandOverworldCaveNetwork.class,"LOCAL").get(null);
            Object region=thread.get();
            Method getRegion=region.getClass().getDeclaredMethod(
                    "get",int.class,int.class);
            getRegion.setAccessible(true);
            Object c=getRegion.invoke(region,Math.floorDiv(x,768),
                    Math.floorDiv(z,768));
            if((horizontal&frozenHeight(geometry(c),y))==0)expected=0L;
        }
        yes(expected==observed,"Overworld fused 47-bit Y mask changed at "
                +x+","+y+","+z+" original="+expected+" new="+observed);
        maskChecks++;
        if(horizontal==0L)zeros++;else if(horizontal==-1L)edges++;
        else if(observed==0L)pruned++;else positive++;
        if(density && y>=GrandOverworldCaveNetwork.MIN_Y
                && y<=GrandOverworldCaveNetwork.MAX_GEOMETRY_Y+2
                && horizontal>0 && observed==0L){
            for(double shift:WARP) {
                double wx=x+shift*6.0,wy=y+shift*2.0,
                        wz=z-shift*5.0;
                double value=GrandOverworldCaveNetwork.strength(wx,wy,wz);
                yes(Double.doubleToRawLongBits(value)==
                        Double.doubleToRawLongBits(0.0),
                        "Height filter removed true giant cavern at "
                        +x+","+y+","+z+" warp="+shift+" strength="+value);
                fullStrengthSamples++;
            }
        }
    }
    public static void main(String[] args)throws Exception {
        Class<?> net=GrandOverworldCaveNetwork.class;
        ThreadLocal<?> local=(ThreadLocal<?>)field(net,"LOCAL").get(null);
        local.remove();
        Object regions=local.get();
        Method select=regions.getClass().getDeclaredMethod("get",int.class,int.class);
        select.setAccessible(true);
        final int heightCount=GrandOverworldCaveNetwork.MAX_GEOMETRY_Y+3-
                GrandOverworldCaveNetwork.MIN_Y;
        for(int gx=-2;gx<=2;gx++)for(int gz=-2;gz<=2;gz++){
            Object c=select.invoke(regions,gx,gz);
            var halls=(GrandOverworldCaveNetwork.Hall[][])get(c,"halls");
            int x=(int)halls[1][0].x(),z=(int)halls[1][0].z();
            long horizontal=GrandOverworldCaveNetwork.componentMaskForColumn(x,z);
            yes(horizontal>0,"A known cave hall must be horizontally reachable");
            // Populates Y table only for a genuinely nonempty X/Z source.
            GrandOverworldCaveNetwork.componentMaskForColumnAtHeight(x,395,z);
            c=select.invoke(regions,gx,gz);
            long[] table=(long[])get(c,"heightComponents");
            yes(table!=null && table.length==heightCount,
                    "Lazy height table length/bound mismatch");
            yes(field(c.getClass(),"heightComponentsReady").getBoolean(c),
                    "Positive cave column did not build lazy table");
            Bound[] b=geometry(c);
            for(int y=GrandOverworldCaveNetwork.MIN_Y;
                    y<=GrandOverworldCaveNetwork.MAX_GEOMETRY_Y+2;y++){
                long old=frozenHeight(b,y);
                long now=table[y-GrandOverworldCaveNetwork.MIN_Y];
                yes(old==now,"Entire original 47-bit Y mask differs in graph "
                        +gx+","+gz+" y="+y+" original="+old+" new="+now);
                tableChecks++;
            }
        }
        Random rng=new Random(0x31261024L);
        int[] levels={-40,0,75,135,250,395,550,790,995,1200,1330,1470,
                1550,1590,1660,1698,1700,1702,1703};
        for(int i=0;i<1900;i++) {
            final int x,z;
            if(i%11==0){
                x=(rng.nextInt(21)-10)*768+(i%2==0?3:765);
                z=rng.nextInt(16001)-8000;
            }else if(i%11==1){
                x=rng.nextInt(16001)-8000;
                z=(rng.nextInt(21)-10)*768+(i%2==0?4:764);
            }else if(i%11==2){
                x=29_998_000-rng.nextInt(1000);
                z=-29_998_000+rng.nextInt(1000);
            }else{
                x=rng.nextInt(20001)-10000;
                z=rng.nextInt(20001)-10000;
            }
            for(int y:levels)check(x,y,z,i%9==0);
        }
        // Cave-rich columns guarantee active positive X/Z masks as well as
        // safely empty heights far above/below the individual galleries.
        for(int gx=-4;gx<=4;gx++)for(int gz=-4;gz<=4;gz++)
            for(int layer=0;layer<4;layer++){
                var h=GrandOverworldCaveNetwork.hall(gx,gz,layer);
                int x=(int)h.x(),z=(int)h.z();
                for(int y=-40;y<=1700;y+=83)
                    check(x,y,z,true);
                check(x,(int)h.y(),z,true);
            }
        yes(tableChecks>40_000 && maskChecks>40_000 && pruned>1000
                && positive>1000 && edges>1000 && fullStrengthSamples>5000,
                "Insufficient true-cave/empty-Y/edge/warp proof. table="
                +tableChecks+" probes="+maskChecks+" positive="+positive
                +" pruned="+pruned+" edges="+edges
                +" originalFullStrength="+fullStrengthSamples);
        System.out.println("OVERWORLD_47BIT_HEIGHT_MASK_PARITY_PASS heightTableBits="
                +tableChecks+" columnHeightComparisons="+maskChecks
                +" safeYRejects="+pruned+" active="+positive
                +" boundaryFallbacks="+edges
                +" exactZeroFullGraphProbes="+fullStrengthSamples);
    }
}
