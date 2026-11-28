package org.realscale.neoforge.client.clipmap;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL20;

/**
 * Uses the fog color already computed by Minecraft for the current camera,
 * including biome transitions, time of day, weather and dimension effects.
 */
final class ClipmapFogColor {
    private static final float FALLBACK_RED = 0.58f;
    private static final float FALLBACK_GREEN = 0.69f;
    private static final float FALLBACK_BLUE = 0.80f;

    private ClipmapFogColor() {}

    static void upload(int uniformLocation) {
        float[] color = RenderSystem.getShaderFogColor();
        GL20.glUniform3f(uniformLocation,
                channel(color, 0, FALLBACK_RED),
                channel(color, 1, FALLBACK_GREEN),
                channel(color, 2, FALLBACK_BLUE));
    }

    static float channel(float[] color, int index, float fallback) {
        if (color == null || index < 0 || index >= color.length
                || !Float.isFinite(color[index])) {
            return fallback;
        }
        return Math.max(0.0f, Math.min(1.0f, color[index]));
    }
}
