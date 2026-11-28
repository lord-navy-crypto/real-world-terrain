package org.realscale.neoforge.client.clipmap;

import java.util.Locale;

/** Compact, stable biome classes streamed with visible-surface height tiles. */
public final class ClipmapBiomeMaterial {
    public static final byte UNKNOWN = 0;
    public static final byte GRASSLAND = 1;
    public static final byte FOREST = 2;
    public static final byte DESERT = 3;
    public static final byte SNOW = 4;
    public static final byte ROCK = 5;
    public static final byte WETLAND = 6;
    public static final byte SAVANNA = 7;
    public static final byte WATER = 8;
    public static final byte BADLANDS = 9;

    private ClipmapBiomeMaterial() {
    }

    /** Classifies a biome registry path without retaining registry objects or loading chunks. */
    public static byte fromBiomePath(String biomePath) {
        if (biomePath == null || biomePath.isBlank()) {
            return UNKNOWN;
        }
        String path = biomePath.toLowerCase(Locale.ROOT);
        if (containsAny(path, "ocean", "river")) return WATER;
        if (containsAny(path, "badlands", "eroded_badlands", "wooded_badlands")) return BADLANDS;
        if (containsAny(path, "desert", "beach", "dune", "salt", "playa")) return DESERT;
        if (containsAny(path, "snow", "frozen", "ice_spikes", "tundra", "glacier")) return SNOW;
        if (containsAny(path, "stony", "windswept", "peaks", "mountain", "alpine", "scree")) return ROCK;
        if (containsAny(path, "swamp", "mangrove", "marsh", "wetland", "bog")) return WETLAND;
        if (path.contains("savanna")) return SAVANNA;
        if (containsAny(path, "forest", "jungle", "taiga", "grove", "cherry", "woodland")) return FOREST;
        if (containsAny(path, "plains", "meadow", "grassland", "prairie", "fields")) return GRASSLAND;
        return UNKNOWN;
    }

    /** Majority material for a quad; unknown samples cannot outvote known biomes. */
    public static byte majority(byte a, byte b, byte c, byte d) {
        byte[] values = {a, b, c, d};
        byte winner = UNKNOWN;
        int bestCount = 0;
        for (byte candidate : values) {
            if (candidate == UNKNOWN) continue;
            int count = 0;
            for (byte value : values) {
                if (value == candidate) count++;
            }
            if (count > bestCount) {
                bestCount = count;
                winner = candidate;
            }
        }
        return winner;
    }

    public static int baseColor(byte material) {
        return switch (material) {
            case GRASSLAND -> 0x789052;
            case FOREST -> 0x527B45;
            case DESERT -> 0xC1AC7F;
            case SNOW -> 0xE1E7EA;
            case ROCK -> 0x85857F;
            case WETLAND -> 0x526B49;
            case SAVANNA -> 0xA99A5C;
            case WATER -> 0x64869A;
            case BADLANDS -> 0xA65E45;
            default -> -1;
        };
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) return true;
        }
        return false;
    }
}
