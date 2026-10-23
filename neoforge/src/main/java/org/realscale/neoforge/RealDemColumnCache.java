package org.realscale.neoforge;

/**
 * Bounded per-thread lookup for X/Z-only DEM calculations. Each density
 * evaluation can vary in Y, but elevation and blend weight are identical
 * for a given X/Z coordinate and blend width.
 *
 * Two-way buckets retain both alternating colliding columns instead of
 * repeatedly interpolating identical measured USGS heights. The total
 * 4096-column capacity is UNCHANGED. Evictions only cause recomputation;
 * coordinates and widths are always compared before using a cached value.
 * No interpolation arithmetic or terrain density is changed.
 */
final class RealDemColumnCache {
    private static final int SLOTS = 4096;
    private static final int BUCKETS = SLOTS / 2;
    private static final Column OUTSIDE = new Column(0.0, Double.NaN);

    record Column(double influence, double elevation) {}

    private final RealDemTile dem;
    private final long[] coordinates = new long[SLOTS];
    private final int[] blendWidths = new int[SLOTS];
    private final Column[] values = new Column[SLOTS];
    private final byte[] victim = new byte[BUCKETS];

    // Density evaluation commonly visits many Y cells for the same X/Z.
    // This avoids hashing and array probes on the hot repeated-column path.
    private long lastCoordinate;
    private int lastBlendMeters;
    private Column lastColumn;

    RealDemColumnCache(RealDemTile dem) {
        this.dem = java.util.Objects.requireNonNull(dem);
    }

    Column sample(int x, int z, int blendMeters) {
        final long coordinate = ((long) x << 32) | (z & 0xffffffffL);
        if (lastColumn != null && lastCoordinate == coordinate
                && lastBlendMeters == blendMeters) return lastColumn;
        final int bucket = bucket(x, z, blendMeters);
        final int first = bucket << 1;
        final int second = first + 1;
        final Column a = values[first];
        if (a != null && coordinates[first] == coordinate
                && blendWidths[first] == blendMeters) {
            lastCoordinate = coordinate;
            lastBlendMeters = blendMeters;
            lastColumn = a;
            return a;
        }
        final Column b = values[second];
        if (b != null && coordinates[second] == coordinate
                && blendWidths[second] == blendMeters) {
            lastCoordinate = coordinate;
            lastBlendMeters = blendMeters;
            lastColumn = b;
            return b;
        }

        // Keep original influence and interpolation arithmetic/ordering.
        final double weight = dem.influence(x, z, blendMeters);
        // worldSolidYAt() must not be called for the zero-weight boundary/outside.
        final Column result = weight <= 0.0
                ? OUTSIDE : new Column(weight, dem.worldSolidYAt(x, z));
        final int slot;
        if (a == null) slot = first;
        else if (b == null) slot = second;
        else {
            slot = first + (victim[bucket] & 1);
            victim[bucket] ^= 1;
        }
        coordinates[slot] = coordinate;
        blendWidths[slot] = blendMeters;
        values[slot] = result;
        lastCoordinate = coordinate;
        lastBlendMeters = blendMeters;
        lastColumn = result;
        return result;
    }

    // Package-visible so the pure Java parity smoke can construct deliberate
    // collision probes; the cache hash affects hit rate, never world results.
    static int bucket(int x, int z, int blendMeters) {
        int hash = x * 0x9e3779b9 ^ Integer.rotateLeft(z * 0x85ebca6b, 13);
        hash ^= blendMeters * 0xc2b2ae35;
        return (hash ^ (hash >>> 16)) & (BUCKETS - 1);
    }
}
