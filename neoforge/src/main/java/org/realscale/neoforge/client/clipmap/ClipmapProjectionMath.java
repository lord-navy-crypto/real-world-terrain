package org.realscale.neoforge.client.clipmap;

import org.joml.Matrix4f;

/**
 * Extends a conventional OpenGL perspective matrix without changing its
 * field of view, aspect ratio, or near clipping plane.
 *
 * <p>Minecraft's normal camera projection is sized for vanilla chunk distance.
 * A distant terrain renderer must explicitly enlarge its far plane or its
 * outer LOD rings are clipped before rasterization.
 */
public final class ClipmapProjectionMath {
    private ClipmapProjectionMath() {}

    /** Returns the far-plane distance encoded by a conventional perspective matrix. */
    public static float farPlaneBlocks(Matrix4f projection) {
        float m22 = projection.m22();
        float m32 = projection.m32();
        float denominator = m22 + 1.0f;
        if (!Float.isFinite(m22) || !Float.isFinite(m32)
                || Math.abs(denominator) < 1.0e-8f) {
            throw new IllegalArgumentException("projection has no finite perspective far plane");
        }
        float far = m32 / denominator;
        if (!Float.isFinite(far) || far <= 0.0f) {
            throw new IllegalArgumentException("projection has an invalid perspective far plane");
        }
        return far;
    }

    /** Returns the near-plane distance encoded by a conventional perspective matrix. */
    public static float nearPlaneBlocks(Matrix4f projection) {
        float m22 = projection.m22();
        float m32 = projection.m32();
        float denominator = m22 - 1.0f;
        if (!Float.isFinite(m22) || !Float.isFinite(m32)
                || Math.abs(denominator) < 1.0e-8f) {
            throw new IllegalArgumentException("projection has no finite perspective near plane");
        }
        float near = m32 / denominator;
        if (!Float.isFinite(near) || near <= 0.0f) {
            throw new IllegalArgumentException("projection has an invalid perspective near plane");
        }
        return near;
    }

    /**
     * Mutates only m22 and m32 of a conventional OpenGL perspective matrix.
     * Returns the actual far plane, which is never shorter than the input.
     */
    public static float extendFarPlane(Matrix4f projection, float requestedFarBlocks) {
        if (projection == null || !Float.isFinite(requestedFarBlocks)
                || requestedFarBlocks <= 0.0f) {
            throw new IllegalArgumentException("projection and positive finite far plane are required");
        }
        float near = nearPlaneBlocks(projection);
        float currentFar = farPlaneBlocks(projection);
        if (requestedFarBlocks <= currentFar) return currentFar;
        if (requestedFarBlocks <= near) {
            throw new IllegalArgumentException("far plane must exceed the near plane");
        }
        float denominator = requestedFarBlocks - near;
        projection.m22(-(requestedFarBlocks + near) / denominator);
        projection.m32(-(2.0f * requestedFarBlocks * near) / denominator);
        return farPlaneBlocks(projection);
    }
}
