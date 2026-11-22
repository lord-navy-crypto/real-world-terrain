package org.realscale.neoforge.client.clipmap;

import java.util.ArrayList;
import java.util.List;

/**
 * CPU-side layout planner for a nested Geometry Clipmap.
 *
 * <p>This class has no Minecraft or OpenGL dependencies. It defines the
 * snapped windows, nested ring bounds, and incremental sample work required
 * by the renderer. It deliberately does not issue GL calls or generate chunks.
 */
public final class GeometryClipmapLayout {
    public static final int DEFAULT_GRID_CELLS = 128;
    public static final int DEFAULT_LEVEL_COUNT = 6;
    public static final int DEFAULT_BASE_CELL_SPACING_BLOCKS = 4;
    public static final int DEFAULT_NEAR_RENDER_DISTANCE_CHUNKS = 10;

    private final int gridCells;
    private final int levelCount;
    private final int baseCellSpacingBlocks;
    private final int nearRenderDistanceChunks;

    public GeometryClipmapLayout() {
        this(DEFAULT_GRID_CELLS, DEFAULT_LEVEL_COUNT,
                DEFAULT_BASE_CELL_SPACING_BLOCKS,
                DEFAULT_NEAR_RENDER_DISTANCE_CHUNKS);
    }

    public GeometryClipmapLayout(int gridCells, int levelCount,
            int baseCellSpacingBlocks, int nearRenderDistanceChunks) {
        if (gridCells < 16 || (gridCells & (gridCells - 1)) != 0) {
            throw new IllegalArgumentException("gridCells must be a power of two >= 16");
        }
        if (levelCount < 1 || levelCount > 8) {
            throw new IllegalArgumentException("levelCount must be between 1 and 8");
        }
        if (baseCellSpacingBlocks < 1
                || (baseCellSpacingBlocks & (baseCellSpacingBlocks - 1)) != 0) {
            throw new IllegalArgumentException("base cell spacing must be a positive power of two");
        }
        if (nearRenderDistanceChunks < 1) {
            throw new IllegalArgumentException("near render distance must be positive");
        }

        long nearRadius = nearRenderDistanceChunks * 16L;
        long outerHalfExtent = (long) (gridCells / 2) * baseCellSpacingBlocks;
        if (nearRadius % baseCellSpacingBlocks != 0) {
            throw new IllegalArgumentException(
                    "near render radius must align to the finest clipmap grid");
        }
        if (nearRadius >= outerHalfExtent) {
            throw new IllegalArgumentException(
                    "finest clipmap window must extend beyond the vanilla render radius");
        }

        this.gridCells = gridCells;
        this.levelCount = levelCount;
        this.baseCellSpacingBlocks = baseCellSpacingBlocks;
        this.nearRenderDistanceChunks = nearRenderDistanceChunks;
    }

    public Layout compute(double cameraX, double cameraZ) {
        return compute(cameraX, cameraZ, null);
    }

    /**
     * Computes the new snapped windows and the minimum L-shaped sample update
     * for each level relative to a previous layout.
     *
     * <p>Call with {@code previous == null} after a world/dimension/seed change.
     */
    public Layout compute(double cameraX, double cameraZ, Layout previous) {
        validateCameraCoordinate(cameraX);
        validateCameraCoordinate(cameraZ);
        validatePrevious(previous);

        List<Level> levels = new ArrayList<>(levelCount);
        long nearRadius = nearRenderDistanceChunks * 16L;

        for (int index = 0; index < levelCount; index++) {
            long spacing = (long) baseCellSpacingBlocks << index;
            long centerX = snap(cameraX, spacing);
            long centerZ = snap(cameraZ, spacing);
            long halfExtent = (long) (gridCells / 2) * spacing;

            Bounds outer = new Bounds(centerX - halfExtent,
                    centerZ - halfExtent, centerX + halfExtent,
                    centerZ + halfExtent);
            Bounds inner;
            if (index == 0) {
                inner = new Bounds(centerX - nearRadius, centerZ - nearRadius,
                        centerX + nearRadius, centerZ + nearRadius);
            } else {
                // The next coarser ring surrounds the previous level's full
                // window. The small snap offset is handled by the transition
                // patch/geomorph stage in the renderer.
                inner = levels.get(index - 1).outerBounds();
            }

            if (!outer.contains(inner)) {
                throw new IllegalStateException(
                        "clipmap level " + index + " does not contain its inner window");
            }

            UpdatePlan update = createUpdatePlan(index, centerX, centerZ,
                    spacing, previous);
            levels.add(new Level(index, spacing, centerX, centerZ,
                    outer, inner, update));
        }

        return new Layout(gridCells, levelCount, baseCellSpacingBlocks,
                nearRenderDistanceChunks, levels);
    }

