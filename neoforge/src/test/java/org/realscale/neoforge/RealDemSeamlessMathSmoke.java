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
        System.out.println("TVA_USGS_V2_SMOKE_PASS: 256 measured elevations, four 2048m fade sides, cache fidelity");
    }
}
