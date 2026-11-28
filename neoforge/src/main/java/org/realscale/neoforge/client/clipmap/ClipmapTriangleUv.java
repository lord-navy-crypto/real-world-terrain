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
    // Keep all terrain samples tightly centred on one neutral white-concrete
    // texel. Widening this to most of a texel reintroduced the atlas's adjacent
    // PBR texels and sharply increased both high- and mid-frequency energy.
    private static final float NEUTRAL_UV_CENTER = 7.5f;
    private static final float NEUTRAL_UV_HALF_RANGE = 0.0625f;
    private static final float NEUTRAL_UV_TRIANGLE_SPAN = 0.09375f;

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
     * Maps a single grid triangle into a compact neutral atlas patch while
     * keeping U aligned to world X and V aligned to world Z.
     *
     * <p>Each triangle is centred independently so Iris's per-triangle
     * midTexCoord is always the exact neutral texel centre (7.5, 7.5).
     * A fixed 0.09375 sprite-local span per cell keeps UV deltas large enough
     * to survive atlas-normalized float precision, while the 2:1 vertex
     * distribution keeps every coordinate inside the 1/8-texel patch.
     *
     * <p>The per-triangle UV origins are intentionally allowed to differ at
     * shared vertices. The sampled neutral texel remains the same, while
     * derivatives preserve a consistent +X tangent and +Z bitangent.
     */
    public static void computeNeutralTriangleWorldAlignedInto(float[] x, float[] z,
            int cellSpacingBlocks, float[] destination) {
        Objects.requireNonNull(x, "x");
        Objects.requireNonNull(z, "z");
        Objects.requireNonNull(destination, "destination");
        if (x.length < 3 || z.length < 3 || destination.length < 6
                || cellSpacingBlocks <= 0) {
            throw new IllegalArgumentException(
                    "triangle UV inputs must contain three vertices and positive cell spacing");
        }

        double minX = Math.min(x[0], Math.min(x[1], x[2]));
        double maxX = Math.max(x[0], Math.max(x[1], x[2]));
        double minZ = Math.min(z[0], Math.min(z[1], z[2]));
        double maxZ = Math.max(z[0], Math.max(z[1], z[2]));
        if (Math.abs((maxX - minX) - cellSpacingBlocks) > 1.0e-3d
                || Math.abs((maxZ - minZ) - cellSpacingBlocks) > 1.0e-3d) {
            throw new IllegalArgumentException(
                    "surface triangle must span exactly one clipmap cell in X and Z");
        }

        double centreX = (x[0] + (double) x[1] + x[2]) / 3.0d;
        double centreZ = (z[0] + (double) z[1] + z[2]) / 3.0d;
        double scale = NEUTRAL_UV_TRIANGLE_SPAN / cellSpacingBlocks;
        for (int vertex = 0; vertex < 3; vertex++) {
            if (!Float.isFinite(x[vertex]) || !Float.isFinite(z[vertex])) {
                throw new IllegalArgumentException("triangle UV positions must be finite");
            }
            destination[vertex * 2] =
                    (float) (NEUTRAL_UV_CENTER + (x[vertex] - centreX) * scale);
            destination[vertex * 2 + 1] =
                    (float) (NEUTRAL_UV_CENTER + (z[vertex] - centreZ) * scale);
            if (Math.abs(destination[vertex * 2] - NEUTRAL_UV_CENTER)
                        > NEUTRAL_UV_HALF_RANGE + 1.0e-6f
                    || Math.abs(destination[vertex * 2 + 1] - NEUTRAL_UV_CENTER)
                        > NEUTRAL_UV_HALF_RANGE + 1.0e-6f) {
                throw new IllegalArgumentException(
                        "triangle UV position exceeds the neutral sub-texel patch");
            }
        }

        double du1 = destination[2] - destination[0];
        double dv1 = destination[3] - destination[1];
        double du2 = destination[4] - destination[0];
        double dv2 = destination[5] - destination[1];
        double determinant = du1 * dv2 - du2 * dv1;
        if (!Double.isFinite(determinant) || Math.abs(determinant) <= 1.0e-6d) {
            throw new IllegalArgumentException(
                    "world-aligned neutral UV triangle is degenerate");
        }

        double midU = (destination[0] + destination[2] + destination[4]) / 3.0d;
        double midV = (destination[1] + destination[3] + destination[5]) / 3.0d;
        if (Math.abs(midU - NEUTRAL_UV_CENTER) > 1.0e-6d
                || Math.abs(midV - NEUTRAL_UV_CENTER) > 1.0e-6d) {
            throw new IllegalArgumentException(
                    "triangle midTexCoord must remain at the neutral texel centre");
        }
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
