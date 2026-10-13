package org.realscale.neoforge;

/**
 * Deterministic two-biome ecotone classifier. Pure Java: intentionally does not
 * mutate worldgen chunks, terrain height, rivers, or Minecraft registries.
 * Integration into a biome source must be implemented and server-tested separately.
 */
public final class EcotoneMath {
    private EcotoneMath() {}

    public enum Habitat { FOREST, MEADOW, DESERT, GRASSLAND, TAIGA, ALPINE, RIVER, OCEAN, OTHER }
    public enum Transition { NONE, WOODLAND_MEADOW, SEMI_ARID_SCRUBLAND, SUBALPINE_WOODLAND }

    public static Transition compatible(Habitat first, Habitat second) {
        if (first == second || first == Habitat.RIVER || second == Habitat.RIVER
                || first == Habitat.OCEAN || second == Habitat.OCEAN) return Transition.NONE;
        if (pair(first, second, Habitat.FOREST, Habitat.MEADOW)) return Transition.WOODLAND_MEADOW;
        if (pair(first, second, Habitat.DESERT, Habitat.GRASSLAND)) return Transition.SEMI_ARID_SCRUBLAND;
        if (pair(first, second, Habitat.TAIGA, Habitat.ALPINE)) return Transition.SUBALPINE_WOODLAND;
        return Transition.NONE;
    }

    private static boolean pair(Habitat a, Habitat b, Habitat c, Habitat d) {
        return (a == c && b == d) || (a == d && b == c);
    }

    /**
     * Return whether a sample lies in a thin transition band.
     * widthBlocks must be 16..64. distanceBlocks is distance to the natural
     * climate-classification boundary, not the distance to a random chunk edge.
     */
    public static boolean insideBand(double distanceBlocks, double widthBlocks,
                                     double slopeDegrees, boolean protectedChannel) {
        if (!Double.isFinite(distanceBlocks) || !Double.isFinite(widthBlocks)
                || !Double.isFinite(slopeDegrees) || widthBlocks < 16 || widthBlocks > 64
                || protectedChannel || slopeDegrees >= 50) return false;
        double effectiveWidth = widthBlocks * (slopeDegrees >= 30 ? 0.5 : 1.0);
        return Math.abs(distanceBlocks) <= effectiveWidth * 0.5;
    }

    /** Smooth transition membership weight from a mathematically measured edge. */
    public static double weight(double distanceBlocks, double widthBlocks) {
        if (!Double.isFinite(distanceBlocks) || !Double.isFinite(widthBlocks)
                || widthBlocks <= 0) return 0.0;
        double t = Math.max(0.0, Math.min(1.0, 1.0 - 2.0 * Math.abs(distanceBlocks) / widthBlocks));
        return t * t * (3.0 - 2.0 * t);
    }
}
