package org.realscale.neoforge;

public final class RealScaleSpawnMathSmoke {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        int[] samples = {100,200,300,400,500,600,700,800,900,1000};
        var natural = RealScaleSpawnMath.profileFor("tva_natural_smoothed");
        var alpine = RealScaleSpawnMath.profileFor("tva_alpine_smoothed");
        var canyon = RealScaleSpawnMath.profileFor("tva_canyon_smoothed");
        var usgs = RealScaleSpawnMath.profileFor("usgs_big_sur");
        check(natural != null && alpine != null && canyon != null && usgs != null,
                "all terrain families need profiles");

        int target = RealScaleSpawnMath.targetHeight(42L, natural, samples);
        check(target == RealScaleSpawnMath.targetHeight(42L, natural, samples),
                "same seed and profile must produce the same target");
        check(target >= samples[0] && target <= samples[samples.length - 1],
                "target must be selected from the sampled terrain distribution");
        int quantile = RealScaleSpawnMath.targetQuantilePermille(42L, natural);
        check(quantile >= natural.minQuantilePermille()
                        && quantile <= natural.maxQuantilePermille(),
                "natural target quantile must stay in its configured band");

        int alpineTarget = RealScaleSpawnMath.targetHeight(42L, alpine, samples);
        check(alpineTarget <= target,
                "alpine preset should bias spawn lower toward foothills");
        check(alpine.searchRadius() >= 1024 && usgs.searchRadius() >= natural.searchRadius(),
                "large-scale terrain presets need a wider bounded search");
        check(RealScaleSpawnMath.score(400,400,2,0)
                        < RealScaleSpawnMath.score(700,400,40,6400),
                "target-height gentle ground must beat a distant high-relief slope");
        check(RealScaleSpawnMath.isHeightAllowed(100,-64,2032),
                "valid terrain surface should be allowed");
        check(!RealScaleSpawnMath.isHeightAllowed(-64,-64,2032),
                "world floor must be rejected");
        check(!RealScaleSpawnMath.isHeightAllowed(2031,-64,2032),
                "top boundary must leave two blocks of headroom");
        check(RealScaleSpawnMath.profileFor("unrecognized_settings").minQuantilePermille()
                        == natural.minQuantilePermille(),
                "unknown settings must use the conservative Natural profile");
        System.out.println("REALSCALE_SPAWN_MATH_OK");
    }
}
