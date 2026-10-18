package org.realscale.neoforge;

/** Check the restored measured DEM source and continuous 2048-block transition. */
public final class RealDemSeamlessMathSmoke {
    private RealDemSeamlessMathSmoke() {}
    private static final RealDemDataSource FROZEN_TILE=RealDemDataSource.loadMeasured();
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    /*
     * Frozen pre-optimization reference: DO NOT call cachedMeasuredHeight.
     * It recomputes all DEM interpolation at every new exterior column.
     */
    private static double clamp(double value,double low,double high) {
        return Math.max(low,Math.min(high,value));
    }
    private static double uncachedExtendedHeight(double x,double z) {
        RealDemDataSource tile=FROZEN_TILE;
        double cx=clamp(x,tile.originX(),tile.maxX());
        double cz=clamp(z,tile.originZ(),tile.maxZ());
        double cell=tile.cellMeters();
        double center=RealDemSeamlessMath.measuredHeight(cx,cz);
        double originalCenter=center;
        double dx=x-cx,dz=z-cz;
        if(dx!=0.0) {
            double neighbor=RealDemSeamlessMath.measuredHeight(
                    cx+(dx<0?cell:-cell),cz);
            double slope=(neighbor-center)/(dx<0?cell:-cell);
            center+=clamp(slope,-1.25,1.25)*
                    RealDemSeamlessMath.taperedOutsideDisplacement(dx);
        }
        if(dz!=0.0) {
            double neighbor=RealDemSeamlessMath.measuredHeight(
                    cx,cz+(dz<0?cell:-cell));
            double slope=(neighbor-originalCenter)/(dz<0?cell:-cell);
            center+=clamp(slope,-1.25,1.25)*
                    RealDemSeamlessMath.taperedOutsideDisplacement(dz);
        }
        return clamp(center,-64.0,2031.0);
    }
    private static void assertBitExact(double expected,double actual,
                                       int x,int z) {
        require(Double.doubleToRawLongBits(expected)==
                Double.doubleToRawLongBits(actual),
                "DEM edge cache changed saved terrain at "+x+","+z);
    }
    private static void checkDistantEdgeCacheParity() {
        int samples=0;
        // Wide annulus around the source, including all four corners,
        // 2048m fade boundaries, direct-map collisions and far procedural.
        for(int x=-3100;x<=3100;x+=41) {
            for(int z=-3100;z<=3100;z+=53) {
                double before=uncachedExtendedHeight(x,z);
                assertBitExact(before,RealDemSeamlessMath.extendedHeight(x,z),x,z);
                assertBitExact(before,RealDemSeamlessMath.cachedExtendedHeight(x,z),x,z);
                double cx=clamp(x,FROZEN_TILE.originX(),
                                 FROZEN_TILE.maxX());
                double cz=clamp(z,FROZEN_TILE.originZ(),
                                 FROZEN_TILE.maxZ());
                assertBitExact(RealDemSeamlessMath.measuredHeight(cx,cz),
                        RealDemSeamlessMath.cachedMeasuredHeight(cx,cz),x,z);
                samples++;
            }
        }
        // Same coordinates on independent worldgen workers; the per-thread
        // memo must never share or alter an answer.
        final java.util.concurrent.atomic.AtomicReference<Throwable> error =
                new java.util.concurrent.atomic.AtomicReference<>();
        Thread[] threads=new Thread[4];
        for(int worker=0;worker<threads.length;worker++) {
            final int wid=worker;
            threads[worker]=new Thread(()->{
                try {
                    for(int i=0;i<1000;i++) {
                        int x=-2250+((i*137+wid*43)%4501);
                        int z=-2250+((i*199+wid*67)%4501);
                        assertBitExact(uncachedExtendedHeight(x,z),
                                RealDemSeamlessMath.cachedExtendedHeight(x,z),x,z);
                    }
                }catch(Throwable t){ error.compareAndSet(null,t); }
            },"dem-worldgen-worker-"+worker);
            threads[worker].start();
        }
        try {
            for(Thread t:threads)t.join();
        }catch(InterruptedException ex){
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        }
        if(error.get()!=null)throw new AssertionError(
                "DEM per-worker memo parity regression",error.get());
        System.out.println("TVA_DEM_EDGE_MEMO_BIT_PARITY_PASS: samples="+samples+
                           " worker_threads=4");
    }

