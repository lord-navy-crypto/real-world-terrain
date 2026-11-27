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
    // A one-pixel neutral-white patch keeps the Iris tangent basis non-degenerate
    // without sampling the full noisy PBR sprite on every distant grid cell.
    private static final float NEUTRAL_UV_MIN = 7.5f;
    private static final float NEUTRAL_UV_RANGE = 1.0f;

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
     * Maps both triangles of one grid quad onto the same full sprite tile.
     * The caller emits the two triangles separately, but shared grid corners
     * receive identical UVs. This keeps the atlas texture tiled at the LOD
     * cell scale instead of stretching one white texel across the whole horizon.
     */
    public static void computeCellAlignedInto(int[] gridX, int[] gridZ,
            float[] destination) {
        computeCellAlignedPatchInto(gridX, gridZ, destination, 0.5f, 15.0f);
    }

    /**
     * Maps each surface quad into the same one-pixel patch at the centre of
     * the neutral white-concrete sprite. This preserves a well-conditioned
     * tangent basis for Iris/PBR shaders while avoiding the full atlas detail
     * pattern that aliases when repeated at every coarse LOD cell.
     */
    public static void computeNeutralCellAlignedInto(int[] gridX, int[] gridZ,
            float[] destination) {
        computeCellAlignedPatchInto(
                gridX, gridZ, destination, NEUTRAL_UV_MIN, NEUTRAL_UV_RANGE);
    }

    private static void computeCellAlignedPatchInto(int[] gridX, int[] gridZ,
            float[] destination, float insetMin, float insetRange) {
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
        for (int vertex = 0; vertex < 3; vertex++) {
            destination[vertex * 2] = insetMin + (gridX[vertex] - minX) * insetRange;
            destination[vertex * 2 + 1] = insetMin + (gridZ[vertex] - minZ) * insetRange;
        }
    }
}
