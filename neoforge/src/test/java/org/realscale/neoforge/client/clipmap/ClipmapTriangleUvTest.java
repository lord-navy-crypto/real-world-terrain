package org.realscale.neoforge.client.clipmap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ClipmapTriangleUvTest {
    @Test
    void worldAlignedNeutralPatchStaysInsideOneTexelAndIsNonDegenerate() {
        float[] uv = new float[6];
        ClipmapTriangleUv.computeNeutralTriangleWorldAlignedInto(
                new float[]{-128.0f, -128.0f, -124.0f},
                new float[]{-128.0f, -124.0f, -128.0f},
                4, uv);

        assertInsideNeutralPatch(uv);
        assertNonDegenerate(uv);
        assertEquals(7.46875f, uv[0], 1.0e-6f);
        assertEquals(7.5625f, uv[4], 1.0e-6f);
        assertEquals(7.5f, midU(uv), 1.0e-6f);
        assertEquals(7.5f, midV(uv), 1.0e-6f);
    }

    @Test
    void adjacentTrianglesKeepTheSameMidTexelAndWorldAlignedTangentFrame() {
        float[] first = new float[6];
        float[] second = new float[6];
        float[] firstX = {-128.0f, -128.0f, -124.0f};
        float[] firstZ = {-128.0f, -124.0f, -128.0f};
        float[] secondX = {-124.0f, -128.0f, -124.0f};
        float[] secondZ = {-128.0f, -124.0f, -124.0f};

        ClipmapTriangleUv.computeNeutralTriangleWorldAlignedInto(
                firstX, firstZ, 4, first);
        ClipmapTriangleUv.computeNeutralTriangleWorldAlignedInto(
                secondX, secondZ, 4, second);

        assertEquals(7.5f, midU(first), 1.0e-6f);
        assertEquals(7.5f, midV(first), 1.0e-6f);
        assertEquals(7.5f, midU(second), 1.0e-6f);
        assertEquals(7.5f, midV(second), 1.0e-6f);

        // Mirrors Iris NormalHelper.computeTangentSmooth on a flat X/Z plane.
        assertArrayEquals(new float[]{1.0f, 0.0f},
                tangentDirection(firstX, firstZ, first), 1.0e-5f);
        assertArrayEquals(new float[]{1.0f, 0.0f},
                tangentDirection(secondX, secondZ, second), 1.0e-5f);
        assertArrayEquals(new float[]{0.0f, 1.0f},
                bitangentDirection(firstX, firstZ, first), 1.0e-5f);
        assertArrayEquals(new float[]{0.0f, 1.0f},
                bitangentDirection(secondX, secondZ, second), 1.0e-5f);

        assertInsideNeutralPatch(first);
        assertInsideNeutralPatch(second);
    }

    @Test
    void localUvSpanIsStableAcrossAllSupportedLodCellSpacings() {
        float[] fine = new float[6];
        float[] coarse = new float[6];
        ClipmapTriangleUv.computeNeutralTriangleWorldAlignedInto(
                new float[]{0.0f, 0.0f, 1.0f},
                new float[]{0.0f, 1.0f, 0.0f}, 1, fine);
        ClipmapTriangleUv.computeNeutralTriangleWorldAlignedInto(
                new float[]{0.0f, 0.0f, 512.0f},
                new float[]{0.0f, 512.0f, 0.0f}, 512, coarse);

        assertArrayEquals(fine, coarse, 1.0e-6f);
        assertInsideNeutralPatch(fine);
    }

    @Test
    void maximumSupportedCellSpacingRemainsInsideTheNeutralTexel() {
        float[] uv = new float[6];
        ClipmapTriangleUv.computeNeutralTriangleWorldAlignedInto(
                new float[]{-4096.0f, -4096.0f, 0.0f},
                new float[]{-4096.0f, 0.0f, -4096.0f},
                4096, uv);

        assertInsideNeutralPatch(uv);
        assertNonDegenerate(uv);
        assertEquals(7.5f, midU(uv), 1.0e-6f);
        assertEquals(7.5f, midV(uv), 1.0e-6f);
    }

    @Test
    void rejectsNonFinitePositionsOrInvalidCellGeometry() {
        float[] uv = new float[6];
        assertThrows(IllegalArgumentException.class, () ->
                ClipmapTriangleUv.computeNeutralTriangleWorldAlignedInto(
                        new float[]{0.0f, Float.NaN, 1.0f},
                        new float[]{0.0f, 1.0f, 0.0f}, 1, uv));
        assertThrows(IllegalArgumentException.class, () ->
                ClipmapTriangleUv.computeNeutralTriangleWorldAlignedInto(
                        new float[]{0.0f, 0.0f, 1.0f},
                        new float[]{0.0f, 1.0f, 0.0f}, 0, uv));
        assertThrows(IllegalArgumentException.class, () ->
                ClipmapTriangleUv.computeNeutralTriangleWorldAlignedInto(
                        new float[]{0.0f, 0.0f, 2.0f},
                        new float[]{0.0f, 1.0f, 0.0f}, 1, uv));
    }

    @Test
    void rejectsDegenerateWorldAlignedTriangle() {
        float[] uv = new float[6];
        assertThrows(IllegalArgumentException.class, () ->
                ClipmapTriangleUv.computeNeutralTriangleWorldAlignedInto(
                        new float[]{0.0f, 1.0f, 2.0f},
                        new float[]{0.0f, 1.0f, 2.0f}, 2, uv));
    }

    private static float midU(float[] uv) {
        return (uv[0] + uv[2] + uv[4]) / 3.0f;
    }

    private static float midV(float[] uv) {
        return (uv[1] + uv[3] + uv[5]) / 3.0f;
    }

    private static float[] tangentDirection(float[] x, float[] z, float[] uv) {
        double edge1X = x[1] - x[0];
        double edge1Z = z[1] - z[0];
        double edge2X = x[2] - x[0];
        double edge2Z = z[2] - z[0];
        double du1 = uv[2] - uv[0];
        double dv1 = uv[3] - uv[1];
        double du2 = uv[4] - uv[0];
        double dv2 = uv[5] - uv[1];
        double inverseDeterminant = 1.0 / (du1 * dv2 - du2 * dv1);
        double tangentX = inverseDeterminant * (dv2 * edge1X - dv1 * edge2X);
        double tangentZ = inverseDeterminant * (dv2 * edge1Z - dv1 * edge2Z);
        double inverseLength = 1.0 / Math.sqrt(tangentX * tangentX + tangentZ * tangentZ);
        return new float[]{(float) (tangentX * inverseLength), (float) (tangentZ * inverseLength)};
    }

    private static float[] bitangentDirection(float[] x, float[] z, float[] uv) {
        double edge1X = x[1] - x[0];
        double edge1Z = z[1] - z[0];
        double edge2X = x[2] - x[0];
        double edge2Z = z[2] - z[0];
        double du1 = uv[2] - uv[0];
        double dv1 = uv[3] - uv[1];
        double du2 = uv[4] - uv[0];
        double dv2 = uv[5] - uv[1];
        double inverseDeterminant = 1.0 / (du1 * dv2 - du2 * dv1);
        double bitangentX = inverseDeterminant * (-du2 * edge1X + du1 * edge2X);
        double bitangentZ = inverseDeterminant * (-du2 * edge1Z + du1 * edge2Z);
        double inverseLength = 1.0 / Math.sqrt(bitangentX * bitangentX + bitangentZ * bitangentZ);
        return new float[]{(float) (bitangentX * inverseLength), (float) (bitangentZ * inverseLength)};
    }

    private static float uvArea(float[] uv) {
        return (uv[2] - uv[0]) * (uv[5] - uv[1])
                - (uv[3] - uv[1]) * (uv[4] - uv[0]);
    }

    private static void assertNonDegenerate(float[] uv) {
        assertTrue(Math.abs(uvArea(uv)) > 1.0e-6f,
                "Iris tangent UV determinant must be non-zero");
    }

    private static void assertInsideNeutralPatch(float[] uv) {
        for (float coordinate : uv) {
            assertTrue(coordinate >= 7.4375f && coordinate <= 7.5625f,
                    "UV must remain within the single neutral texel patch");
        }
    }
}
