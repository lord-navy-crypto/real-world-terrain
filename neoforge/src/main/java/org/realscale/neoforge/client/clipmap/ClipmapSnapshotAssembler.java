package org.realscale.neoforge.client.clipmap;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Reassembles one complete multilevel height-tile snapshot before publishing.
 *
 * <p>Network packets for different snap centres, distances, or dimensions
 * must never be combined into one GPU frame. This class is deliberately free
 * of game and OpenGL dependencies so the contract can be unit-tested.
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
     * Returns true exactly when a complete, internally consistent set is
     * published. While incomplete the last complete snapshot is preserved.
     */
    public boolean accept(int level, int gridCells, int cellSpacing,
            long originX, long originZ, int nearChunks, String dimension,
            T tile) {
        Objects.requireNonNull(tile, "tile");
        if (level < 0 || level >= levelCount || gridCells < 16
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
        SnapshotKey key = new SnapshotKey(dimension, nearChunks, gridCells,
                baseSpacing,
                Math.addExact(originX, half),
                Math.addExact(originZ, half));

        if (!key.equals(pendingKey)) {
            pending.clear();
            pendingKey = key;
        }
        pending.put(level, tile);
        if (pending.size() != levelCount) return false;
        active = Map.copyOf(pending);
        activeKey = pendingKey;
        pending.clear();
        pendingKey = null;
        return true;
    }

    public Map<Integer, T> activeTiles() {
        return active;
    }

    public SnapshotKey activeKey() {
        return activeKey;
    }

    public void clear() {
        pending.clear();
        pendingKey = null;
        active = Map.of();
        activeKey = null;
    }

    public record SnapshotKey(String dimension, int nearChunks, int gridCells,
            int baseSpacing, long centerX, long centerZ) {
    }
}
