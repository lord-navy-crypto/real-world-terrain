package org.realscale.neoforge.client.clipmap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ClipmapTriangleUvTest {
    @Test
    void topSurfaceUsesOneWorldAlignedUvDomain() {
        float[] uv = new float[6];
        ClipmapTriangleUv.computeWorldAlignedInto(
                new float[]{0, 0, 16}, new float[]{10, 12, 11},
                new float[]{0, 16, 16}, new int[]{0, 0, 1},
                new int[]{0, 1, 1}, false, 64.0f, -72.0f, 2032.0f, uv);

        assertNonDegenerate(uv);
        assertInsideSprite(uv);
        assertEquals(8.0f, uv[0], 1.0e-6f);
        assertEquals(8.0f, uv[1], 1.0e-6f);
        assertEquals(9.5f, uv[4], 1.0e-6f);
        assertEquals(9.5f, uv[5], 1.0e-6f);
    }

    @Test
    void adjacentTrianglesShareIdenticalUvsAlongTheirCommonEdge() {
        float[] first = new float[6];
        float[] second = new float[6];
        ClipmapTriangleUv.computeWorldAlignedInto(
                new float[]{0, 16, 0}, new float[]{10, 11, 12},
                new float[]{0, 0, 16}, new int[]{0, 1, 0},
                new int[]{0, 0, 1}, false, 64.0f, -72.0f, 2032.0f, first);
        ClipmapTriangleUv.computeWorldAlignedInto(
                new float[]{16, 16, 0}, new float[]{11, 13, 12},
                new float[]{0, 16, 16}, new int[]{1, 1, 0},
                new int[]{0, 1, 1}, false, 64.0f, -72.0f, 2032.0f, second);

        // (16,0) and (0,16) are shared by both triangles.
        assertEquals(first[2], second[0], 1.0e-6f);
        assertEquals(first[3], second[1], 1.0e-6f);
        assertEquals(first[4], second[4], 1.0e-6f);
        assertEquals(first[5], second[5], 1.0e-6f);
    }

    @Test
    void samePhysicalPositionKeepsTheSameUvAcrossLodSpacing() {
        float[] fine = new float[6];
        float[] coarse = new float[6];
        ClipmapTriangleUv.computeWorldAlignedInto(
                new float[]{0, 16, 0}, new float[]{100, 101, 102},
                new float[]{0, 0, 16}, new int[]{0, 1, 0},
                new int[]{0, 0, 1}, false, 4096.0f, -72.0f, 2032.0f, fine);
        ClipmapTriangleUv.computeWorldAlignedInto(
                new float[]{16, 32, 16}, new float[]{100, 101, 102},
                new float[]{0, 0, 16}, new int[]{0, 1, 0},
                new int[]{0, 0, 1}, false, 4096.0f, -72.0f, 2032.0f, coarse);

        assertEquals(fine[2], coarse[0], 1.0e-6f);
        assertEquals(fine[3], coarse[1], 1.0e-6f);
    }

    @Test
    void adjacentCellsShareMacroWorldUvsWithoutRestartingTexturePerCell() {
        float[] first = new float[6];
        float[] second = new float[6];
        ClipmapTriangleUv.computeWorldAlignedInto(
                new float[]{0, 4, 0}, new float[]{100, 101, 102},
                new float[]{0, 0, 4}, new int[]{0, 1, 0},
                new int[]{0, 0, 1}, false, 8192.0f, -72.0f, 2032.0f, first);
        ClipmapTriangleUv.computeWorldAlignedInto(
                new float[]{4, 8, 4}, new float[]{100, 101, 102},
                new float[]{0, 0, 4}, new int[]{0, 1, 0},
                new int[]{0, 0, 1}, false, 8192.0f, -72.0f, 2032.0f, second);

        // The shared world position (4,0) must keep exactly the same atlas UV.
        assertEquals(first[2], second[0], 1.0e-6f);
        assertEquals(first[3], second[1], 1.0e-6f);

        // A four-block cell samples a tiny fraction of one neutral sprite,
        // rather than restarting the entire 16x16 atlas tile at each cell.
        assertTrue(Math.abs(first[2] - first[0]) < 0.01f);
        assertTrue(Math.abs(first[5] - first[1]) < 0.01f);
    }

    @Test
    void skirtWithFixedXUsesWorldAlignedZAndHeight() {
        float[] uv = new float[6];
        ClipmapTriangleUv.computeWorldAlignedInto(
                new float[]{8, 8, 8}, new float[]{20, 20, 4},
                new float[]{0, 16, 0}, new int[]{4, 4, 4},
                new int[]{0, 1, 0}, true, 64.0f, 0.0f, 32.0f, uv);

        assertNonDegenerate(uv);
        assertInsideSprite(uv);
        assertEquals(8.0f, uv[0], 1.0e-6f);
        assertEquals(9.5f, uv[2], 1.0e-6f);
        assertEquals(9.5f, uv[1], 1.0e-6f);
        assertEquals(3.5f, uv[5], 1.0e-6f);
    }

    @Test
    void skirtWithFixedZUsesWorldAlignedXAndHeight() {
        float[] uv = new float[6];
        ClipmapTriangleUv.computeWorldAlignedInto(
                new float[]{0, 16, 0}, new float[]{20, 20, 4},
                new float[]{8, 8, 8}, new int[]{0, 1, 0},
                new int[]{4, 4, 4}, true, 64.0f, 0.0f, 32.0f, uv);

        assertNonDegenerate(uv);
        assertInsideSprite(uv);
        assertEquals(8.0f, uv[0], 1.0e-6f);
        assertEquals(9.5f, uv[2], 1.0e-6f);
    }

    @Test
    void cellAlignedAtlasUvsTileOneSpriteAcrossBothTrianglesOfEachQuad() {
        float[] first = new float[6];
        float[] second = new float[6];
        ClipmapTriangleUv.computeCellAlignedInto(
                new int[]{12, 12, 13}, new int[]{20, 21, 20}, first);
        ClipmapTriangleUv.computeCellAlignedInto(
                new int[]{13, 12, 13}, new int[]{20, 21, 21}, second);

        assertEquals(0.5f, first[0], 1.0e-6f);
        assertEquals(0.5f, first[1], 1.0e-6f);
        assertEquals(15.5f, first[4], 1.0e-6f);
        assertEquals(0.5f, first[5], 1.0e-6f);
        assertEquals(first[4], second[0], 1.0e-6f);
        assertEquals(first[5], second[1], 1.0e-6f);
        assertEquals(15.5f, second[3], 1.0e-6f);
        assertEquals(15.5f, second[5], 1.0e-6f);
    }

    @Test
    void rejectsInvalidWorldBoundsAndDegenerateProjection() {
        float[] uv = new float[6];
        assertThrows(IllegalArgumentException.class, () ->
                ClipmapTriangleUv.computeWorldAlignedInto(
                        new float[]{0, 0, 0}, new float[]{10, 20, 30},
                        new float[]{0, 0, 0}, new int[]{0, 0, 0},
                        new int[]{0, 0, 0}, false, 0.0f, -72.0f, 2032.0f, uv));
        assertThrows(IllegalArgumentException.class, () ->
                ClipmapTriangleUv.computeWorldAlignedInto(
                        new float[]{0, 0, 0}, new float[]{10, 20, 30},
                        new float[]{0, 0, 0}, new int[]{0, 0, 0},
                        new int[]{0, 0, 0}, false, 64.0f, -72.0f, 2032.0f, uv));
    }

    private static void assertNonDegenerate(float[] uv) {
        float du1 = uv[2] - uv[0];
        float dv1 = uv[3] - uv[1];
        float du2 = uv[4] - uv[0];
        float dv2 = uv[5] - uv[1];
        assertTrue(Math.abs(du1 * dv2 - du2 * dv1) > 1.0e-5f,
                "Iris tangent UV determinant must be non-zero");
    }

    private static void assertInsideSprite(float[] uv) {
        for (float coordinate : uv) {
            assertTrue(coordinate >= 2.0f && coordinate <= 14.0f,
                    "UV must stay inside the sprite to avoid atlas bleeding");
        }
    }
}
