package org.realscale.neoforge;

/** Check the restored measured DEM source and continuous 2048-block transition. */
public final class RealDemSeamlessMathSmoke {
    private RealDemSeamlessMathSmoke() {}
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
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
        // Revisit 256 far-separated DEM/fade columns while sweeping more
        // than 4096 other columns through the bounded cache. Neither
        // collisions nor evictions may change measured height raw bits.
        final int nProbe=256;
        int[] px=new int[nProbe],pz=new int[nProbe];
        long[] originalBits=new long[nProbe];
        for(int k=0;k<nProbe;k++) {
            px[k]=((k*149)%4200)-2100;
            pz[k]=((k*257)%4300)-2150;
            originalBits[k]=Double.doubleToRawLongBits(
                    RealDemSeamlessMath.extendedHeight(px[k],pz[k]));
        }
        for(int pass=0;pass<4;pass++) {
            for(int k=0;k<8192;k++) {
                int x=((k*79+pass*31)%4800)-2400;
                int z=((k*173+pass*43)%4800)-2400;
                RealDemSeamlessMath.cachedExtendedHeight(x,z);
            }
            for(int k=nProbe-1;k>=0;k--)
                require(Double.doubleToRawLongBits(
                        RealDemSeamlessMath.cachedExtendedHeight(px[k],pz[k]))
                        ==originalBits[k],
                        "DEM cache churn changed height at "+px[k]+","+pz[k]);
        }
        System.out.println("TVA_USGS_V2_SMOKE_PASS: 256 measured elevations, four 2048m fade sides, cache fidelity");
    }
}
