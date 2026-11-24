package org.realscale.neoforge.client.clipmap;

/**
 * Continuous, low-noise material palette for distant surface geometry.
 *
 * <p>Material transitions are evaluated at grid vertices and interpolated by
 * the GPU. This avoids assigning a different flat color to every coarse LOD
 * triangle, which otherwise produces visible triangular color mosaics.
 */
public final class ClipmapSurfacePalette {
    private static final int GRASS = 0x789052;
    private static final int ROCK = 0x85857F;
    private static final int SAND = 0xC1AC7F;
    private static final int SNOW = 0xE1E7EA;
    private static final int WATER = 0x64869A;

    private ClipmapSurfacePalette() {
    }

    public static int colorAt(float height, float normalY, float seaLevel,
            boolean verticalSkirt) {
        if (!Float.isFinite(height) || !Float.isFinite(normalY)
                || !Float.isFinite(seaLevel)) {
            throw new IllegalArgumentException("surface palette inputs must be finite");
        }
        if (verticalSkirt) return ROCK;

        float slope = 1.0f - clamp(normalY, 0.0f, 1.0f);
        float rockBySlope = smoothstep(0.16f, 0.55f, slope);
        float rockByElevation = smoothstep(seaLevel + 197.0f,
                seaLevel + 737.0f, height) * 0.6f;
        float rockCover = Math.max(rockBySlope, rockByElevation);
        float beach = (1.0f - smoothstep(seaLevel + 2.0f,
                seaLevel + 25.0f, height))
                * (1.0f - smoothstep(0.10f, 0.34f, slope)) * 0.85f;
        float snowCover = smoothstep(seaLevel + 677.0f,
                seaLevel + 1157.0f, height);

        float red = channel(GRASS, 16);
        float green = channel(GRASS, 8);
        float blue = channel(GRASS, 0);
        red = mix(red, channel(ROCK, 16), rockCover);
        green = mix(green, channel(ROCK, 8), rockCover);
        blue = mix(blue, channel(ROCK, 0), rockCover);
        red = mix(red, channel(SAND, 16), beach);
        green = mix(green, channel(SAND, 8), beach);
        blue = mix(blue, channel(SAND, 0), beach);
        red = mix(red, channel(SNOW, 16), snowCover);
        green = mix(green, channel(SNOW, 8), snowCover);
        blue = mix(blue, channel(SNOW, 0), snowCover);
        return pack(red, green, blue);
    }

    public static int waterColor() {
        return WATER;
    }

    private static int channel(int rgb, int shift) {
        return (rgb >>> shift) & 0xFF;
    }

    private static int pack(float red, float green, float blue) {
        int r = Math.round(clamp(red, 0.0f, 255.0f));
        int g = Math.round(clamp(green, 0.0f, 255.0f));
        int b = Math.round(clamp(blue, 0.0f, 255.0f));
        return (r << 16) | (g << 8) | b;
    }

    private static float mix(float from, float to, float amount) {
        return from + (to - from) * clamp(amount, 0.0f, 1.0f);
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = clamp((value - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
