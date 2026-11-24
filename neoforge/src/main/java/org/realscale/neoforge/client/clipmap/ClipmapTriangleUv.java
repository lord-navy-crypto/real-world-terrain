package org.realscale.neoforge.client.clipmap;

import java.util.Objects;

/**
 * Creates a non-degenerate, atlas-safe UV basis for a clipmap triangle.
 *
 * <p>Top surfaces map world X/Z to U/V. Vertical seam skirts map their
 * horizontal edge axis and world Y to U/V. Coordinates stay inside a uniform
 * sprite's interior so texture filtering cannot bleed from neighbouring atlas
 * entries, while Iris can still derive a finite tangent frame for PBR shaders.
 */
public final class ClipmapTriangleUv {
    private static final float UV_MIN = 2.0f;
    private static final float UV_MAX = 14.0f;
    private static final float UV_RANGE = UV_MAX - UV_MIN;
    private static final float MIN_SPAN = 1.0e-6f;

    private ClipmapTriangleUv() {
    }

    /**
     * Writes six sprite-local UV coordinates as U0,V0,U1,V1,U2,V2.
     * The destination array is reusable to avoid per-triangle allocations.
     */
    public static void computeInto(float[] x, float[] y, float[] z,
            int[] gridX, int[] gridZ, boolean verticalSkirt, float[] destination) {
        Objects.requireNonNull(x, "x");
        Objects.requireNonNull(y, "y");
        Objects.requireNonNull(z, "z");
        Objects.requireNonNull(gridX, "gridX");
        Objects.requireNonNull(gridZ, "gridZ");
        Objects.requireNonNull(destination, "destination");
        if (x.length < 3 || y.length < 3 || z.length < 3
                || gridX.length < 3 || gridZ.length < 3 || destination.length < 6) {
            throw new IllegalArgumentException("triangle UV arrays must contain at least three vertices");
        }

        float minUAxis = Float.POSITIVE_INFINITY;
        float maxUAxis = Float.NEGATIVE_INFINITY;
        float minVAxis = Float.POSITIVE_INFINITY;
        float maxVAxis = Float.NEGATIVE_INFINITY;
        boolean useGridZForSkirt = verticalSkirt
                && gridX[0] == gridX[1] && gridX[1] == gridX[2];

        for (int vertex = 0; vertex < 3; vertex++) {
            float uAxis = verticalSkirt
                    ? (useGridZForSkirt ? z[vertex] : x[vertex])
                    : x[vertex];
            float vAxis = verticalSkirt ? y[vertex] : z[vertex];
            if (!Float.isFinite(uAxis) || !Float.isFinite(vAxis)) {
                throw new IllegalArgumentException("triangle UV input must be finite");
            }
            minUAxis = Math.min(minUAxis, uAxis);
            maxUAxis = Math.max(maxUAxis, uAxis);
            minVAxis = Math.min(minVAxis, vAxis);
            maxVAxis = Math.max(maxVAxis, vAxis);
        }

        float uSpan = maxUAxis - minUAxis;
        float vSpan = maxVAxis - minVAxis;
        if (uSpan <= MIN_SPAN || vSpan <= MIN_SPAN) {
            throw new IllegalArgumentException("triangle UV projection is degenerate");
        }

        for (int vertex = 0; vertex < 3; vertex++) {
            float uAxis = verticalSkirt
                    ? (useGridZForSkirt ? z[vertex] : x[vertex])
                    : x[vertex];
            float vAxis = verticalSkirt ? y[vertex] : z[vertex];
            destination[vertex * 2] = UV_MIN + (uAxis - minUAxis) / uSpan * UV_RANGE;
            destination[vertex * 2 + 1] = UV_MIN + (vAxis - minVAxis) / vSpan * UV_RANGE;
        }
    }

