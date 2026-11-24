package org.realscale.neoforge.client.clipmap;

import java.util.Objects;

/**
 * Builds stable world-surface normals from a bounded height grid.
 *
 * <p>Normals use a configurable multi-cell footprint to suppress cell-scale
 * height noise while preserving the sampled geometry and its silhouette.
 * Neighbours inside the finer clipmap hole are ignored; ring and hole borders
 * fall back to one-sided differences instead of sampling placeholder heights.
 */
public final class ClipmapSurfaceNormals {
    private ClipmapSurfaceNormals() {
    }

    public static float[] compute(float[] heights, int cells, int spacingBlocks,
            int holeHalfCells, int radiusCells) {
        Objects.requireNonNull(heights, "heights");
        if (cells < 1 || spacingBlocks < 1 || holeHalfCells < 0
                || holeHalfCells >= cells / 2 || radiusCells < 1 || radiusCells > 8) {
            throw new IllegalArgumentException("invalid surface-normal grid parameters");
        }
        int side = cells + 1;
        if (heights.length != Math.multiplyExact(side, side)) {
            throw new IllegalArgumentException("height array does not match grid dimensions");
        }

        float[] normals = new float[Math.multiplyExact(heights.length, 3)];
        for (int z = 0; z <= cells; z++) {
            for (int x = 0; x <= cells; x++) {
                int index = z * side + x;
                float center = heights[index];
                int normalIndex = index * 3;
                if (!Float.isFinite(center) || insideHole(x, z, cells, holeHalfCells)) {
                    normals[normalIndex + 1] = 1.0f;
                    continue;
                }

                float slopeX = axisSlope(heights, cells, spacingBlocks,
                        holeHalfCells, radiusCells, x, z, center, true);
                float slopeZ = axisSlope(heights, cells, spacingBlocks,
                        holeHalfCells, radiusCells, x, z, center, false);
                float nx = -slopeX;
                float ny = 1.0f;
                float nz = -slopeZ;
                float inverseLength = 1.0f / (float) Math.sqrt(
                        nx * nx + ny * ny + nz * nz);
                normals[normalIndex] = nx * inverseLength;
                normals[normalIndex + 1] = ny * inverseLength;
                normals[normalIndex + 2] = nz * inverseLength;
            }
        }
        return normals;
    }

    private static float axisSlope(float[] heights, int cells, int spacing,
            int holeHalfCells, int radius, int x, int z,
            float center, boolean xAxis) {
        int side = cells + 1;
        for (int step = radius; step >= 1; step--) {
            float negative = sample(heights, cells, holeHalfCells,
                    xAxis ? x - step : x, xAxis ? z : z - step);
            float positive = sample(heights, cells, holeHalfCells,
                    xAxis ? x + step : x, xAxis ? z : z + step);
            if (Float.isFinite(negative) && Float.isFinite(positive)) {
                return (positive - negative) / (2.0f * step * spacing);
            }
        }

        // At an outer edge or beside the finer ring's hole, use the nearest
        // valid one-sided sample rather than a zero-filled/placeholder texel.
        for (int step = 1; step <= radius; step++) {
            float positive = sample(heights, cells, holeHalfCells,
                    xAxis ? x + step : x, xAxis ? z : z + step);
            if (Float.isFinite(positive)) {
                return (positive - center) / (step * spacing);
            }
            float negative = sample(heights, cells, holeHalfCells,
                    xAxis ? x - step : x, xAxis ? z : z - step);
            if (Float.isFinite(negative)) {
                return (center - negative) / (step * spacing);
            }
        }
        return 0.0f;
    }

    private static float sample(float[] heights, int cells, int holeHalfCells,
            int x, int z) {
        if (x < 0 || x > cells || z < 0 || z > cells
                || insideHole(x, z, cells, holeHalfCells)) {
            return Float.NaN;
        }
        return heights[z * (cells + 1) + x];
    }

    private static boolean insideHole(int x, int z, int cells, int halfHole) {
        int center = cells / 2;
        return halfHole > 0
                && Math.abs(x - center) < halfHole
                && Math.abs(z - center) < halfHole;
    }
}
