package org.realscale.neoforge.client.clipmap;

import java.util.ArrayList;
import java.util.List;

/**
 * CPU-side planner for a nested, world-aligned Geometry Clipmap.
 *
 * <p>All levels share one centre snapped to the penultimate level's spacing.
 * That keeps every nested ring boundary on a vertex row of both adjacent
 * levels, avoiding cracks without a camera-following grid shift every tick.
 * The camera can move within the protected inner margin; the complete stack
 * recentres only when it crosses the shared snap cell.
 *
 * <p>No Minecraft chunks, block states, or OpenGL objects are created here.
 */
public final class GeometryClipmapLayout {
    public static final int DEFAULT_GRID_CELLS = 128;
    public static final int MAX_GRID_CELLS = 256;
    public static final int DEFAULT_LEVEL_COUNT = 6;
    public static final int DEFAULT_BASE_CELL_SPACING_BLOCKS = 4;
    public static final int DEFAULT_NEAR_RENDER_DISTANCE_CHUNKS = 10;

    private final int gridCells;
    private final int levelCount;
    private final int baseCellSpacingBlocks;
    private final int nearRenderDistanceChunks;
    private final long effectiveInnerRadiusBlocks;

    public GeometryClipmapLayout() {
        this(DEFAULT_GRID_CELLS, DEFAULT_LEVEL_COUNT,
                DEFAULT_BASE_CELL_SPACING_BLOCKS,
                DEFAULT_NEAR_RENDER_DISTANCE_CHUNKS);
    }

    public GeometryClipmapLayout(int gridCells, int levelCount,
            int baseCellSpacingBlocks, int nearRenderDistanceChunks) {
        if (gridCells < 16 || gridCells > MAX_GRID_CELLS || (gridCells & (gridCells - 1)) != 0) {
            throw new IllegalArgumentException("gridCells must be a power of two >= 16");
        }
        if (levelCount < 2 || levelCount > 8) {
            throw new IllegalArgumentException("levelCount must be between 2 and 8");
        }
        if (baseCellSpacingBlocks < 1
                || (baseCellSpacingBlocks & (baseCellSpacingBlocks - 1)) != 0) {
            throw new IllegalArgumentException("base cell spacing must be a positive power of two");
        }
        if (nearRenderDistanceChunks < 2 || nearRenderDistanceChunks > 64) {
            throw new IllegalArgumentException("near render distance must be between 2 and 64 chunks");
        }

        long nearRadius = nearRenderDistanceChunks * 16L;
        long centreSnapSpacing = (long) baseCellSpacingBlocks << (levelCount - 1);
        long cameraOffsetAllowance = centreSnapSpacing / 2L;
        // Reserve an inner hole that remains inside vanilla's near field
        // even when the common snapped centre lags behind the camera.
        if (nearRadius <= cameraOffsetAllowance) {
            throw new IllegalArgumentException(
                    "near view distance must exceed half the clipmap snap spacing");
        }
        long protectedInnerRadius = nearRadius - cameraOffsetAllowance;
        long finestHalfExtent = (long) (gridCells / 2) * baseCellSpacingBlocks;
        if (nearRadius % baseCellSpacingBlocks != 0) {
            throw new IllegalArgumentException(
                    "near render radius must align to the finest clipmap grid");
        }
        if (protectedInnerRadius >= finestHalfExtent) {
            throw new IllegalArgumentException(
                    "finest clipmap window must extend beyond its camera-safe inner radius");
        }

        this.gridCells = gridCells;
        this.levelCount = levelCount;
        this.baseCellSpacingBlocks = baseCellSpacingBlocks;
        this.nearRenderDistanceChunks = nearRenderDistanceChunks;
        this.effectiveInnerRadiusBlocks = protectedInnerRadius;
    }

    /**
     * Selects the coarsest safe base spacing for the requested vanilla radius.
     * The standard 8–10 chunk case retains four-block finest samples.
     */
    public static GeometryClipmapLayout forRenderDistanceChunks(int chunks) {
        return forRenderDistanceChunks(chunks, DEFAULT_GRID_CELLS);
    }

    /**
     * Selects a requested far-field grid while preserving the vanilla near-field
     * overlap. Short range uses fewer height samples; the grid is promoted only
     * when the requested size would expose an unsampled gap beside vanilla terrain.
     */
    public static GeometryClipmapLayout forRenderDistanceChunks(
            int chunks, int requestedGridCells) {
        if (chunks < 2 || chunks > 64) {
            throw new IllegalArgumentException("render distance must be between 2 and 64 chunks");
        }
        if (requestedGridCells < 64 || requestedGridCells > MAX_GRID_CELLS
                || (requestedGridCells & (requestedGridCells - 1)) != 0) {
            throw new IllegalArgumentException(
                    "requested clipmap grid must be 64, 128, or 256 cells");
        }
        int spacing = DEFAULT_BASE_CELL_SPACING_BLOCKS;
        long nearRadius = chunks * 16L;
        // At short view distances, shrink the snap cell instead of leaving
        // an unsampled clipmap hole outside the loaded vanilla terrain.
        while (((long) spacing << (DEFAULT_LEVEL_COUNT - 2)) >= nearRadius) {
            spacing >>= 1;
        }
        while (chunks > 4L * spacing) {
            spacing <<= 1;
        }
        // A 32-block grid cannot align every chunk radius (e.g. 33*16).
        // Prefer a smaller aligned grid to an invalid or cracked hole.
        while (nearRadius % spacing != 0L) {
            spacing >>= 1;
        }

        long cameraOffsetAllowance =
                ((long) spacing << (DEFAULT_LEVEL_COUNT - 1)) / 2L;
        long protectedInnerRadius = nearRadius - cameraOffsetAllowance;
        int effectiveGridCells = requestedGridCells;
        while (effectiveGridCells < MAX_GRID_CELLS
                && protectedInnerRadius >= (long) (effectiveGridCells / 2) * spacing) {
            effectiveGridCells <<= 1;
        }
        if (protectedInnerRadius >= (long) (effectiveGridCells / 2) * spacing) {
            throw new IllegalArgumentException(
                    "clipmap grid cannot cover the requested vanilla near distance");
        }
        return new GeometryClipmapLayout(effectiveGridCells,
                DEFAULT_LEVEL_COUNT, spacing, chunks);
    }

