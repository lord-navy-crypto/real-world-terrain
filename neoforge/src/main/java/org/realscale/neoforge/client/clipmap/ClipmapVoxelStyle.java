package org.realscale.neoforge.client.clipmap;

/**
 * Distance-dependent transition from Minecraft-like stepped terrain to the
 * continuous far-field height surface.
 *
 * <p>The first visible ring beyond the protected vanilla overlap is quantized
 * into flat, block-height cells with vertical risers. The voxel influence fades
 * to zero before that ring's outer edge; all other LODs remain continuous.
 */
public final class ClipmapVoxelStyle {
    private static final float FADE_LENGTH_CELLS = 40.0f;
    private static final int MAX_VOXEL_CELL_SPACING_BLOCKS = 8;

    private ClipmapVoxelStyle() {
    }

    /**
     * Selects the first ring whose outer edge reaches beyond the vanilla
     * render radius plus the shared-centre snap allowance.
     */
    public static int transitionLevel(int gridCells, int baseCellSpacingBlocks,
            int nearRenderDistanceChunks) {
        if (gridCells < 16 || gridCells > 256 || (gridCells & (gridCells - 1)) != 0
                || baseCellSpacingBlocks < 1
                || nearRenderDistanceChunks < 2 || nearRenderDistanceChunks > 64) {
            throw new IllegalArgumentException("invalid voxel transition layout");
        }
        double nearRadius = nearRenderDistanceChunks * 16.0;
        double cameraOffsetAllowance = baseCellSpacingBlocks * 16.0;
        double protectedStart = nearRadius + cameraOffsetAllowance
                + baseCellSpacingBlocks * 2.0;
        for (int level = 0; level < GeometryClipmapLayout.DEFAULT_LEVEL_COUNT; level++) {
            long spacing = (long) baseCellSpacingBlocks << level;
            // Do not turn already-distant, coarse rings into giant voxels.
            // The vanilla chunk renderer owns exact blocks; the custom stepped
            // band is limited to reasonably fine near-field samples.
            if (spacing > MAX_VOXEL_CELL_SPACING_BLOCKS) break;
            double outerRadius = (gridCells * 0.5) * spacing;
            if (outerRadius > protectedStart) return level;
        }
        return -1;
    }

    /**
     * Returns 0 inside the protected vanilla overlap, rises to a stepped
     * surface immediately beyond it, then fades to 0 at the selected ring edge.
     */
    public static float blend(int level, double distanceBlocks,
            int nearRenderDistanceChunks, int baseCellSpacingBlocks,
            int cellSpacingBlocks, int gridCells) {
        if (level < 0 || level >= GeometryClipmapLayout.DEFAULT_LEVEL_COUNT
                || !Double.isFinite(distanceBlocks) || distanceBlocks < 0.0
                || nearRenderDistanceChunks < 2 || nearRenderDistanceChunks > 64
                || baseCellSpacingBlocks < 1 || cellSpacingBlocks < baseCellSpacingBlocks
                || gridCells < 16 || gridCells > 256) {
            throw new IllegalArgumentException("invalid voxel transition inputs");
        }
        int transitionLevel = transitionLevel(
                gridCells, baseCellSpacingBlocks, nearRenderDistanceChunks);
        if (level != transitionLevel) return 0.0f;

        double nearRadius = nearRenderDistanceChunks * 16.0;
        double cameraOffsetAllowance = baseCellSpacingBlocks * 16.0;
        double fadeStart = nearRadius + cameraOffsetAllowance
                + cellSpacingBlocks * 2.0;
        double outerRadius = (gridCells * 0.5) * cellSpacingBlocks;
        double fadeEnd = Math.min(
                fadeStart + cellSpacingBlocks * FADE_LENGTH_CELLS, outerRadius);
        if (fadeEnd <= fadeStart) return 0.0f;
        double available = fadeEnd - fadeStart;
        double fadeInEnd = fadeStart + Math.min(
                cellSpacingBlocks * 2.0, available * 0.25);
        float fadeIn = smoothstep((float) fadeStart, (float) fadeInEnd,
                (float) distanceBlocks);
        float fadeOut = 1.0f - smoothstep((float) fadeInEnd,
                (float) fadeEnd, (float) distanceBlocks);
        return fadeIn * fadeOut;
    }

    /** Snap a sampled surface height to the nearest Minecraft block level. */
    public static float quantizeHeight(float height) {
        if (!Float.isFinite(height)) {
            throw new IllegalArgumentException("voxel height must be finite");
        }
        return (float) Math.floor(height + 0.5f);
    }

    /** Blend a continuous height toward a flat voxel-cell height. */
    public static float blendHeight(float continuous, float voxel, float amount) {
        if (!Float.isFinite(continuous) || !Float.isFinite(voxel)
                || !Float.isFinite(amount)) {
            throw new IllegalArgumentException("voxel height blend inputs must be finite");
        }
        float t = clamp(amount, 0.0f, 1.0f);
        return continuous + (voxel - continuous) * t;
    }

    /** Blend packed RGB colors without allowing alpha or channel overflow. */
    public static int blendColor(int continuousRgb, int voxelRgb, float amount) {
        if (!Float.isFinite(amount)) {
            throw new IllegalArgumentException("voxel color blend must be finite");
        }
        float t = clamp(amount, 0.0f, 1.0f);
        int red = Math.round(channel(continuousRgb, 16)
                + (channel(voxelRgb, 16) - channel(continuousRgb, 16)) * t);
        int green = Math.round(channel(continuousRgb, 8)
                + (channel(voxelRgb, 8) - channel(continuousRgb, 8)) * t);
        int blue = Math.round(channel(continuousRgb, 0)
                + (channel(voxelRgb, 0) - channel(continuousRgb, 0)) * t);
        return (red << 16) | (green << 8) | blue;
    }

    public static float smoothstep(float edge0, float edge1, float value) {
        if (!Float.isFinite(edge0) || !Float.isFinite(edge1)
                || !Float.isFinite(value) || edge1 <= edge0) {
            throw new IllegalArgumentException("invalid smoothstep inputs");
        }
        float t = clamp((value - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private static int channel(int rgb, int shift) {
        return (rgb >>> shift) & 0xFF;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
