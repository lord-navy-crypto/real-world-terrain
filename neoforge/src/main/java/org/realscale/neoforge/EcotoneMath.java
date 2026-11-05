package org.realscale.neoforge;

/**
 * Deterministic two-biome ecotone classifier. Pure Java: intentionally does not
 * mutate worldgen chunks, terrain height, rivers, or Minecraft registries.
 * Integration into a biome source must be implemented and server-tested separately.
 */
public final class EcotoneMath {
    private EcotoneMath() {}

    public enum Habitat { FOREST, MEADOW, DESERT, GRASSLAND, TAIGA, ALPINE, RIVER, OCEAN, SWAMP, PLAINS, SNOW, BIRCH_FOREST, OTHER }
    public enum Transition { NONE, WOODLAND_MEADOW, SEMI_ARID_SCRUBLAND, SUBALPINE_WOODLAND, FOREST_STEPPE, MARSHY_WOODLAND, ROCKY_ALPINE_MEADOW, SNOWY_TREELINE, FLOODPLAIN_MARGIN, BIRCH_CONIFER_TRANSITION, WET_MEADOW }

    public static Transition compatible(Habitat first, Habitat second) {
        if (first == second || first == Habitat.OCEAN || second == Habitat.OCEAN) return Transition.NONE;
        if (first == Habitat.RIVER || second == Habitat.RIVER) {
            return pair(first, second, Habitat.RIVER, Habitat.PLAINS) ? Transition.FLOODPLAIN_MARGIN : Transition.NONE;
        }
        if (pair(first, second, Habitat.FOREST, Habitat.MEADOW)) return Transition.WOODLAND_MEADOW;
        if (pair(first, second, Habitat.DESERT, Habitat.GRASSLAND)) return Transition.SEMI_ARID_SCRUBLAND;
        if (pair(first, second, Habitat.TAIGA, Habitat.ALPINE)) return Transition.SUBALPINE_WOODLAND;
        if (pair(first, second, Habitat.FOREST, Habitat.GRASSLAND)) return Transition.FOREST_STEPPE;
        if (pair(first, second, Habitat.FOREST, Habitat.SWAMP)) return Transition.MARSHY_WOODLAND;
        if (pair(first, second, Habitat.MEADOW, Habitat.ALPINE)) return Transition.ROCKY_ALPINE_MEADOW;
        if (pair(first, second, Habitat.TAIGA, Habitat.SNOW)) return Transition.SNOWY_TREELINE;
        if (pair(first, second, Habitat.BIRCH_FOREST, Habitat.TAIGA)) return Transition.BIRCH_CONIFER_TRANSITION;
        if (pair(first, second, Habitat.PLAINS, Habitat.SWAMP)) return Transition.WET_MEADOW;
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

    /**
     * Quart-position radial distance squared in BLOCK units, avoiding sqrt in
     * Minecraft's dense per-chunk biome sampling loop. One quart is 4 blocks.
     * A diagonal step is correctly sqrt(2) times a cardinal step.
     */
    public static int quartDistanceSquared(int dx, int dz, int step) {
        if (step < 1 || (dx == 0 && dz == 0)
                || Math.abs(dx) > 1 || Math.abs(dz) > 1)
            throw new IllegalArgumentException("Expected nonzero unit heading and positive step");
        return 16 * step * step * (dx * dx + dz * dz);
    }

    /** Whether a candidate at radial quart distance is inside a biome border. */
    public static boolean withinRadialBand(int dx, int dz, int step, int widthBlocks) {
        return widthBlocks >= 16 && widthBlocks <= 64
                && 4L * quartDistanceSquared(dx, dz, step)
                    <= (long) widthBlocks * widthBlocks;
    }

    /** Smooth transition membership weight from a mathematically measured edge. */
    public static double weight(double distanceBlocks, double widthBlocks) {
        if (!Double.isFinite(distanceBlocks) || !Double.isFinite(widthBlocks)
                || widthBlocks <= 0) return 0.0;
        double t = Math.max(0.0, Math.min(1.0, 1.0 - 2.0 * Math.abs(distanceBlocks) / widthBlocks));
        return t * t * (3.0 - 2.0 * t);
    }
}
