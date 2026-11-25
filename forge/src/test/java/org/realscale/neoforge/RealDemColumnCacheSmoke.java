package org.realscale.neoforge;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Compares cached values against the unchanged original DEM calculation. */
public final class RealDemColumnCacheSmoke {
    private RealDemColumnCacheSmoke() {}

    private static void sameBits(double expected, double actual, String what) {
        if (Double.doubleToRawLongBits(expected) != Double.doubleToRawLongBits(actual)) {
            throw new AssertionError(what + ": expected " + expected + " got " + actual);
        }
    }

    private static long check(RealDemTile tile) {
        RealDemColumnCache cache = new RealDemColumnCache(tile);
        long samples = 0;
        for (int blend : new int[]{32, 96, 128, 256}) {
            for (int z = tile.originZ() - 16; z <= tile.maxZ() + 16; z += 7) {
                for (int x = tile.originX() - 16; x <= tile.maxX() + 16; x += 5) {
                    RealDemColumnCache.Column entry = cache.sample(x, z, blend);
                    sameBits(tile.influence(x, z, blend), entry.influence(), "weight " + x + "," + z);
                    if (entry.influence() > 0.0) {
                        sameBits(tile.worldSolidYAt(x, z), entry.elevation(), "elevation " + x + "," + z);
                    }
                    // Cached and original density arithmetic must agree exactly, including
                    // partial blend, fully covered terrain, and zero influence.
                    for (int y : new int[]{-64, 0, 63, 500, 1280, 2031}) {
                        double w = tile.influence(x, z, blend);
                        double base = 0.612345678;
                        double expected = w <= 0.0 ? base :
                            (w >= 1.0 ? (tile.worldSolidYAt(x, z) - y) / 128.0 :
                                ((tile.worldSolidYAt(x, z) - y) / 128.0) * w + base * (1.0 - w));
                        double actual = entry.influence() <= 0.0 ? base :
                            (entry.influence() >= 1.0 ? (entry.elevation() - y) / 128.0 :
                                ((entry.elevation() - y) / 128.0) * entry.influence()
                                    + base * (1.0 - entry.influence()));
                        sameBits(expected, actual, "density " + x + "," + z + "," + y);
                    }
                    if (cache.sample(x, z, blend) != entry) {
                        throw new AssertionError("same-column cache miss for " + x + "," + z);
                    }
                    samples++;
                }
            }
        }
        // Direct-mapped collisions must not return stale values for other columns.
        for (int i = 0; i < 8192; i++) {
            int x = tile.originX() + 1 + Math.floorMod(i * 103, 478);
            int z = tile.originZ() + 1 + Math.floorMod(i * 53, 478);
            RealDemColumnCache.Column c = cache.sample(x, z, 96);
            sameBits(tile.worldSolidYAt(x, z), c.elevation(), "collision " + i);
        }
        return samples;
    }

    public static void main(String[] args) throws Exception {
        RealDemTile tile = RealDemTile.load();
        long checked = check(tile);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            Future<?>[] tests = new Future<?>[3];
            for (int i = 0; i < tests.length; i++) tests[i] = pool.submit(() -> check(tile));
            for (Future<?> f : tests) f.get();
        } finally {
            pool.shutdownNow();
        }
        System.out.println("PASS: cached DEM X/Z columns match baseline exactly (" + checked
            + " sequential samples plus three parallel runs; collisions and blend widths covered)");
    }
}
