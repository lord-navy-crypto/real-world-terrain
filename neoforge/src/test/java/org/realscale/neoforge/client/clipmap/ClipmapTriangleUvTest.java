package org.realscale.neoforge.client.clipmap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ClipmapTriangleUvTest {
    @Test
    void topSurfaceUsesNonDegenerateWorldAlignedUvCoordinates() {
        float[] uv = new float[6];
        ClipmapTriangleUv.computeInto(
                new float[]{0, 0, 16}, new float[]{10, 12, 11},
                new float[]{0, 16, 16}, new int[]{0, 0, 1},
                new int[]{0, 1, 1}, false, uv);

        assertNonDegenerate(uv);
        assertInsideSprite(uv);
        assertEquals(2.0f, uv[0], 1.0e-6f);
        assertEquals(2.0f, uv[1], 1.0e-6f);
        assertEquals(14.0f, uv[4], 1.0e-6f);
        assertEquals(14.0f, uv[5], 1.0e-6f);
    }

    @Test
    void skirtWithFixedXUsesZAndHeightForNonDegenerateUvCoordinates() {
        float[] uv = new float[6];
        ClipmapTriangleUv.computeInto(
                new float[]{8, 8, 8}, new float[]{20, 20, 4},
                new float[]{0, 16, 0}, new int[]{4, 4, 4},
                new int[]{0, 1, 0}, true, uv);

        assertNonDegenerate(uv);
        assertInsideSprite(uv);
        assertEquals(2.0f, uv[0], 1.0e-6f);
        assertEquals(14.0f, uv[1], 1.0e-6f);
        assertEquals(14.0f, uv[2], 1.0e-6f);
        assertEquals(2.0f, uv[5], 1.0e-6f);
    }

    @Test
    void skirtWithFixedZUsesXAndHeightForNonDegenerateUvCoordinates() {
        float[] uv = new float[6];
        ClipmapTriangleUv.computeInto(
                new float[]{0, 16, 0}, new float[]{20, 20, 4},
                new float[]{8, 8, 8}, new int[]{0, 1, 0},
                new int[]{4, 4, 4}, true, uv);

        assertNonDegenerate(uv);
        assertInsideSprite(uv);
    }

    @Test
    void rejectsDegenerateProjectionInsteadOfEmittingInvalidTangentFrame() {
        float[] uv = new float[6];
        assertThrows(IllegalArgumentException.class, () ->
                ClipmapTriangleUv.computeInto(
                        new float[]{0, 0, 0}, new float[]{10, 20, 30},
                        new float[]{0, 0, 0}, new int[]{0, 0, 0},
                        new int[]{0, 0, 0}, false, uv));
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