    public static void main(String[] args) {
        RealDemTile source = RealDemTile.load();
        int n = 0;
        for (int x = source.originX(); x <= source.maxX(); x += 32) {
            for (int z = source.originZ(); z <= source.maxZ(); z += 32) {
                double measured = source.worldSolidYAt(x, z);
                double actual = RealDemSeamlessMath.measuredHeight(x, z);
                require(Math.abs(measured - actual) < 1.e-6,
                        "DEM measurement changed at " + x + ", " + z);
                require(RealDemSeamlessMath.weightOutside(x,z,2048)==1.0,
                        "Measured tile lost full strength");
                n++;
            }
        }
        require(n == 256, "Missing measured DEM grid samples: " + n);
        require(RealDemSeamlessMath.weightOutside(source.maxX()+2048,0,2048)==0.0,
                "East transition fails to reach procedural terrain");
        require(RealDemSeamlessMath.weightOutside(source.originX()-2048,0,2048)==0.0,
                "West transition fails to reach procedural terrain");
        require(RealDemSeamlessMath.weightOutside(0,source.maxZ()+2048,2048)==0.0,
                "South transition fails to reach procedural terrain");
        require(RealDemSeamlessMath.weightOutside(0,source.originZ()-2048,2048)==0.0,
                "North transition fails to reach procedural terrain");
        for (int x = -2288; x <= 2288; x += 61) {
            for (int z = -2288; z <= 2288; z += 79) {
                double v = RealDemSeamlessMath.extendedHeight(x,z);
                require(Double.isFinite(v) && v >= -64 && v <= 2031,
                        "USGS exterior extrapolation exceeded height limits");
                require(Double.doubleToLongBits(v) == Double.doubleToLongBits(
                        RealDemSeamlessMath.cachedExtendedHeight(x,z)),
                        "USGS per-thread height cache changed computed result");
            }
        }
        // The optimized transition-weight lookup must reproduce every bit of
        // the original formula across the tile, fade ring and far field.
        for (int fade : new int[]{256, 768, 2048, 4096}) {
            for (int x = -4608; x <= 4608; x += 109) {
                for (int z = -4608; z <= 4608; z += 127) {
                    long original = Double.doubleToRawLongBits(
                            RealDemSeamlessMath.weightOutside(x, z, fade));
                    long cached = Double.doubleToRawLongBits(
                            RealDemSeamlessMath.cachedWeightOutside(x, z, fade));
                    require(original == cached,
                            "Fade weight changed at " + x + "," + z + " width=" + fade);
                }
            }
        }
        // Far-away exploration never pulls the small USGS pilot tile into
        // distant procedural regions. These signed and near-world-border
        // samples also protect overflow-safe arithmetic in the fade cache.
        for (int fade : new int[]{256,768,2048,4096}) {
            for (int x : new int[]{-30_000_000,-1_048_576,-65_536,-8192,
                                    8192,65_536,1_048_576,30_000_000,
                                    Integer.MIN_VALUE,Integer.MAX_VALUE}) {
                for (int z : new int[]{-30_000_000,-8192,0,8192,
                                        30_000_000,Integer.MAX_VALUE}) {
                    long original=Double.doubleToRawLongBits(
                            RealDemSeamlessMath.weightOutside(x,z,fade));
                    long optimized=Double.doubleToRawLongBits(
                            RealDemSeamlessMath.cachedWeightOutside(x,z,fade));
                    require(original==optimized,
                            "Far-distance USGS fade regression at "+x+","+z);
                    require(original==Double.doubleToRawLongBits(0.0),
                            "Distant procedural chunk mistakenly influenced by DEM");
                }
            }
        }
        checkDistantEdgeCacheParity();
        System.out.println("TVA_USGS_V2_SMOKE_PASS: 256 measured elevations, four 2048m fade sides, cache fidelity");
    }
}
