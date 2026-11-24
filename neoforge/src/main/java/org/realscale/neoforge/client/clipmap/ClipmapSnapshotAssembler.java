package org.realscale.neoforge.client.clipmap;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Publishes contiguous LOD prefixes without mixing snapshot centres.
 *
 * <p>Network packets for different request generations, snap centres,
 * distances, or dimensions must never be combined into one GPU frame.
 * This class is deliberately free of game and OpenGL dependencies so the
 * contract can be unit-tested.
 */
public final class ClipmapSnapshotAssembler<T> {
    private final int levelCount;
    private final Map<Integer, T> pending = new HashMap<>();
    private SnapshotKey pendingKey;
    private Map<Integer, T> active = Map.of();
    private SnapshotKey activeKey;

    public ClipmapSnapshotAssembler(int levelCount) {
        if (levelCount < 2 || levelCount > 8) {
            throw new IllegalArgumentException("level count must be 2..8");
        }
        this.levelCount = levelCount;
    }

    /**
     * Returns true when an internally consistent renderable prefix is published.
     * A complete previous window remains visible until a complete replacement.
     */
    public boolean accept(long requestId, int level, int gridCells, int cellSpacing,
            long originX, long originZ, int nearChunks, String dimension,
            T tile) {
        Objects.requireNonNull(tile, "tile");
        if (requestId <= 0L || level < 0 || level >= levelCount || gridCells < 16
                || (gridCells & (gridCells - 1)) != 0
                || cellSpacing <= 0 || nearChunks < 2 || nearChunks > 64
                || dimension == null || dimension.isBlank()) {
            throw new IllegalArgumentException("invalid clipmap snapshot metadata");
        }
        int baseSpacing = cellSpacing >> level;
        if (baseSpacing < 1 || (baseSpacing << level) != cellSpacing
                || (baseSpacing & (baseSpacing - 1)) != 0) {
            throw new IllegalArgumentException(
                    "clipmap spacing must double at each level");
        }
        long half = ((long) gridCells / 2L) * cellSpacing;
        SnapshotKey key = new SnapshotKey(requestId, dimension, nearChunks,
                gridCells, baseSpacing,
                Math.addExact(originX, half),
                Math.addExact(originZ, half));

        if (activeKey != null && requestId < activeKey.requestId()) return false;
        if (key.equals(activeKey) && active.size() == levelCount) return false;
        if (!key.equals(pendingKey)) {
            pending.clear();
            pendingKey = key;
        }
        if (pending.putIfAbsent(level, tile) != null) return false;
        int prefix = 0;
        while (pending.containsKey(prefix)) prefix++;
        if (prefix == 0) return false;
        if (activeKey != null && !key.equals(activeKey) && active.size() == levelCount
                && prefix != levelCount) return false;
        if (key.equals(activeKey) && prefix <= active.size()) return false;
        Map<Integer, T> ready = new HashMap<>();
        for (int i = 0; i < prefix; i++) ready.put(i, pending.get(i));
        active = Map.copyOf(ready);
        activeKey = key;
        if (prefix == levelCount) {
            pending.clear();
            pendingKey = null;
        }
        return true;
    }

    /**
     * Keep the old complete window while it covers the camera, but promote an
     * available replacement prefix when moving out of that window. Never mix
     * origins: a partial replacement must itself cover the camera.
     */
    public boolean promotePendingForCamera(double x, double z) {
        if (pendingKey == null || activeKey == null || pendingKey.equals(activeKey)
                || covers(activeKey, x, z) || !covers(pendingKey, x, z)) return false;
        int prefix = 0;
        while (pending.containsKey(prefix)) prefix++;
        if (prefix == 0) return false;
        Map<Integer, T> ready = new HashMap<>();
        for (int i = 0; i < prefix; i++) ready.put(i, pending.get(i));
        active = Map.copyOf(ready);
        activeKey = pendingKey;
        return true;
    }

    private static boolean covers(SnapshotKey key, double x, double z) {
        double half = key.gridCells() * (double) key.baseSpacing() / 2.0;
        return Math.abs(x - key.centerX()) <= half && Math.abs(z - key.centerZ()) <= half;
    }

    public Map<Integer, T> activeTiles() { return active; }
    public SnapshotKey activeKey() { return activeKey; }

    /** Number of distinct LOD levels received for the pending snapshot. */
    public int pendingLevelCount() { return pending.size(); }

    /** Bit mask of pending LOD levels for packet-delivery diagnostics. */
    public int pendingLevelMask() {
        int mask = 0;
        for (int level : pending.keySet()) mask |= 1 << level;
        return mask;
    }

    public void clear() {
        pending.clear();
        pendingKey = null;
        active = Map.of();
        activeKey = null;
    }

    public record SnapshotKey(long requestId, String dimension, int nearChunks,
            int gridCells, int baseSpacing, long centerX, long centerZ) {}
}
