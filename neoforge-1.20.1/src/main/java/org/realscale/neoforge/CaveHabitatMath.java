package org.realscale.neoforge;

/**
 * Pure classifier for candidate cave habitats. Does not register a biome source
 * or alter terrain noise; caller must sample cave context from real worldgen.
 */
public final class CaveHabitatMath {
    private CaveHabitatMath() {}
    public enum CaveHabitat {
        NONE, DAMP_LIMESTONE, DEEP_FUNGAL, DRY_KARST,
        UNDERGROUND_SPRINGS, VOLCANIC, GLACIAL
    }

    /**
     * Depth-sensitive preliminary cave selection.
     * @param y sampled block Y
     * @param surfaceY local surface block Y
     * @param caveAir true only if cave air/void is confirmed
     * @param humidity normalized 0..1 local moisture proxy
     * @param limestone derived from host geology; not from a biome name
     * @param lavaNearby from sampled lava boundary
     * @param flowingSpring from a validated aquifer water-seep source
     * @param cold from climate/geology combined
     */
    public static CaveHabitat select(int y, int surfaceY, boolean caveAir,
                                     double humidity, boolean limestone,
                                     boolean lavaNearby, boolean flowingSpring,
                                     boolean cold) {
        if (!caveAir || surfaceY - y < 12 || !Double.isFinite(humidity)) return CaveHabitat.NONE;
        double moisture = Math.max(0.0, Math.min(1.0, humidity));
        if (lavaNearby && y <= 0) return CaveHabitat.VOLCANIC;
        if (cold && y >= -16) return CaveHabitat.GLACIAL;
        if (flowingSpring && moisture >= 0.65) return CaveHabitat.UNDERGROUND_SPRINGS;
        if (limestone && moisture >= 0.62) return CaveHabitat.DAMP_LIMESTONE;
        if (limestone && moisture <= 0.28) return CaveHabitat.DRY_KARST;
        if (moisture >= 0.83 && y <= 20) return CaveHabitat.DEEP_FUNGAL;
        return CaveHabitat.NONE;
    }
}