    public int gridCells() {
        return gridCells;
    }

    public Layout compute(double cameraX, double cameraZ) {
        return compute(cameraX, cameraZ, null);
    }

    /**
     * Computes snapped windows and minimum newly exposed L-shaped sample work.
     * Pass {@code previous == null} after a world, dimension, seed, or config change.
     */
    public Layout compute(double cameraX, double cameraZ, Layout previous) {
        validateCameraCoordinate(cameraX);
        validateCameraCoordinate(cameraZ);
        validatePrevious(previous);

        List<Level> levels = new ArrayList<>(levelCount);
        long centreSnapSpacing = (long) baseCellSpacingBlocks << (levelCount - 1);
        long sharedCenterX = snapNearest(cameraX, centreSnapSpacing);
        long sharedCenterZ = snapNearest(cameraZ, centreSnapSpacing);

        for (int index = 0; index < levelCount; index++) {
            long spacing = (long) baseCellSpacingBlocks << index;
            long halfExtent = (long) (gridCells / 2) * spacing;
            Bounds outer = new Bounds(sharedCenterX - halfExtent,
                    sharedCenterZ - halfExtent, sharedCenterX + halfExtent,
                    sharedCenterZ + halfExtent);
            Bounds inner;
            if (index == 0) {
                inner = new Bounds(sharedCenterX - effectiveInnerRadiusBlocks,
                        sharedCenterZ - effectiveInnerRadiusBlocks,
                        sharedCenterX + effectiveInnerRadiusBlocks,
                        sharedCenterZ + effectiveInnerRadiusBlocks);
            } else {
                inner = levels.get(index - 1).outerBounds();
            }

            if (!outer.contains(inner)) {
                throw new IllegalStateException(
                        "clipmap level " + index + " does not contain its inner window");
            }
            if (!isGridAligned(inner.minX() - outer.minX(), spacing)
                    || !isGridAligned(inner.minZ() - outer.minZ(), spacing)
                    || !isGridAligned(inner.maxX() - outer.minX(), spacing)
                    || !isGridAligned(inner.maxZ() - outer.minZ(), spacing)) {
                throw new IllegalStateException(
                        "clipmap ring boundaries must align to level " + index + " vertices");
            }

            UpdatePlan update = createUpdatePlan(index, sharedCenterX, sharedCenterZ,
                    spacing, previous);
            levels.add(new Level(index, spacing, sharedCenterX, sharedCenterZ,
                    outer, inner, update));
        }

        return new Layout(gridCells, levelCount, baseCellSpacingBlocks,
                nearRenderDistanceChunks, effectiveInnerRadiusBlocks, levels);
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

        long newSamples = absX * side + absZ * side - absX * absZ;
        if (newSamples >= fullSampleCount) {
            return new UpdatePlan(toIntSaturated(shiftX),
                    toIntSaturated(shiftZ), fullSampleCount, true);
        }
        return new UpdatePlan(toIntSaturated(shiftX),
                toIntSaturated(shiftZ), newSamples, false);
    }

    private void validatePrevious(Layout previous) {
        if (previous == null) return;
        if (previous.gridCells() != gridCells
                || previous.levelCount() != levelCount
                || previous.baseCellSpacingBlocks() != baseCellSpacingBlocks
                || previous.nearRenderDistanceChunks() != nearRenderDistanceChunks
                || previous.levels().size() != levelCount) {
            throw new IllegalArgumentException(
                    "previous layout was created with a different clipmap configuration");
        }
    }

    private static long snapNearest(double coordinate, long spacing) {
        long block = (long) Math.floor(coordinate);
        return Math.floorDiv(block + spacing / 2L, spacing) * spacing;
    }

    private static boolean isGridAligned(long offsetFromOrigin, long spacing) {
        return Math.floorMod(offsetFromOrigin, spacing) == 0L;
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
            long effectiveInnerRadiusBlocks, List<Level> levels) {
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

        public long widthX() { return maxX - minX; }
        public long widthZ() { return maxZ - minZ; }
    }

    public record UpdatePlan(int shiftCellsX, int shiftCellsZ,
            long newSampleVertices, boolean fullRefresh) {
    }
}
