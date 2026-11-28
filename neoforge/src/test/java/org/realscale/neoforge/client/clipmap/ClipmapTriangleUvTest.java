package org.realscale.neoforge.client.clipmap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ClipmapTriangleUvTest {
    @Test
    void neutralSingleTexelPatchHasWellConditionedTangentBasis() {
        float[] uv = new float[6];
        ClipmapTriangleUv.computeNeutralCellAlignedInto(
                new int[]{12, 12, 13}, new int[]{20, 21, 20}, uv);

        assertNonDegenerate(uv);
        assertTrue(Math.abs((uv[2] - uv[0]) * (uv[5] - uv[1])
                - (uv[4] - uv[0]) * (uv[3] - uv[1])) >= 0.5f,
                "neutral UV basis must not be numerically tiny");
        assertInsideSprite(uv);
        assertArrayEquals(
                new float[]{7.125f, 7.125f, 7.125f, 7.875f, 7.875f, 7.125f},
                uv, 1.0e-6f);
    }

    @Test
    void adjacentCellsReuseTheSameNeutralTexelPatch() {
        float[] first = new float[6];
        float[] second = new float[6];
        ClipmapTriangleUv.computeNeutralCellAlignedInto(
                new int[]{12, 12, 13}, new int[]{20, 21, 20}, first);
        ClipmapTriangleUv.computeNeutralCellAlignedInto(
                new int[]{13, 13, 14}, new int[]{20, 21, 20}, second);

        assertNonDegenerate(first);
        assertNonDegenerate(second);
        assertArrayEquals(first, second, 1.0e-6f);
        for (float coordinate : first) {
            assertTrue(coordinate >= 7.125f && coordinate <= 7.875f);
        }
    }

    @Test
    void rejectsDegenerateCellAlignedNeutralPatch() {
        float[] uv = new float[6];
        assertThrows(IllegalArgumentException.class, () ->
                ClipmapTriangleUv.computeNeutralCellAlignedInto(
                        new int[]{0, 0, 0}, new int[]{0, 1, 2}, uv));
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
