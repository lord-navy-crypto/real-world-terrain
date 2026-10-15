package org.realscale.neoforge;

import java.util.Arrays;
import java.util.Objects;

/**
 * Tiny bounded, allocation-free-on-hit memo table for a worker's fully
 * resolved quart-coordinate biome queries.
 *
 * <p>Ownership: one instance per BiomeSource and worldgen thread. The owner
 * MUST clear() whenever the Climate.Sampler identity changes. Keys are the
 * exact packed quart coordinates already used by NaturalEcotoneBiomeSource,
 * and values are the same registry Holder instances returned by that source.
 *
 * <p>Collisions replace cache entries, NEVER biome results. All misses fall
 * back to the existing biome-selection algorithm. The hot last-result slot
 * avoids probing the array for identical consecutive coordinates.
 */
final class QuartBiomeResultCache<V> {
    static final int SLOTS = 2048; // power of two; bounded memory per worker

    private final long[] coordinates = new long[SLOTS];
    private final Object[] results = new Object[SLOTS];
    private long lastCoordinate;
    private Object lastValue;

    @SuppressWarnings("unchecked")
    V get(long coordinate) {
        if (lastValue != null && lastCoordinate == coordinate)
            return (V) lastValue;
        final int index = slot(coordinate);
        final Object result = results[index];
        if (result == null || coordinates[index] != coordinate)
            return null;
        lastCoordinate = coordinate;
        lastValue = result;
        return (V) result;
    }

    void put(long coordinate, V value) {
        Objects.requireNonNull(value, "biome resolution must return a holder");
        final int index = slot(coordinate);
        coordinates[index] = coordinate;
        results[index] = value;
        lastCoordinate = coordinate;
        lastValue = value;
    }

    void clear() {
        Arrays.fill(results, null);
        lastValue = null;
    }

    // Include the high, middle and low bits of Minecraft's packed BlockPos.
    // Hash collisions only affect hit rates, not deterministic correctness.
    static int slot(long coordinate) {
        long h = coordinate ^ (coordinate >>> 21) ^ (coordinate >>> 42);
        h ^= h >>> 32;
        return ((int) h ^ ((int) h >>> 16)) & (SLOTS - 1);
    }
}
