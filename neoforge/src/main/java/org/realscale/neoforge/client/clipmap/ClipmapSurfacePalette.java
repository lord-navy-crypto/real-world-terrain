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

    public static final int MATERIAL_GRASS = 0;
    public static final int MATERIAL_ROCK = 1;
    public static final int MATERIAL_SAND = 2;
    public static final int MATERIAL_SNOW = 3;

    /**
     * World-anchored texture tint for the Iris terrain atlas path. The legacy
     * palette above remains useful to CPU tests; this overload restores the
     * moisture/regional variation used by the procedural renderer without
     * restarting noise at each LOD cell.
     */
    public static int texturedTintAt(float height, float normalY, float seaLevel,
            boolean verticalSkirt, long worldX, long worldZ) {
        if (!Float.isFinite(height) || !Float.isFinite(normalY)
                || !Float.isFinite(seaLevel)) {
            throw new IllegalArgumentException("surface palette inputs must be finite");
        }
        if (verticalSkirt) return 0xB8B5AE;

        float macro = valueNoise(worldX, worldZ, 2048);
        float regional = valueNoise(worldX, worldZ, 512);
        float local = valueNoise(worldX, worldZ, 256);
        float moisture = clamp(0.72f * macro + 0.28f * regional, 0.0f, 1.0f);
        float slope = 1.0f - clamp(normalY, 0.0f, 1.0f);
        float rockCover = Math.max(
                smoothstep(0.16f, 0.55f, slope),
                smoothstep(seaLevel + 197.0f, seaLevel + 737.0f, height) * 0.6f);
        float beach = (1.0f - smoothstep(seaLevel + 2.0f,
                seaLevel + 25.0f, height))
                * (1.0f - smoothstep(0.10f, 0.34f, slope)) * 0.85f;
        float snowCover = smoothstep(seaLevel + 677.0f, seaLevel + 1157.0f,
                height + (macro - 0.5f) * 130.0f);

        // These are texture multipliers, not flat material colors. The actual
        // grass/stone/sand/snow atlas texels now provide the surface detail.
        float dry = smoothstep(0.15f, 0.85f, moisture);
        // Block top-face sprites already contain their authored material
        // colors. Use near-neutral multipliers so grass stays green, sand
        // stays warm, and snow stays bright instead of tinting every biome
        // brown/orange as the previous aggressive multipliers did.
        float red = mix(0.91f, 1.00f, dry);
        float green = mix(0.98f, 0.94f, dry);
        float blue = mix(0.93f, 0.86f, dry);
        red = mix(red, 0.96f, rockCover);
        green = mix(green, 0.97f, rockCover);
        blue = mix(blue, 0.98f, rockCover);
        red = mix(red, 1.00f, beach);
        green = mix(green, 0.98f, beach);
        blue = mix(blue, 0.93f, beach);
        red = mix(red, 1.00f, snowCover);
        green = mix(green, 1.00f, snowCover);
        blue = mix(blue, 1.00f, snowCover);
        float variation = 0.985f + 0.03f * local;
        return pack(red * variation * 255.0f,
                green * variation * 255.0f,
                blue * variation * 255.0f);
    }

    public static int materialAt(float height, float normalY, float seaLevel) {
        if (!Float.isFinite(height) || !Float.isFinite(normalY)
                || !Float.isFinite(seaLevel)) {
            throw new IllegalArgumentException("surface material inputs must be finite");
        }
        float slope = 1.0f - clamp(normalY, 0.0f, 1.0f);
        float rockCover = Math.max(smoothstep(0.16f, 0.55f, slope),
                smoothstep(seaLevel + 197.0f, seaLevel + 737.0f, height) * 0.6f);
        float beach = (1.0f - smoothstep(seaLevel + 2.0f,
                seaLevel + 25.0f, height))
                * (1.0f - smoothstep(0.10f, 0.34f, slope)) * 0.85f;
        float snow = smoothstep(seaLevel + 677.0f,
                seaLevel + 1157.0f, height);
        if (snow >= 0.5f) return MATERIAL_SNOW;
        if (beach >= 0.45f) return MATERIAL_SAND;
        if (rockCover >= 0.45f) return MATERIAL_ROCK;
        return MATERIAL_GRASS;
    }

    private static float valueNoise(long worldX, long worldZ, int cellSize) {
        long x0 = Math.floorDiv(worldX, cellSize);
        long z0 = Math.floorDiv(worldZ, cellSize);
        float fx = Math.floorMod(worldX, cellSize) / (float) cellSize;
        float fz = Math.floorMod(worldZ, cellSize) / (float) cellSize;
        fx = fx * fx * (3.0f - 2.0f * fx);
        fz = fz * fz * (3.0f - 2.0f * fz);
        float a = hashCell(x0, z0);
        float b = hashCell(x0 + 1L, z0);
        float c = hashCell(x0, z0 + 1L);
        float d = hashCell(x0 + 1L, z0 + 1L);
        return mix(mix(a, b, fx), mix(c, d, fx), fz);
    }

    private static float hashCell(long x, long z) {
        long value = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (value >>> 40) / 16777215.0f;
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