    private UpdatePlan createUpdatePlan(int index, long centerX, long centerZ,
            long spacing, Layout previous) {
        long side = (long) gridCells + 1L;
        long fullSampleCount = side * side;
        if (previous == null) {
            return new UpdatePlan(0, 0, fullSampleCount, true);
        }

        Level old = previous.levels().get(index);
        long deltaX = centerX - old.centerX();
        long deltaZ = centerZ - old.centerZ();
        if (deltaX % spacing != 0 || deltaZ % spacing != 0) {
            throw new IllegalStateException("clipmap snap delta is not cell aligned");
        }

        long shiftX = deltaX / spacing;
        long shiftZ = deltaZ / spacing;
        long absX = Math.abs(shiftX);
        long absZ = Math.abs(shiftZ);
        if (absX == 0 && absZ == 0) {
            return new UpdatePlan(0, 0, 0, false);
        }
        if (absX >= gridCells || absZ >= gridCells) {
            return new UpdatePlan(toIntSaturated(shiftX),
                    toIntSaturated(shiftZ), fullSampleCount, true);
        }

        // The newly exposed columns and rows form an L-shaped update region.
        // Subtract their shared corner so each new sample is counted once.
        long newSamples = absX * side + absZ * side - absX * absZ;
        if (newSamples >= fullSampleCount) {
            return new UpdatePlan(toIntSaturated(shiftX),
                    toIntSaturated(shiftZ), fullSampleCount, true);
        }
        return new UpdatePlan(toIntSaturated(shiftX),
                toIntSaturated(shiftZ), newSamples, false);
    }

    private void validatePrevious(Layout previous) {
        if (previous == null) {
            return;
        }
        if (previous.gridCells() != gridCells
                || previous.levelCount() != levelCount
                || previous.baseCellSpacingBlocks() != baseCellSpacingBlocks
                || previous.nearRenderDistanceChunks() != nearRenderDistanceChunks
                || previous.levels().size() != levelCount) {
            throw new IllegalArgumentException(
                    "previous layout was created with a different clipmap configuration");
        }
    }

    private static long snap(double coordinate, long spacing) {
        // floorDiv is important for negative world coordinates: truncation
        // toward zero would make the clipmap jitter asymmetrically at x/z < 0.
        long block = (long) Math.floor(coordinate);
        return Math.floorDiv(block, spacing) * spacing;
    }

    private static void validateCameraCoordinate(double coordinate) {
        if (!Double.isFinite(coordinate)
                || coordinate < Integer.MIN_VALUE
                || coordinate > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "camera coordinate must be finite and inside Minecraft's block-coordinate range");
        }
    }

    private static int toIntSaturated(long value) {
        if (value > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        if (value < Integer.MIN_VALUE) return Integer.MIN_VALUE;
        return (int) value;
    }

    public record Layout(int gridCells, int levelCount,
            int baseCellSpacingBlocks, int nearRenderDistanceChunks,
            List<Level> levels) {
        public Layout {
            levels = List.copyOf(levels);
        }

        public long nearRadiusBlocks() {
            return nearRenderDistanceChunks * 16L;
        }

        public long maxAxisExtentBlocks() {
            return levels.get(levels.size() - 1).outerBounds().widthX() / 2L;
        }
    }

    public record Level(int index, long cellSpacingBlocks,
            long centerX, long centerZ, Bounds outerBounds,
            Bounds innerBounds, UpdatePlan updatePlan) {
    }

    /** Bounds use a half-open interval: [min, max). */
    public record Bounds(long minX, long minZ, long maxX, long maxZ) {
        public Bounds {
            if (maxX <= minX || maxZ <= minZ) {
                throw new IllegalArgumentException("bounds must have positive area");
            }
        }

        public boolean contains(Bounds other) {
            return other.minX >= minX && other.minZ >= minZ
                    && other.maxX <= maxX && other.maxZ <= maxZ;
        }

        public boolean containsStrict(long x, long z) {
            return x > minX && x < maxX && z > minZ && z < maxZ;
        }

        public long widthX() {
            return maxX - minX;
        }

        public long widthZ() {
            return maxZ - minZ;
        }
    }

    public record UpdatePlan(int shiftCellsX, int shiftCellsZ,
            long newSampleVertices, boolean fullRefresh) {
    }
}
