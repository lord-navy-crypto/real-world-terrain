package org.realscale.neoforge.client.clipmap;

import java.util.BitSet;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/**
 * Sparse CPU-side height samples for one Geometry Clipmap ring.
 *
 * <p>Only vertices outside the finer level's inner window are sampled.
 * When a previous grid is supplied, samples whose world coordinates remain
 * in the new window are copied; only newly exposed rows/columns invoke the
 * sampler. No Minecraft chunks or block states are requested.
 */
public final class ClipmapHeightGrid {
    private final int gridCells;
    private final int gridSide;
    private final int cellSpacingBlocks;
    private final long originX;
    private final long originZ;
    private final float[] heights;
    private final BitSet sampled;
    private final int newlySampledVertexCount;

    private ClipmapHeightGrid(int gridCells, int cellSpacingBlocks,
            long originX, long originZ, float[] heights, BitSet sampled,
            int newlySampledVertexCount) {
        this.gridCells = gridCells;
        this.gridSide = gridCells + 1;
        this.cellSpacingBlocks = cellSpacingBlocks;
        this.originX = originX;
        this.originZ = originZ;
        this.heights = heights;
        this.sampled = sampled;
        this.newlySampledVertexCount = newlySampledVertexCount;
    }

    public static ClipmapHeightGrid sample(
            GeometryClipmapLayout.Level level,
            int gridCells,
            SurfaceHeightSampler sampler) {
        return sample(level, gridCells, sampler, null);
    }

    public static ClipmapHeightGrid sample(
            GeometryClipmapLayout.Level level,
            int gridCells,
            SurfaceHeightSampler sampler,
            ClipmapHeightGrid previous) {
        return sample(level, gridCells, sampler, previous, () -> true);
    }

    /**
     * Tests cancellation once every 32 grid vertices. This is critical when
     * density sampling is expensive: changing worlds or teleporting should
     * release the only sampler thread before it finishes an obsolete ring.
     */
    public static ClipmapHeightGrid sample(
            GeometryClipmapLayout.Level level,
            int gridCells,
            SurfaceHeightSampler sampler,
            ClipmapHeightGrid previous,
            BooleanSupplier stillCurrent) {
        return sample(level, gridCells, sampler, previous, stillCurrent, ignored -> { });
    }

    /**
     * Reports monotonic grid-scan progress while retaining cancellation checks.
     * Progress callbacks run on the sampler thread and should remain lightweight.
     */
    public static ClipmapHeightGrid sample(
            GeometryClipmapLayout.Level level,
            int gridCells,
            SurfaceHeightSampler sampler,
            ClipmapHeightGrid previous,
            BooleanSupplier stillCurrent,
            IntConsumer progress) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(sampler, "sampler");
        Objects.requireNonNull(stillCurrent, "stillCurrent");
        Objects.requireNonNull(progress, "progress");
        if (gridCells < 1) {
            throw new IllegalArgumentException("gridCells must be positive");
        }

        GeometryClipmapLayout.Bounds outer = level.outerBounds();
        long spacing = level.cellSpacingBlocks();
        if (outer.widthX() != (long) gridCells * spacing
                || outer.widthZ() != (long) gridCells * spacing) {
            throw new IllegalArgumentException(
                    "outer bounds do not match the requested grid dimensions");
        }
        if (previous != null && (previous.gridCells != gridCells
                || previous.cellSpacingBlocks != spacing)) {
            throw new IllegalArgumentException(
                    "previous grid has incompatible resolution or cell spacing");
        }

        int side = gridCells + 1;
        float[] heights = new float[Math.multiplyExact(side, side)];
        BitSet sampled = new BitSet(heights.length);
        GeometryClipmapLayout.Bounds hole = level.innerBounds();
        int newlySampled = 0;

        for (int gridZ = 0; gridZ < side; gridZ++) {
            long worldZ = outer.minZ() + (long) gridZ * spacing;
            for (int gridX = 0; gridX < side; gridX++) {
                long worldX = outer.minX() + (long) gridX * spacing;
                int index = gridZ * side + gridX;
                if ((index & 31) == 0 && !stillCurrent.getAsBoolean()) {
                    throw new CancellationException("Obsolete RealScale clipmap sampling");
                }
                if ((index & 31) == 0) {
                    progress.accept((int) (index * 100L / heights.length));
                }

                if (hole.containsStrict(worldX, worldZ)) {
                    heights[index] = Float.NaN;
                    continue;
                }

                int oldX = -1;
                int oldZ = -1;
                if (previous != null) {
                    long dx = worldX - previous.originX;
                    long dz = worldZ - previous.originZ;
                    if (dx % spacing == 0L && dz % spacing == 0L) {
                        long candidateX = dx / spacing;
                        long candidateZ = dz / spacing;
                        if (candidateX >= 0 && candidateX < previous.gridSide
                                && candidateZ >= 0 && candidateZ < previous.gridSide) {
                            oldX = (int) candidateX;
                            oldZ = (int) candidateZ;
                        }
                    }
                }

                if (oldX >= 0 && previous.isSampledAt(oldX, oldZ)) {
                    heights[index] = previous.heightAt(oldX, oldZ);
                } else {
                    float height = sampler.sampleHeight(
                            Math.toIntExact(worldX), Math.toIntExact(worldZ));
                    if (!Float.isFinite(height)) {
                        throw new IllegalArgumentException(
                                "height sampler returned a non-finite value at "
                                        + worldX + "," + worldZ);
                    }
                    heights[index] = height;
                    newlySampled++;
                }
                sampled.set(index);
            }
        }

        progress.accept(100);
        return new ClipmapHeightGrid(gridCells,
                Math.toIntExact(spacing), outer.minX(), outer.minZ(),
                heights, sampled, newlySampled);
    }

    public int gridCells() { return gridCells; }
    public int gridSide() { return gridSide; }
    public int cellSpacingBlocks() { return cellSpacingBlocks; }
    public long originX() { return originX; }
    public long originZ() { return originZ; }
    public int sampledVertexCount() { return sampled.cardinality(); }
    public int newlySampledVertexCount() { return newlySampledVertexCount; }

    public boolean isSampledAt(int gridX, int gridZ) {
        return sampled.get(index(gridX, gridZ));
    }

    public float heightAt(int gridX, int gridZ) {
        int index = index(gridX, gridZ);
        if (!sampled.get(index)) {
            throw new IllegalStateException(
                    "vertex is inside a finer clipmap window and was intentionally not sampled");
        }
        return heights[index];
    }

    public float[] copyHeights() {
        return heights.clone();
    }

    private int index(int gridX, int gridZ) {
        if (gridX < 0 || gridX >= gridSide
                || gridZ < 0 || gridZ >= gridSide) {
            throw new IndexOutOfBoundsException(
                    "clipmap grid coordinate outside " + gridSide + "x" + gridSide);
        }
        return gridZ * gridSide + gridX;
    }

    @FunctionalInterface
    public interface SurfaceHeightSampler {
        float sampleHeight(int blockX, int blockZ);
    }
}
