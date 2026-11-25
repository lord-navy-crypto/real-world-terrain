package org.realscale.neoforge.client.clipmap;

/**
 * Distance-dependent transition from Minecraft-like stepped terrain to the
 * continuous far-field height surface.
 *
 * <p>The first clipmap ring starts in the overlap with vanilla chunks. Its
 * surface is initially quantized into flat, block-height cells with vertical
 * risers. The voxel influence then fades to zero before the first coarse ring.
 * All other LODs remain continuous.
 */
public final class ClipmapVoxelStyle {
    private static final float FADE_LENGTH_CELLS = 48.0f;

    private ClipmapVoxelStyle() {
    }

    /**
     * Returns 1 near the vanilla view boundary and 0 in the far half of LOD 0.
     * Higher LODs are always continuous.
     */
    public static float blend(int level, double distanceBlocks,
            int nearRenderDistanceChunks, int cellSpacingBlocks) {
        if (level < 0 || level >= GeometryClipmapLayout.DEFAULT_LEVEL_COUNT
                || !Double.isFinite(distanceBlocks) || distanceBlocks < 0.0
                || nearRenderDistanceChunks < 2 || nearRenderDistanceChunks > 64
                || cellSpacingBlocks < 1) {
            throw new IllegalArgumentException("invalid voxel transition inputs");
        }
        if (level != 0) return 0.0f;

        double nearRadius = nearRenderDistanceChunks * 16.0;
        double fadeEnd = nearRadius + cellSpacingBlocks * FADE_LENGTH_CELLS;
        return 1.0f - smoothstep((float) nearRadius, (float) fadeEnd,
                (float) distanceBlocks);
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
