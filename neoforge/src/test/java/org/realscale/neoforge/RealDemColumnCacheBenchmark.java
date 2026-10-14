package org.realscale.neoforge;

import java.util.Arrays;

/**
 * Deterministic microbenchmark using the exact bundled DEM and the original
 * floating-point density expressions. This does NOT measure Minecraft chunk
 * generation, population, IO, or client FPS.
 *
 * Run on Java 21 with RealDemTile, RealDemColumnCache and the DEM resource.
 */
public final class RealDemColumnCacheBenchmark {
    private static volatile double sink;
    private static final int WARMUP = 6;
    private static final int TRIALS = 9;

    private enum Pattern { COLUMN_MAJOR, HEIGHT_MAJOR, OUTSIDE }

    private static final class Work {
        final int[] xs;
        final int[] zs;
        final int[] ys;
        Work(RealDemTile tile, Pattern pattern) {
            // 12,288 samples; deliberately test both best-case column reuse
            // and a height-major traversal that can thrash the 256-slot cache.
            final int columns = 384;
            final int levels = 32;
            int n = columns * levels;
            xs = new int[n]; zs = new int[n]; ys = new int[n];
            for (int i = 0; i < n; i++) {
                int column = pattern == Pattern.HEIGHT_MAJOR ? i % columns : i / levels;
                int y = pattern == Pattern.HEIGHT_MAJOR ? i / columns : i % levels;
                if (pattern == Pattern.OUTSIDE) {
                    xs[i] = tile.maxX() + 1 + (column % 90);
                    zs[i] = tile.maxZ() + 1 + ((column / 90) * 31);
                } else {
                    xs[i] = tile.originX() + 10 + (column * 17) % 450;
                    zs[i] = tile.originZ() + 10 + (column * 47) % 450;
                }
                ys[i] = -32 + y * 48;
            }
        }
    }

    private static double run(RealDemTile tile, Work work, boolean cached) {
        RealDemColumnCache cache = cached ? new RealDemColumnCache(tile) : null;
        double sum = 0;
        for (int i = 0; i < work.xs.length; i++) {
            int x = work.xs[i], z = work.zs[i], y = work.ys[i];
            double base = 0.125 + (i % 67) / 1000.0;
            if (cached) {
                if (x <= tile.originX() || x >= tile.maxX()
                        || z <= tile.originZ() || z >= tile.maxZ()) {
                    sum += base;
                    continue;
                }
                var col = cache.sample(x, z, 96);
                double t = col.influence();
                if (t <= 0) {
                    sum += base;
                } else {
                    double d = (col.elevation() - y) / 128.0;
                    sum += t >= 1 ? d : d * t + base * (1.0 - t);
                }
            } else {
                double t = tile.influence(x, z, 96);
                if (t <= 0) {
                    sum += base;
                } else {
                    double d = (tile.worldSolidYAt(x, z) - y) / 128.0;
                    sum += t >= 1 ? d : d * t + base * (1.0 - t);
                }
            }
        }
        sink = sum;
        return sum;
    }

    private static double[] runTrials(RealDemTile tile, Work work, Pattern kind) {
        for (int i = 0; i < WARMUP; i++) {
            run(tile, work, false);
            run(tile, work, true);
        }
        long[] original = new long[TRIALS];
        long[] optimized = new long[TRIALS];
        for (int trial = 0; trial < TRIALS; trial++) {
            // Alternate order to reduce warm-cache/JIT bias.
            boolean optimizedFirst = trial % 2 == 1;
            for (int n = 0; n < 2; n++) {
                boolean useCache = (n == 0) == optimizedFirst;
                long start = System.nanoTime();
                double result = run(tile, work, useCache);
                long elapsed = System.nanoTime() - start;
                if (!Double.isFinite(result)) throw new AssertionError("Invalid result");
                (useCache ? optimized : original)[trial] = elapsed;
            }
        }
        Arrays.sort(original);
        Arrays.sort(optimized);
        double baseline = original[TRIALS/2] / 1e6;
        double fast = optimized[TRIALS/2] / 1e6;
        System.out.printf("%-16s: baseline %.3f ms, cached %.3f ms, ratio %.3fx (%d samples)%n",
                kind, baseline, fast, baseline / fast, work.xs.length);
        return new double[]{baseline, fast};
    }

    public static void main(String[] args) {
        RealDemTile tile = RealDemTile.load();
        System.out.println("RealScale DEM microbenchmark; relative results are NOT chunkgen FPS/TPS.");
        for (Pattern kind : Pattern.values())
            runTrials(tile, new Work(tile, kind), kind);
        if (Double.isNaN(sink)) throw new AssertionError("Lost benchmark checksum");
    }
}
