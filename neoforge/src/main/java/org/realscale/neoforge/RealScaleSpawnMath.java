package org.realscale.neoforge;

import java.util.Arrays;
import java.util.Locale;

/**
 * Deterministic preset-aware spawn elevation selection.
 *
 * A preset defines a low-to-mid percentile band. The actual target Y is then
 * derived from the safe terrain samples around world origin, so it adapts to
 * RealScale's very different vertical scales instead of assuming sea level is
 * close to the land surface.
 */
public final class RealScaleSpawnMath {
    private RealScaleSpawnMath() {}

    public record Profile(
            int minQuantilePermille,
            int maxQuantilePermille,
            int searchRadius,
            int candidateCount
    ) {
        public Profile {
            if (minQuantilePermille < 0 || maxQuantilePermille < minQuantilePermille
                    || maxQuantilePermille > 1000 || searchRadius < 32
                    || candidateCount < 1 || candidateCount > 40) {
                throw new IllegalArgumentException("Invalid RealScale spawn profile");
            }
        }
    }

    private static final Profile NATURAL =
            new Profile(320, 420, 2048, 32);

    /** Select a conservative low-to-mid percentile band for a noise-settings ID. */
    public static Profile profileFor(String settingsId) {
        String id = settingsId == null ? "" : settingsId.toLowerCase(Locale.ROOT);
        int separator = id.indexOf(':');
        if (separator >= 0) id = id.substring(separator + 1);

        if (containsAny(id, "alpine", "himalayan", "mountain")) {
            return new Profile(180, 300, 2048, 40);
        }
        if (containsAny(id, "canyon", "grand_canyon")) {
            return new Profile(350, 500, 1536, 32);
        }
        if (id.contains("glacial")) {
            return new Profile(280, 400, 2048, 32);
        }
        if (id.contains("volcanic")) {
            return new Profile(220, 350, 2048, 32);
        }
        if (id.contains("karst")) {
            return new Profile(300, 420, 2048, 32);
        }
        if (id.contains("dune")) {
            return new Profile(300, 450, 2048, 32);
        }
        if (containsAny(id, "usgs", "measured", "big_sur")) {
            return new Profile(280, 400, 2560, 40);
        }
        return NATURAL;
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) return true;
        }
        return false;
    }

    /** Deterministic seed-specific percentile within the selected preset band. */
    public static int targetQuantilePermille(long seed, Profile profile) {
        long profileBits = ((long) profile.minQuantilePermille() << 48)
                ^ ((long) profile.maxQuantilePermille() << 32)
                ^ ((long) profile.searchRadius() << 16)
                ^ profile.candidateCount();
        long mixed = mix64(seed ^ profileBits ^ 0x52534C535041574EL);
        int span = profile.maxQuantilePermille() - profile.minQuantilePermille() + 1;
        return profile.minQuantilePermille()
                + (int) Long.remainderUnsigned(mixed, span);
    }

    /**
     * Convert the preset's deterministic quantile into an actual Y value
     * using safe surface samples collected from the active terrain generator.
     */
    public static int targetHeight(long seed, Profile profile, int[] sampledHeights) {
        if (sampledHeights == null || sampledHeights.length == 0) {
            throw new IllegalArgumentException("At least one safe terrain sample is required");
        }
        int[] sorted = Arrays.copyOf(sampledHeights, sampledHeights.length);
        Arrays.sort(sorted);
        int quantile = targetQuantilePermille(seed, profile);
        int index = (int) Math.round((quantile / 1000.0) * (sorted.length - 1));
        return sorted[index];
    }

    /** Lower scores prefer the target elevation, gentler relief, and origin proximity. */
    public static double score(
            int surfaceY,
            int targetY,
            int localRelief,
            int distanceSquared
    ) {
        return Math.abs(surfaceY - targetY) * 3.0
                + Math.max(0, localRelief) * 4.0
                + Math.sqrt(Math.max(0, distanceSquared)) * 0.02;
    }

    /** Reject samples outside the generator's usable vertical range. */
    public static boolean isHeightAllowed(int surfaceY, int minY, int maxY) {
        return surfaceY > minY + 1 && surfaceY + 1 < maxY;
    }

    static long mix64(long value) {
        long z = value;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
