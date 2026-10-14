package org.realscale.neoforge;

/** Shared deterministic grid for rare, opt-in ocean sky islands. Pure math, no world access. */
public final class SkyIslandLayout {
    private SkyIslandLayout() {}
    public static final int GRID = 512;
    public static final int RING_RADIUS = 384;
    private static final int[] PROBE_RADII = {96, 192, 288, RING_RADIUS};
    private static final int DIRECTIONS = 16;
    private static final int[] PROBE_X = new int[PROBE_RADII.length * DIRECTIONS];
    private static final int[] PROBE_Z = new int[PROBE_X.length];
    static {
        for (int n = 0; n < PROBE_RADII.length; n++) {
            for (int i = 0; i < DIRECTIONS; i++) {
                double a = i * 2.0 * Math.PI / DIRECTIONS;
                PROBE_X[n * DIRECTIONS + i] = (int)Math.round(PROBE_RADII[n] * Math.cos(a));
                PROBE_Z[n * DIRECTIONS + i] = (int)Math.round(PROBE_RADII[n] * Math.sin(a));
            }
        }
    }
    public record Site(int x, int z, int topY, int radius) {}
    private static long mix(long x) {
        x ^= x >>> 30; x *= 0xbf58476d1ce4e5b9L;
        x ^= x >>> 27; x *= 0x94d049bb133111ebL;
        return x ^ (x >>> 31);
    }
    public static Site at(int blockX, int blockZ) {
        long gx = Math.floorDiv((long) blockX + GRID / 2, GRID);
        long gz = Math.floorDiv((long) blockZ + GRID / 2, GRID);
        long hash = mix((gx * 0x9e3779b97f4a7c15L)
                      ^ (gz * 0xc2b2ae3d27d4eb4fL)
                      ^ 0x3bc7f54a83e1d209L);
        int x = (int) (gx * GRID - 128 + Math.floorMod(hash, 256L));
        int z = (int) (gz * GRID - 128 + Math.floorMod(hash >>> 15, 256L));
        int y = 183 + (int) Math.floorMod(hash >>> 30, 21L);
        int radius = 10 + (int) Math.floorMod(hash >>> 47, 5L);
        return new Site(x, z, y, radius);
    }
    /**
     * A finite conservative coastal-distance probe, not a global shortest
     * distance field. Samples only the ungenerated underlying climate source.
     */
    public static boolean remoteDeepOcean(Site site,
            java.util.function.BiPredicate<Integer,Integer> deep,
            java.util.function.BiPredicate<Integer,Integer> ocean) {
        if (!deep.test(site.x(),site.z())) return false;
        // Denser 22.5-degree sampling detects coast wedges between the
        // original eight 45-degree compass rays. Trigonometry is precomputed.
        for (int i = 0; i < PROBE_X.length; i++)
            if (!ocean.test(site.x() + PROBE_X[i], site.z() + PROBE_Z[i]))
                return false;
        return true;
    }
    public static boolean insideBiome(Site site, int blockX, int blockY, int blockZ) {
        int dx = blockX - site.x(), dz = blockZ - site.z();
        return dx * dx + dz * dz <= site.radius() * site.radius()
                && blockY >= site.topY() - 13 && blockY <= site.topY() + 8;
    }
}
