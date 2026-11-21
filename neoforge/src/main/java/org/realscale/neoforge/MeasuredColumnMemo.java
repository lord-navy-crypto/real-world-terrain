package org.realscale.neoforge;

/**
 * Exact, fixed-size X/Z cache for a proven Y-invariant density function.
 *
 * Two-way buckets preserve both values when structure height probes alternate
 * between columns whose hashes alias. Eviction only forces recomputation;
 * coordinate identity is always checked before returning a cached double.
 * One cache instance belongs to one mapped density function and one thread.
 */
final class MeasuredColumnMemo {
    static final int SLOTS = 8192;
    private static final int BUCKETS = SLOTS / 2;
    private final int capacity;
    private final int bucketMask;
    private final long[] coordinates;
    private final double[] values;
    private final boolean[] valid;
    private final byte[] victim;
    private boolean lastValid;
    private long lastCoordinate;
    private double lastValue;
    private long missCoordinate;
    private int missBucket;

    MeasuredColumnMemo() {
        this(RealScalePerformanceProfile.cacheSlots("measured_horizontal", SLOTS));
    }

    MeasuredColumnMemo(int slots) {
        if (slots < 16 || (slots & (slots - 1)) != 0)
            throw new IllegalArgumentException("Cache slots must be a power of two >= 16");
        this.capacity = slots;
        this.bucketMask = (slots >>> 1) - 1;
        this.coordinates = new long[slots];
        this.values = new double[slots];
        this.valid = new boolean[slots];
        this.victim = new byte[slots >>> 1];
    }

    int capacity() {
        return capacity;
    }

    boolean lookup(int x, int z) {
        long coordinate = ((long)x << 32) | (z & 0xffffffffL);
        if (lastValid && lastCoordinate == coordinate) return true;
        int first = bucket(x,z,bucketMask) << 1;
        if (valid[first] && coordinates[first] == coordinate) {
            lastCoordinate = coordinate;
            lastValue = values[first];
            lastValid = true;
            return true;
        }
        int second = first + 1;
        if (valid[second] && coordinates[second] == coordinate) {
            lastCoordinate = coordinate;
            lastValue = values[second];
            lastValid = true;
            return true;
        }
        missCoordinate = coordinate;
        missBucket = first;
        return false;
    }

    double value() { return lastValue; }

    /** Store only after a lookup miss, without any intervening lookup. */
    void store(double value) {
        int first = missBucket;
        int second = first + 1;
        final int target;
        if (!valid[first] || coordinates[first] == missCoordinate) target = first;
        else if (!valid[second] || coordinates[second] == missCoordinate) target = second;
        else {
            int bucket = first >>> 1;
            target = first + (victim[bucket] & 1);
            victim[bucket] ^= 1;
        }
        coordinates[target] = missCoordinate;
        values[target] = value;
        valid[target] = true;
        lastCoordinate = missCoordinate;
        lastValue = value;
        lastValid = true;
    }

    static int bucket(int x,int z) {
        return bucket(x,z,BUCKETS - 1);
    }

    private static int bucket(int x,int z,int mask) {
        int hash = x * 0x9e3779b9 ^ Integer.rotateLeft(z * 0x85ebca6b,13);
        return (hash ^ (hash >>> 16)) & mask;
    }
}