    /**
     * Maps a triangle into one shared world-aligned UV domain for the complete
     * clipmap. Unlike per-triangle normalization, adjacent triangles and LOD
     * rings now sample the same atlas coordinates at the same world position.
     *
     * <p>The uniform sprite patch is intentionally spread over the full
     * horizon. This keeps Iris/PBR tangent frames valid while preventing the
     * same tiny material-normal pattern from restarting on every triangle.
     */
    public static void computeWorldAlignedInto(float[] x, float[] y, float[] z,
            int[] gridX, int[] gridZ, boolean verticalSkirt,
            float worldHalfExtent, float minWorldY, float maxWorldY,
            float[] destination) {
        Objects.requireNonNull(x, "x");
        Objects.requireNonNull(y, "y");
        Objects.requireNonNull(z, "z");
        Objects.requireNonNull(gridX, "gridX");
        Objects.requireNonNull(gridZ, "gridZ");
        Objects.requireNonNull(destination, "destination");
        if (x.length < 3 || y.length < 3 || z.length < 3
                || gridX.length < 3 || gridZ.length < 3 || destination.length < 6) {
            throw new IllegalArgumentException("triangle UV arrays must contain at least three vertices");
        }
        if (!Float.isFinite(worldHalfExtent) || worldHalfExtent <= 0.0f
                || !Float.isFinite(minWorldY) || !Float.isFinite(maxWorldY)
                || maxWorldY <= minWorldY) {
            throw new IllegalArgumentException("world-aligned UV bounds must be finite and non-degenerate");
        }

        boolean useGridZForSkirt = verticalSkirt
                && gridX[0] == gridX[1] && gridX[1] == gridX[2];
        for (int vertex = 0; vertex < 3; vertex++) {
            if (!Float.isFinite(x[vertex]) || !Float.isFinite(y[vertex])
                    || !Float.isFinite(z[vertex])) {
                throw new IllegalArgumentException("triangle UV input must be finite");
            }
            float horizontal = verticalSkirt
                    ? (useGridZForSkirt ? z[vertex] : x[vertex])
                    : x[vertex];
            float vertical = verticalSkirt ? y[vertex] : z[vertex];
            destination[vertex * 2] = mapToSprite(
                    horizontal, -worldHalfExtent, worldHalfExtent);
            destination[vertex * 2 + 1] = verticalSkirt
                    ? mapToSprite(vertical, minWorldY, maxWorldY)
                    : mapToSprite(vertical, -worldHalfExtent, worldHalfExtent);
        }

        float du1 = destination[2] - destination[0];
        float dv1 = destination[3] - destination[1];
        float du2 = destination[4] - destination[0];
        float dv2 = destination[5] - destination[1];
        if (Math.abs(du1 * dv2 - du2 * dv1) <= MIN_SPAN) {
            throw new IllegalArgumentException("world-aligned triangle UV projection is degenerate");
        }
    }

    /**
     * Maps both triangles of one grid quad onto the same full sprite tile.
     * The caller emits the two triangles separately, but shared grid corners
     * receive identical UVs. This keeps the atlas texture tiled at the LOD
     * cell scale instead of stretching one white texel across the whole horizon.
     */
    public static void computeCellAlignedInto(int[] gridX, int[] gridZ,
            float[] destination) {
        Objects.requireNonNull(gridX, "gridX");
        Objects.requireNonNull(gridZ, "gridZ");
        Objects.requireNonNull(destination, "destination");
        if (gridX.length < 3 || gridZ.length < 3 || destination.length < 6) {
            throw new IllegalArgumentException("triangle grid and UV arrays must contain three vertices");
        }
        int minX = Math.min(gridX[0], Math.min(gridX[1], gridX[2]));
        int minZ = Math.min(gridZ[0], Math.min(gridZ[1], gridZ[2]));
        int maxX = Math.max(gridX[0], Math.max(gridX[1], gridX[2]));
        int maxZ = Math.max(gridZ[0], Math.max(gridZ[1], gridZ[2]));
        int spanX = maxX - minX;
        int spanZ = maxZ - minZ;
        if (spanX != 1 || spanZ != 1) {
            throw new IllegalArgumentException("surface triangle must belong to one grid quad");
        }
        final float insetMin = 0.5f;
        final float insetRange = 15.0f;
        for (int vertex = 0; vertex < 3; vertex++) {
            destination[vertex * 2] = insetMin + (gridX[vertex] - minX) * insetRange;
            destination[vertex * 2 + 1] = insetMin + (gridZ[vertex] - minZ) * insetRange;
        }
    }

    private static float mapToSprite(float value, float minimum, float maximum) {
        float normalized = Math.max(0.0f, Math.min(1.0f,
                (value - minimum) / (maximum - minimum)));
        return UV_MIN + normalized * UV_RANGE;
    }

}
