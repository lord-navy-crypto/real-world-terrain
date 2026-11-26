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

    /**
     * World-anchored procedural terrain color for the Iris-native path.
     *
     * <p>These are complete terrain colors, not tint multipliers for Minecraft's
     * block/item atlas. The Iris terrain shader still requires a texture
     * coordinate, so the renderer samples one neutral white texel and carries
     * the actual grass, dry-ground, rock, sand and snow material through vertex
     * color. This keeps the terrain independent of inventory and mod-item art.
     */
    public static int terrainColorAt(float height, float normalY, float seaLevel,
            boolean verticalSkirt, long worldX, long worldZ) {
        if (!Float.isFinite(height) || !Float.isFinite(normalY)
                || !Float.isFinite(seaLevel)) {
            throw new IllegalArgumentException("surface palette inputs must be finite");
        }
        if (verticalSkirt) return 0x6B665E;

        float macro = valueNoise(worldX, worldZ, 2048);
        float regional = valueNoise(worldX, worldZ, 512);
        float local = valueNoise(worldX, worldZ, 256);
        float moisture = clamp(0.72f * macro + 0.28f * regional, 0.0f, 1.0f);
        float slope = 1.0f - clamp(normalY, 0.0f, 1.0f);

        // Broad vegetation palette. Climate sampling can replace this
        // world-anchored proxy once the server sends biome/climate attributes.
        // Procedural lushness proxy only; this is not measured biome climate.
        float lushness = smoothstep(0.15f, 0.85f, moisture);
        float red = mix(0.37f, 0.18f, lushness);
        float green = mix(0.34f, 0.34f, lushness);
        float blue = mix(0.22f, 0.16f, lushness);

        float rockCover = Math.max(
                smoothstep(0.16f, 0.55f, slope),
                smoothstep(seaLevel + 197.0f, seaLevel + 737.0f, height) * 0.6f);
        red = mix(red, 0.42f, rockCover);
        green = mix(green, 0.40f, rockCover);
        blue = mix(blue, 0.37f, rockCover);

        float beach = (1.0f - smoothstep(seaLevel + 2.0f,
                seaLevel + 25.0f, height))
                * (1.0f - smoothstep(0.10f, 0.34f, slope));
        red = mix(red, 0.61f, 0.85f * beach);
        green = mix(green, 0.55f, 0.85f * beach);
        blue = mix(blue, 0.38f, 0.85f * beach);

        float snowCover = smoothstep(seaLevel + 677.0f, seaLevel + 1157.0f,
                height + (macro - 0.5f) * 130.0f);
        red = mix(red, 0.84f, snowCover);
        green = mix(green, 0.88f, snowCover);
        blue = mix(blue, 0.91f, snowCover);

        // Low-amplitude world-anchored variation prevents a flat painted look
        // without reintroducing cell-sized texture repetition at coarse LODs.
        float variation = 0.97f + 0.06f * regional;
        float mottling = 0.985f + 0.03f * local;
        float lightness = variation * mottling;
        return pack(red * lightness * 255.0f,
                green * lightness * 255.0f,
                blue * lightness * 255.0f);
    }

    /** Biome-aware palette; unknown IDs retain the existing procedural fallback. */
    public static int terrainColorAt(float height,float normalY,float seaLevel,
            boolean verticalSkirt,byte biomeMaterial,long worldX,long worldZ){
        if(biomeMaterial==ClipmapBiomeMaterial.UNKNOWN)
            return terrainColorAt(height,normalY,seaLevel,verticalSkirt,worldX,worldZ);
        if(!Float.isFinite(height)||!Float.isFinite(normalY)||!Float.isFinite(seaLevel))
            throw new IllegalArgumentException("surface palette inputs must be finite");
        if(verticalSkirt)return 0x6B665E;
        int base=ClipmapBiomeMaterial.baseColor(biomeMaterial);
        if(base<0)return terrainColorAt(height,normalY,seaLevel,verticalSkirt,worldX,worldZ);
        float slope=1.0f-clamp(normalY,0.0f,1.0f);
        float rock=smoothstep(0.18f,0.58f,slope);
        float snow=smoothstep(seaLevel+677.0f,seaLevel+1157.0f,height+(valueNoise(worldX,worldZ,2048)-0.5f)*130.0f);
        float red=mix(channel(base,16),channel(ROCK,16),rock);
        float green=mix(channel(base,8),channel(ROCK,8),rock);
        float blue=mix(channel(base,0),channel(ROCK,0),rock);
        red=mix(red,channel(SNOW,16),snow);green=mix(green,channel(SNOW,8),snow);blue=mix(blue,channel(SNOW,0),snow);
        float regional=valueNoise(worldX,worldZ,512),local=valueNoise(worldX,worldZ,256);
        float lightness=(0.97f+0.06f*regional)*(0.985f+0.03f*local);
        return pack(red*lightness,green*lightness,blue*lightness);
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
