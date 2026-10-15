package org.realscale.neoforge;

/**
 * Pure deterministic, continuous ecology patch-field math.
 *
 * This is a vegetation/decoration density field, not a biome source,
 * climate simulator, terrain noise router or representation of rainfall.
 * All integer operations deliberately use floorDiv/floorMod so negative
 * world coordinates and chunk boundaries remain continuous.
 *
 * Macro patches: 768 blocks. Sub-patches: 128 blocks. Minecraft's own
 * seeded RandomSource supplies the final per-attempt roll in the Feature.
 */
public final class EcologyDistributionMath {
    private EcologyDistributionMath() {}

    public enum Habitat {
        TEMPERATE_RAINFOREST, SAVANNA_CANOPY, CHAPARRAL_SCRUB,
        GRASSLAND_MOSAIC, ALPINE_TUNDRA, FLOODPLAIN_BANK,
        TROPICAL_RAINFOREST, TEMPERATE_SEASONAL_FOREST, BOREAL_TAIGA,
        DESERT_OUTCROPS, ARCTIC_TUNDRA, FRESHWATER_BED,
        COASTAL_SEAGRASS, CORAL_REEF_FLOOR, ESTUARY_SHALLOWS
    }

    private static long mix64(long x) {
        x = (x ^ (x >>> 30)) * 0xbf58476d1ce4e5b9L;
        x = (x ^ (x >>> 27)) * 0x94d049bb133111ebL;
        return x ^ (x >>> 31);
    }

    private static double at(int gx, int gz, long salt) {
        long v = (long) gx * 0x9e3779b97f4a7c15L
               ^ (long) gz * 0xd1b54a32d192ed03L ^ salt;
        return (mix64(v) >>> 11) * 0x1.0p-53;
    }

    private static double smooth(double value) {
        double v = Math.max(0.0, Math.min(1.0, value));
        return v * v * (3.0 - 2.0 * v);
    }

    private static double between(double lo, double hi, double value) {
        return smooth((value - lo) / (hi - lo));
    }

    private static double sample(int x, int z, int period, long salt) {
        int gx = Math.floorDiv(x, period), gz = Math.floorDiv(z, period);
        double tx = smooth(Math.floorMod(x, period) / (double) period);
        double tz = smooth(Math.floorMod(z, period) / (double) period);
        double a = at(gx, gz, salt);
        double b = at(gx + 1, gz, salt);
        double c = at(gx, gz + 1, salt);
        double d = at(gx + 1, gz + 1, salt);
        return (a + (b - a) * tx) * (1.0 - tz)
                + (c + (d - c) * tx) * tz;
    }

    /**
     * Smooth correlated site suitability. Habitat-specific salts yield
     * different clustered shapes instead of white-noise speckles.
     */
    public static double patchIndex(Habitat habitat, int x, int z) {
        long salt = 0x53eac019c6b11e2dL
                ^ (long) (habitat.ordinal() + 1) * 0x9e3779b97f4a7c15L;
        double broad = sample(x, z, 768, salt);
        double local = sample(x, z, 128, salt ^ 0x679f5fdef4a5186dL);
        return 0.76 * broad + 0.24 * local;
    }

    /**
     * Conservative relative-height decoration preference ONLY, not a
     * temperature adjustment and not a fixed global snowline.
     */
    public static double altitudeFactor(Habitat habitat, int y) {
        return switch (habitat) {
            case TROPICAL_RAINFOREST ->
                    1.0 - 0.82 * between(160.0, 460.0, y);
            case SAVANNA_CANOPY, CHAPARRAL_SCRUB ->
                    1.0 - 0.72 * between(210.0, 600.0, y);
            case TEMPERATE_SEASONAL_FOREST ->
                    1.0 - 0.62 * between(260.0, 730.0, y);
            case TEMPERATE_RAINFOREST ->
                    1.0 - 0.48 * between(330.0, 870.0, y);
            case GRASSLAND_MOSAIC ->
                    1.0 - 0.40 * between(500.0, 1100.0, y);
            case ALPINE_TUNDRA ->
                    0.18 + 0.82 * between(105.0, 375.0, y);
            case DESERT_OUTCROPS ->
                    1.0 - 0.45 * between(550.0, 1400.0, y);
            default -> 1.0;
        };
    }

    public static double placementProbability(Habitat habitat, int x, int y, int z) {
        double density = switch (habitat) {
            case TROPICAL_RAINFOREST, TEMPERATE_RAINFOREST -> 0.94;
            case TEMPERATE_SEASONAL_FOREST -> 0.85;
            case BOREAL_TAIGA -> 0.90;
            case GRASSLAND_MOSAIC -> 0.98;
            case SAVANNA_CANOPY -> 0.72;
            case CHAPARRAL_SCRUB -> 0.73;
            case DESERT_OUTCROPS -> 0.58;
            case ALPINE_TUNDRA, ARCTIC_TUNDRA -> 0.83;
            case FLOODPLAIN_BANK -> 0.76;
            case FRESHWATER_BED, COASTAL_SEAGRASS -> 0.82;
            case CORAL_REEF_FLOOR, ESTUARY_SHALLOWS -> 0.70;
        };
        double clump = between(0.13, 0.81, patchIndex(habitat, x, z));
        return Math.max(0.0, Math.min(0.98,
                density * (0.12 + 0.88 * clump) * altitudeFactor(habitat, y)));
    }

    /**
     * Light-limited aquatic visual accents: shallow seagrass meadows and
     * estuary vegetation do not belong on very deep marine seafloors.
     * Freshwater beds are exempt: they can occur high above sea level.
     * This is a simple light-depth proxy, not a marine carbon model.
     */
    public static boolean aquaticDepthSuitable(Habitat habitat, int bottomY, int seaLevel) {
        int depth = seaLevel - bottomY;
        return switch (habitat) {
            case COASTAL_SEAGRASS -> depth >= 1 && depth <= 16;
            case CORAL_REEF_FLOOR -> depth >= 1 && depth <= 28;
            case ESTUARY_SHALLOWS -> depth >= 1 && depth <= 10;
            default -> true;
        };
    }
}
