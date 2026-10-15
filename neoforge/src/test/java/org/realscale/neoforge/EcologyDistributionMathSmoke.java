package org.realscale.neoforge;

import org.realscale.neoforge.EcologyDistributionMath.Habitat;

/** Runs in CI with javac only, without loading NeoForge or Minecraft. */
public final class EcologyDistributionMathSmoke {
    private EcologyDistributionMathSmoke() {}

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        int sites = 0;
        for (Habitat habitat : Habitat.values()) {
            double min = 1.0, max = 0.0;
            for (int x = -4096; x <= 4096; x += 131) {
                for (int z = -4096; z <= 4096; z += 173) {
                    double p = EcologyDistributionMath.placementProbability(habitat, x, 90, z);
                    double same = EcologyDistributionMath.placementProbability(habitat, x, 90, z);
                    require(Double.isFinite(p) && p >= 0.0 && p <= 1.0,
                            "Unbounded probability " + habitat);
                    require(Double.doubleToLongBits(p) == Double.doubleToLongBits(same),
                            "Nondeterministic probability " + habitat);
                    min = Math.min(min, p);
                    max = Math.max(max, p);
                    sites++;
                }
            }
            require(max - min >= 0.18, "No real macro-density variation: " + habitat);
            // Both sides of zero and a chunk edge are smoothly interpolated.
            for (int x : new int[] {-769, -513, -129, -17, -16, -1, 0, 15, 16, 127, 768}) {
                double a = EcologyDistributionMath.patchIndex(habitat, x, -17);
                double b = EcologyDistributionMath.patchIndex(habitat, x + 1, -17);
                require(Math.abs(a - b) < 0.02,
                        "Patch seam or spike at " + x + ", habitat " + habitat);
            }
        }
        require(EcologyDistributionMath.altitudeFactor(Habitat.TROPICAL_RAINFOREST, 90)
                > EcologyDistributionMath.altitudeFactor(Habitat.TROPICAL_RAINFOREST, 650),
                "High tropical crowns should become less frequent at high altitudes");
        require(EcologyDistributionMath.altitudeFactor(Habitat.SAVANNA_CANOPY, 130)
                > EcologyDistributionMath.altitudeFactor(Habitat.SAVANNA_CANOPY, 750),
                "Warm lowland canopy preference missing");
        require(EcologyDistributionMath.altitudeFactor(Habitat.ALPINE_TUNDRA, 700)
                > EcologyDistributionMath.altitudeFactor(Habitat.ALPINE_TUNDRA, 75),
                "Alpine high-elevation preference missing");
        require(EcologyDistributionMath.patchIndex(Habitat.TROPICAL_RAINFOREST, 1000, 2000)
                != EcologyDistributionMath.patchIndex(Habitat.DESERT_OUTCROPS, 1000, 2000),
                "Independent habitat salt missing");
        System.out.println("ECOLOGY_SPATIAL_DENSITY_PASS " + Habitat.values().length
                + " habitats, " + sites + " deterministic coordinates, no seams");
    }
}
