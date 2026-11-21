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

    private static final int BUCKETS = SLOTS / 2;
    private final int capacity;
    private final int bucketMask;
    private final long[] coordinates;
    private final Object[] results;
    private final byte[] victim;
    private long lastCoordinate;
    private Object lastValue;

    QuartBiomeResultCache() {
        this(RealScalePerformanceProfile.cacheSlots("biome_result", SLOTS));
    }

    QuartBiomeResultCache(int slots) {
        if (slots < 16 || (slots & (slots - 1)) != 0)
            throw new IllegalArgumentException("Cache slots must be a power of two >= 16");
        this.capacity = slots;
        this.bucketMask = (slots >>> 1) - 1;
        this.coordinates = new long[slots];
        this.results = new Object[slots];
        this.victim = new byte[slots >>> 1];
    }

    int capacity() {
        return capacity;
    }

    @SuppressWarnings("unchecked")
    V get(long coordinate) {
        if (lastValue != null && lastCoordinate == coordinate)
            return (V) lastValue;
        final int first = bucket(coordinate,bucketMask) << 1;
        Object result = results[first];
        if (result == null || coordinates[first] != coordinate) {
            result = results[first+1];
            if (result == null || coordinates[first+1] != coordinate)
                return null;
        }
        lastCoordinate = coordinate;
        lastValue = result;
        return (V) result;
    }

    void put(long coordinate, V value) {
        Objects.requireNonNull(value, "biome resolution must return a holder");
        final int bucket = bucket(coordinate,bucketMask);
        final int first = bucket << 1;
        final int second = first + 1;
        final int index;
        if (results[first] != null && coordinates[first] == coordinate)
            index = first;
        else if (results[second] != null && coordinates[second] == coordinate)
            index = second;
        else if (results[first] == null)
            index = first;
        else if (results[second] == null)
            index = second;
        else {
            index = first + (victim[bucket] & 1);
            victim[bucket] ^= 1;
        }
        coordinates[index] = coordinate;
        results[index] = value;
        lastCoordinate = coordinate;
        lastValue = value;
    }

    void clear() {
        Arrays.fill(results, null);
        Arrays.fill(victim, (byte)0);
        lastValue = null;
    }

    // Include the high, middle and low bits of Minecraft's packed BlockPos.
    // Hash collisions only affect hit rates, not deterministic correctness.
    static int bucket(long coordinate) {
        return bucket(coordinate,BUCKETS - 1);
    }

    private static int bucket(long coordinate,int mask) {
        return slot(coordinate) & mask;
    }

    static int slot(long coordinate) {
        return slot(coordinate,SLOTS - 1);
    }

    private static int slot(long coordinate,int mask) {
        long h = coordinate ^ (coordinate >>> 21) ^ (coordinate >>> 42);
        h ^= h >>> 32;
        return ((int) h ^ ((int) h >>> 16)) & mask;
    }
}
