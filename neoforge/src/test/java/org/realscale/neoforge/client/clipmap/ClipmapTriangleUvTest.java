package org.realscale.neoforge.client.clipmap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ClipmapTriangleUvTest {
    @Test
    void worldAlignedNeutralPatchStaysInsideOneTexelAndIsNonDegenerate() {
        float[] uv = new float[6];
        ClipmapTriangleUv.computeNeutralWorldAlignedInto(
                new float[]{-128.0f, -128.0f, -124.0f},
                new float[]{-128.0f, -124.0f, -128.0f},
                16_384L, uv);

        assertInsideNeutralPatch(uv);
        assertNonDegenerate(uv);
        assertEquals(7.5f - 128.0f * (0.0625f / 16_384.0f), uv[0], 1.0e-7f);
        assertEquals(7.5f - 124.0f * (0.0625f / 16_384.0f), uv[4], 1.0e-7f);
    }

    @Test
    void adjacentTrianglesShareWorldPositionUvsAndTangentOrientation() {
        float[] first = new float[6];
        float[] second = new float[6];
        float[] firstX = {-128.0f, -128.0f, -124.0f};
        float[] firstZ = {-128.0f, -124.0f, -128.0f};
        float[] secondX = {-124.0f, -128.0f, -124.0f};
        float[] secondZ = {-128.0f, -124.0f, -124.0f};

        ClipmapTriangleUv.computeNeutralWorldAlignedInto(
                firstX, firstZ, 16_384L, first);
        ClipmapTriangleUv.computeNeutralWorldAlignedInto(
                secondX, secondZ, 16_384L, second);

        // The diagonal's two shared world positions must not jump in UV space.
        assertEquals(first[4], second[0], 1.0e-7f);
        assertEquals(first[5], second[1], 1.0e-7f);
        assertEquals(first[2], second[2], 1.0e-7f);
        assertEquals(first[3], second[3], 1.0e-7f);

        // A positive world X/Z scale preserves the signed triangle orientation.
        assertEquals(Math.signum(worldArea(firstX, firstZ)),
                Math.signum(uvArea(first)), 0.0f);
        assertEquals(Math.signum(worldArea(secondX, secondZ)),
                Math.signum(uvArea(second)), 0.0f);

        // Mirrors Iris NormalHelper.computeTangentSmooth on a flat X/Z plane.
        // Both halves of the quad must resolve to the same +X tangent and +Z bitangent.
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
    void identicalWorldPositionsKeepIdenticalUvsAcrossLodMeshes() {
        float[] fine = new float[6];
        float[] coarse = new float[6];
        float[] x = {2048.0f, 2048.0f, 2064.0f};
        float[] z = {-1024.0f, -1008.0f, -1024.0f};

        // The caller supplies the shared outer extent, not each ring's local extent.
        ClipmapTriangleUv.computeNeutralWorldAlignedInto(x, z, 65_536L, fine);
        ClipmapTriangleUv.computeNeutralWorldAlignedInto(x, z, 65_536L, coarse);
        assertArrayEquals(fine, coarse, 0.0f);
        assertInsideNeutralPatch(fine);
    }

    @Test
    void maximumSupportedClipmapExtentRemainsInsideTheNeutralTexel() {
        float[] uv = new float[6];
        ClipmapTriangleUv.computeNeutralWorldAlignedInto(
                new float[]{-65_536.0f, -65_536.0f, -65_520.0f},
                new float[]{-65_536.0f, -65_520.0f, -65_536.0f},
                65_536L, uv);

        assertInsideNeutralPatch(uv);
        assertNonDegenerate(uv);
        assertEquals(7.4375f, uv[0], 1.0e-6f);
    }

    @Test
    void rejectsNonFiniteOrOutOfExtentWorldPositions() {
        float[] uv = new float[6];
        assertThrows(IllegalArgumentException.class, () ->
                ClipmapTriangleUv.computeNeutralWorldAlignedInto(
                        new float[]{0.0f, Float.NaN, 1.0f},
                        new float[]{0.0f, 1.0f, 0.0f}, 16_384L, uv));
        assertThrows(IllegalArgumentException.class, () ->
                ClipmapTriangleUv.computeNeutralWorldAlignedInto(
                        new float[]{0.0f, 16_385.0f, 1.0f},
                        new float[]{0.0f, 1.0f, 0.0f}, 16_384L, uv));
    }

    @Test
    void rejectsDegenerateWorldAlignedTriangle() {
        float[] uv = new float[6];
        assertThrows(IllegalArgumentException.class, () ->
                ClipmapTriangleUv.computeNeutralWorldAlignedInto(
                        new float[]{0.0f, 1.0f, 2.0f},
                        new float[]{0.0f, 1.0f, 2.0f}, 16_384L, uv));
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

    private static float worldArea(float[] x, float[] z) {
        return (x[1] - x[0]) * (z[2] - z[0])
                - (z[1] - z[0]) * (x[2] - x[0]);
    }

    private static void assertNonDegenerate(float[] uv) {
        assertTrue(Math.abs(uvArea(uv)) > 1.0e-12f,
                "Iris tangent UV determinant must be non-zero");
    }

    private static void assertInsideNeutralPatch(float[] uv) {
        for (float coordinate : uv) {
            assertTrue(coordinate >= 7.4375f && coordinate <= 7.5625f,
                    "UV must remain within the single neutral texel patch");
        }
    }
}
