package org.realscale.neoforge;

import java.util.Locale;

/**
 * Deterministic low-to-mid elevation spawn profiles.
 *
 * This class deliberately has no Minecraft dependencies so the profile and
 * scoring rules can be smoke-tested with a plain Java 21 compiler.
 */
public final class RealScaleSpawnMath {
    private RealScaleSpawnMath() {}

    public record Profile(
            int minOffset,
            int maxOffset,
            int maxSpawnOffset,
            int searchRadius,
            int candidateCount,
            int maxRelief
    ) {
        public Profile {
            if (minOffset < 1 || maxOffset < minOffset
                    || maxSpawnOffset < maxOffset || searchRadius < 32
                    || candidateCount < 1 || candidateCount > 40 || maxRelief < 0) {
                throw new IllegalArgumentException("Invalid RealScale spawn profile");
            }
        }
    }

    private static final Profile NATURAL =
            new Profile(8, 28, 64, 384, 32, 18);

    /**
     * Select a conservative profile from a noise-settings registry path.
     * The target is relative to the generator sea level, not an absolute Y.
     */
    public static Profile profileFor(String settingsId) {
        String id = settingsId == null ? "" : settingsId.toLowerCase(Locale.ROOT);
        int separator = id.indexOf(':');
        if (separator >= 0) id = id.substring(separator + 1);

        if (containsAny(id, "alpine", "himalayan", "mountain")) {
            return new Profile(8, 26, 64, 384, 40, 22);
        }
        if (containsAny(id, "canyon", "grand_canyon")) {
            return new Profile(4, 18, 48, 384, 32, 20);
        }
        if (id.contains("glacial")) {
            return new Profile(6, 22, 56, 384, 32, 20);
        }
        if (id.contains("volcanic")) {
            return new Profile(6, 22, 56, 384, 32, 22);
        }
        if (id.contains("karst")) {
            return new Profile(5, 20, 52, 384, 32, 18);
        }
        if (id.contains("dune")) {
            return new Profile(4, 18, 48, 384, 32, 16);
        }
        if (containsAny(id, "usgs", "measured", "big_sur")) {
            return new Profile(5, 22, 60, 448, 40, 20);
        }
        return NATURAL;
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) return true;
        }
        return false;
    }

    /** Deterministic target elevation for a world seed and terrain profile. */
    public static int targetHeight(long seed, int seaLevel, Profile profile) {
        int span = profile.maxOffset() - profile.minOffset() + 1;
        long profileBits = ((long) profile.minOffset() << 48)
                ^ ((long) profile.maxOffset() << 32)
                ^ ((long) profile.maxSpawnOffset() << 16)
                ^ profile.searchRadius()
                ^ ((long) profile.candidateCount() << 8)
                ^ profile.maxRelief();
        long mixed = mix64(seed ^ profileBits ^ 0x52534C535041574EL);
        int offset = (int) Long.remainderUnsigned(mixed, span);
        return seaLevel + profile.minOffset() + offset;
    }

    /**
     * Lower scores are better: elevation match first, then local relief,
     * followed by a small preference for locations near the world origin.
     */
    public static double score(
            int surfaceY,
            int targetY,
            int localRelief,
            int distanceSquared
    ) {
        return Math.abs(surfaceY - targetY) * 4.0
                + Math.max(0, localRelief) * 2.5
                + Math.sqrt(Math.max(0, distanceSquared)) * 0.025;
    }

    /** Reject submerged lowlands and extreme high-altitude surfaces. */
    public static boolean isHeightAllowed(int surfaceY, int seaLevel, Profile profile) {
        return surfaceY >= seaLevel + 2
                && surfaceY <= seaLevel + profile.maxSpawnOffset();
    }

    static long mix64(long value) {
        long z = value;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
