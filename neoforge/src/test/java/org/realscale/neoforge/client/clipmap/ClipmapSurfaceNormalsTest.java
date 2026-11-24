package org.realscale.neoforge.client.clipmap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ClipmapSurfaceNormalsTest {
    @Test
    void flatSurfaceProducesUpwardNormalsAcrossTheWholeRing() {
        int cells = 16;
        float[] heights = new float[(cells + 1) * (cells + 1)];
        java.util.Arrays.fill(heights, 128.0f);

        float[] normals = ClipmapSurfaceNormals.compute(
                heights, cells, 4, 4, 2);
        for (int i = 0; i < heights.length; i++) {
            assertEquals(0.0f, normals[i * 3], 1.0e-6f);
            assertEquals(1.0f, normals[i * 3 + 1], 1.0e-6f);
            assertEquals(0.0f, normals[i * 3 + 2], 1.0e-6f);
        }
    }

    @Test
    void planarSlopeProducesConsistentNormalsAtInteriorAndOuterEdges() {
        int cells = 16;
        int spacing = 4;
        float[] heights = new float[(cells + 1) * (cells + 1)];
        for (int z = 0; z <= cells; z++) {
            for (int x = 0; x <= cells; x++) {
                heights[z * (cells + 1) + x] = 2.0f * x * spacing
                        - 0.5f * z * spacing;
            }
        }

        float[] normals = ClipmapSurfaceNormals.compute(
                heights, cells, spacing, 0, 2);
        float length = (float) Math.sqrt(5.25f);
        for (int[] point : new int[][]{{8, 8}, {0, 0}, {16, 16}, {0, 16}}) {
            int index = (point[1] * (cells + 1) + point[0]) * 3;
            assertEquals(-2.0f / length, normals[index], 1.0e-5f);
            assertEquals(1.0f / length, normals[index + 1], 1.0e-5f);
            assertEquals(0.5f / length, normals[index + 2], 1.0e-5f);
        }
    }

    @Test
    void widerNormalFootprintFiltersAlternatingCellScaleHeightNoise() {
        int cells = 16;
        float[] heights = new float[(cells + 1) * (cells + 1)];
        for (int z = 0; z <= cells; z++) {
            for (int x = 0; x <= cells; x++) {
                heights[z * (cells + 1) + x] = (x & 1) == 0 ? 0.0f : 12.0f;
            }
        }

        float[] normals = ClipmapSurfaceNormals.compute(
                heights, cells, 4, 0, 2);
        int index = (8 * (cells + 1) + 8) * 3;
        assertEquals(0.0f, normals[index], 1.0e-6f);
        assertEquals(1.0f, normals[index + 1], 1.0e-6f);
        assertEquals(0.0f, normals[index + 2], 1.0e-6f);
    }

    @Test
    void innerHoleBoundaryUsesValidOneSidedSamplesWithoutNaNs() {
        int cells = 16;
        int spacing = 4;
        int halfHole = 4;
        float[] heights = new float[(cells + 1) * (cells + 1)];
        for (int z = 0; z <= cells; z++) {
            for (int x = 0; x <= cells; x++) {
                heights[z * (cells + 1) + x] = 0.5f * x * spacing
                        + 0.25f * z * spacing;
            }
        }
        for (int z = 5; z < 12; z++) {
            for (int x = 5; x < 12; x++) {
                heights[z * (cells + 1) + x] = Float.NaN;
            }
        }

        float[] normals = ClipmapSurfaceNormals.compute(
                heights, cells, spacing, halfHole, 2);
        int boundary = (8 * (cells + 1) + 4) * 3;
        float length = (float) Math.sqrt(1.3125f);
        assertEquals(-0.5f / length, normals[boundary], 1.0e-5f);
        assertEquals(1.0f / length, normals[boundary + 1], 1.0e-5f);
        assertEquals(-0.25f / length, normals[boundary + 2], 1.0e-5f);
        for (float value : normals) assertTrue(Float.isFinite(value));
    }
}
