package org.realscale.neoforge.client.clipmap;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ClipmapProjectionMathTest {
    private static Matrix4f vanillaProjection() {
        return new Matrix4f().perspective(
                (float) Math.toRadians(70.0), 16.0f / 9.0f, 0.05f, 640.0f);
    }

    @Test
    void readsVanillaTenChunkProjectionFarPlane() {
        Matrix4f projection = vanillaProjection();
        assertEquals(0.05f, ClipmapProjectionMath.nearPlaneBlocks(projection), 1.0e-4f);
        assertEquals(640.0f, ClipmapProjectionMath.farPlaneBlocks(projection), 0.1f);
    }

    @Test
    void extendsFarPlaneWhilePreservingFovAspectAndNearPlane() {
        Matrix4f projection = vanillaProjection();
        float m00 = projection.m00();
        float m11 = projection.m11();
        float m23 = projection.m23();
        float m33 = projection.m33();

        float actual = ClipmapProjectionMath.extendFarPlane(projection, 24_084.0f);

        assertEquals(24_084.0f, actual, 200.0f);
        assertTrue(actual >= 23_170.0f,
                "The far plane must include the outer clipmap corners before fog fade");
        assertEquals(0.05f, ClipmapProjectionMath.nearPlaneBlocks(projection), 1.0e-4f);
        assertEquals(m00, projection.m00(), 0.0f);
        assertEquals(m11, projection.m11(), 0.0f);
        assertEquals(m23, projection.m23(), 0.0f);
        assertEquals(m33, projection.m33(), 0.0f);
    }

    @Test
    void neverShortensAnAlreadyLongProjection() {
        Matrix4f projection = new Matrix4f().perspective(
                (float) Math.toRadians(70.0), 16.0f / 9.0f, 0.05f, 30_000.0f);
        float before = ClipmapProjectionMath.farPlaneBlocks(projection);
        float after = ClipmapProjectionMath.extendFarPlane(projection, 24_084.0f);
        assertEquals(before, after, 0.01f);
        assertEquals(30_000.0f, after, 100.0f);
    }

    @Test
    void rejectsNonPerspectiveOrInvalidRequests() {
        assertThrows(IllegalArgumentException.class,
                () -> ClipmapProjectionMath.extendFarPlane(new Matrix4f(), 24_084.0f));
        assertThrows(IllegalArgumentException.class,
                () -> ClipmapProjectionMath.extendFarPlane(vanillaProjection(), Float.NaN));
    }
}
