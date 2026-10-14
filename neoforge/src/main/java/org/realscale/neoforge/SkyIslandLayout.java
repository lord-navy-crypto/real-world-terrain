package org.realscale.neoforge;

/** Shared deterministic grid for rare, opt-in ocean sky islands. Pure math, no world access. */
public final class SkyIslandLayout {
    private SkyIslandLayout() {}
    public static final int GRID = 512;
    public static final int RING_RADIUS = 352;
    public record Site(int x, int z, int topY, int radius) {}
    private static long mix(long x) {
        x ^= x >>> 30; x *= 0xbf58476d1ce4e5b9L;
        x ^= x >>> 27; x *= 0x94d049bb133111ebL;
        return x ^ (x >>> 31);
    }
    public static Site at(int blockX, int blockZ) {
        long gx = Math.floorDiv(blockX, GRID);
        long gz = Math.floorDiv(blockZ, GRID);
        long hash = mix((gx * 0x9e3779b97f4a7c15L)
                      ^ (gz * 0xc2b2ae3d27d4eb4fL)
                      ^ 0x3bc7f54a83e1d209L);
        int x = (int) (gx * GRID + 128 + Math.floorMod(hash, 256L));
        int z = (int) (gz * GRID + 128 + Math.floorMod(hash >>> 15, 256L));
        int y = 183 + (int) Math.floorMod(hash >>> 30, 21L);
        int radius = 10 + (int) Math.floorMod(hash >>> 47, 5L);
        return new Site(x, z, y, radius);
    }
    public static boolean insideBiome(Site site, int blockX, int blockY, int blockZ) {
        int dx = blockX - site.x(), dz = blockZ - site.z();
        return dx * dx + dz * dz <= site.radius() * site.radius()
                && blockY >= site.topY() - 13 && blockY <= site.topY() + 8;
    }
}
