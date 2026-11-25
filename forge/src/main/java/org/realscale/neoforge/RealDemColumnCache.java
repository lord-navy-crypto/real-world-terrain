package org.realscale.neoforge;

/**
 * Bounded per-thread lookup for X/Z-only DEM calculations. Each density evaluation
 * can vary in Y, but elevation and blend weight are identical for a given X/Z.
 * This cache never changes the noise sampling grid or the original arithmetic.
 */
final class RealDemColumnCache {
    private static final int SLOTS = 4096;
    private static final Column OUTSIDE = new Column(0.0, Double.NaN);

    record Column(double influence, double elevation) {}

    private final RealDemTile dem;
    private final int capacity;
    private final int slotMask;
    private final long[] coordinates;
    private final int[] blendWidths;
    private final Column[] values;

    // Density evaluation commonly visits many Y cells for the same X/Z.
    // This avoids hashing and array probes on the hot repeated-column path.
    private long lastCoordinate;
    private int lastBlendMeters;
    private Column lastColumn;

    RealDemColumnCache(RealDemTile dem) {
        this(dem, RealScalePerformanceProfile.cacheSlots("real_dem_column", SLOTS));
    }

    RealDemColumnCache(RealDemTile dem, int slots) {
        if (slots < 16 || (slots & (slots - 1)) != 0)
            throw new IllegalArgumentException("Cache slots must be a power of two >= 16");
        this.dem = java.util.Objects.requireNonNull(dem);
        this.capacity = slots;
        this.slotMask = slots - 1;
        this.coordinates = new long[slots];
        this.blendWidths = new int[slots];
        this.values = new Column[slots];
    }

    int capacity() {
        return capacity;
    }

    Column sample(int x, int z, int blendMeters) {
        final long coordinate = ((long) x << 32) | (z & 0xffffffffL);
        if (lastColumn != null && lastCoordinate == coordinate
                && lastBlendMeters == blendMeters) return lastColumn;
        final int slot = index(x, z, blendMeters);
        final Column previous = values[slot];
        if (previous != null && coordinates[slot] == coordinate && blendWidths[slot] == blendMeters) {
            lastCoordinate = coordinate;
            lastBlendMeters = blendMeters;
            lastColumn = previous;
            return previous;
        }

        final double weight = dem.influence(x, z, blendMeters);
        // worldSolidYAt() must not be called for the zero-weight boundary/outside.
        final Column result = weight <= 0.0 ? OUTSIDE : new Column(weight, dem.worldSolidYAt(x, z));
        coordinates[slot] = coordinate;
        blendWidths[slot] = blendMeters;
        values[slot] = result;
        lastCoordinate = coordinate;
        lastBlendMeters = blendMeters;
        lastColumn = result;
        return result;
    }

    private int index(int x, int z, int blendMeters) {
        int hash = x * 0x9e3779b9 ^ Integer.rotateLeft(z * 0x85ebca6b, 13);
        hash ^= blendMeters * 0xc2b2ae35;
        return (hash ^ (hash >>> 16)) & slotMask;
    }
}
