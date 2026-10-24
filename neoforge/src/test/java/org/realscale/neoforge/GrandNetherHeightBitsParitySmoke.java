package org.realscale.neoforge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

/**
 * Original Test.27 19-component height comparison, independently replayed
 * against Test.28's region-local, lazily precomputed primitive Y bit tables.
 * A mismatch is a semantic regression even if a sampled cave looks intact.
 */
public final class GrandNetherHeightBitsParitySmoke {
    private static long checks,active,empty,prunable,lazyRebuilds;
    private static final Field MIN_TUBE,MAX_TUBE;
    static {
        try {
            Class<?> tube=Class.forName(
                    "org.realscale.neoforge.GrandNetherCaveNetwork$TubeSegment");
            MIN_TUBE=tube.getDeclaredField("minY");MIN_TUBE.setAccessible(true);
            MAX_TUBE=tube.getDeclaredField("maxY");MAX_TUBE.setAccessible(true);
        }catch(Exception e){throw new ExceptionInInitializerError(e);}
    }
    private static Field field(Class<?> type,String name)throws Exception {
        Field f=type.getDeclaredField(name);f.setAccessible(true);return f;
    }
    private static Object get(Object owner,String name)throws Exception {
        return field(owner.getClass(),name).get(owner);
    }
    private static void require(boolean ok,String message) {
        if(!ok)throw new AssertionError(message);
    }
    private static boolean touchTube(Object segment,double lo,double hi)
            throws Exception {
        return hi>=MIN_TUBE.getDouble(segment)
                && lo<=MAX_TUBE.getDouble(segment);
    }
    // Copy the exact inclusive comparisons and component-bit ordering of
    // the previous Test.27 per-voxel mayContainVoidAtHeight implementation.
    private static int frozenBits(Object c,int y)throws Exception {
        double lowY=(double)y-2.0,highY=(double)y+2.0;
        var halls=(GrandNetherCaveNetwork.Hall[][])get(c,"halls");
        var satellites=(GrandNetherCaveNetwork.Hall[][])get(c,"satellites");
        var corridors=(Object[][])get(c,"corridors");
        var branches=(Object[][])get(c,"branches");
        int bits=0;
        for(int layer=0;layer<2;layer++) {
            int shift=layer*9;
            var center=halls[layer][0];
            if(highY>=center.y()-center.radiusY() &&
                    lowY<=center.y()+center.radiusY())
                bits|=1<<shift;
            for(int direction=0;direction<4;direction++) {
                if(touchTube(corridors[layer][direction],lowY,highY))
                    bits|=1<<(shift+1+direction);
            }
            for(int gallery=0;gallery<2;gallery++) {
                var side=satellites[layer][gallery];
                if(highY>=side.y()-side.radiusY() &&
                        lowY<=side.y()+side.radiusY())
                    bits|=1<<(shift+5+gallery*2);
                if(touchTube(branches[layer][gallery],lowY,highY))
                    bits|=1<<(shift+6+gallery*2);
            }
        }
        if(touchTube(get(c,"interlayer"),lowY,highY))bits|=1<<18;
        return bits;
    }
    public static void main(String[] args)throws Exception {
        final Class<?> network=GrandNetherCaveNetwork.class;
        final ThreadLocal<?> worker=(ThreadLocal<?>)field(
                network,"CACHED_HALLS").get(null);
        worker.remove();
        Object regions=worker.get();
        Method select=regions.getClass().getDeclaredMethod("get",
                int.class,int.class);
        select.setAccessible(true);
        Random rng=new Random(0x28261024L);
        for(int gx=-3;gx<=3;gx++)for(int gz=-3;gz<=3;gz++) {
            Object cell=select.invoke(regions,gx,gz);
            int cx=gx*GrandNetherCaveNetwork.CELL+128;
            int cz=gz*GrandNetherCaveNetwork.CELL+128;
            int[] table=(int[])get(cell,"heightComponents");
            require(table.length==GrandNetherCaveNetwork.MAX_Y-
                    GrandNetherCaveNetwork.MIN_Y+1,
                    "Wrong bounded Nether height table");
            Field ready=field(cell.getClass(),"heightComponentsReady");
            require(!ready.getBoolean(cell),
                    "A newly loaded cell must not eagerly build all 297 heights");
            // Empty masks MUST NOT build the lazy table.
            require(!GrandNetherCaveNetwork.mayContainVoidAtHeight(
                    cx,205,cz,0),"Zero source mask retained a cave");
            require(!ready.getBoolean(cell),
                    "Zero source mask must not initialize the height table");
            // The first candidate creates the table, all following
            // per-Y samples reuse the same exact primitive bit array.
            GrandNetherCaveNetwork.mayContainVoidAtHeight(cx,205,cz,1);
            require(ready.getBoolean(cell),"Lazy first-use table missing");
            lazyRebuilds++;
            for(int y=GrandNetherCaveNetwork.MIN_Y;
                    y<=GrandNetherCaveNetwork.MAX_Y;y++) {
                int old=frozenBits(cell,y);
                int optimized=table[y-GrandNetherCaveNetwork.MIN_Y];
                require(old==optimized,
                        "19-bit frozen Y table mismatch cell="+gx+","+gz
                        +" y="+y+" old="+Integer.toHexString(old)
                        +" new="+Integer.toHexString(optimized));
                if(old==0)empty++;else active++;
                if(old!=0 && old!=0x7ffff)prunable++;
                checks++;
                // Every single component, random partial masks, and the
                // complete 19-bit source column must behave like Test.27.
                for(int bit=0;bit<19;bit++) {
                    int mask=1<<bit;
                    boolean truth=(old&mask)!=0;
                    require(GrandNetherCaveNetwork.mayContainVoidAtHeight(
                            cx,y,cz,mask)==truth,
                            "Single-component height gate changed at y="+y
                                    +" bit="+bit);
                    checks++;
                }
                for(int t=0;t<5;t++) {
                    int mask=t==0?0x7ffff:rng.nextInt(1<<19);
                    require(GrandNetherCaveNetwork.mayContainVoidAtHeight(
                            cx,y,cz,mask)==((old&mask)!=0),
                            "Mixed source column no longer matches original"
                                +" cell="+gx+","+gz+" y="+y);
                    checks++;
                }
            }
            require(!GrandNetherCaveNetwork.mayContainVoidAtHeight(
                    cx,GrandNetherCaveNetwork.MIN_Y,cz,0),
                    "Zero should reject even near Y boundary");
            require(GrandNetherCaveNetwork.mayContainVoidAtHeight(
                    cx,GrandNetherCaveNetwork.MIN_Y,cz,-1),
                    "Cell-edge sentinel must always keep original path");
            require(GrandNetherCaveNetwork.mayContainVoidAtHeight(
                    cx,GrandNetherCaveNetwork.MAX_Y+1,cz,1),
                    "Unknown out-of-band Y must conservatively keep old path");
        }
        // A single reused region slot MUST rebuild the table from the new
        // seeded graph instead of retaining bits from its prior occupant.
        worker.remove();
        regions=worker.get();
        Object a=select.invoke(regions,0,0);
        var ready=field(a.getClass(),"heightComponentsReady");
        GrandNetherCaveNetwork.mayContainVoidAtHeight(128,205,128,1);
        require(ready.getBoolean(a),"First cell's height table absent");
        // Four worker-local snapshots; visit many regions to force eviction.
        for(int n=1;n<=28;n++){
            int gx=n*17,gz=n*23;
            GrandNetherCaveNetwork.mayContainVoidAtHeight(
                    gx*256+128,205,gz*256+128,1);
        }
        Object cell=select.invoke(regions,0,0);
        require(cell!=null,"No region after deliberate evictions");
        require(!ready.getBoolean(cell),
                "Reconstructed region must not retain another cell's table");
        GrandNetherCaveNetwork.mayContainVoidAtHeight(128,205,128,1);
        require(ready.getBoolean(cell),"Rebuilt table not initialized");
        require(((int[])get(cell,"heightComponents"))[205-
                GrandNetherCaveNetwork.MIN_Y]==frozenBits(cell,205),
                "Evicted region's height bits were stale");

        require(checks>300_000 && active>500 && empty>500 &&
                    prunable>2000 && lazyRebuilds==49,
                "No sufficient altitude mask parity coverage: checked="
                +checks+" active="+active+" empty="+empty);
        System.out.println("NETHER_LAZY_HEIGHT_BITS_PARITY_PASS comparisons="
                +checks+" graphCells="+lazyRebuilds+" active="+active
                +" empty="+empty+" partiallyPrunable="+prunable);
    }
}
