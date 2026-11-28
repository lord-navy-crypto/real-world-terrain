package org.realscale.neoforge.client.clipmap;

import net.minecraft.world.level.Level;

import java.util.Objects;

/**
 * Immutable per-frame environment inputs for the standalone Geometry Clipmap.
 *
 * <p>The sun direction follows Minecraft 1.21.1's celestial rotation:
 * rotate the world-up vector around X by the sky angle, then around Y by
 * -90 degrees. This produces a world-space direction of (-sin(angle),
 * cos(angle), 0), matching the vanilla/Iris celestial basis.
 */
public final class ClipmapEnvironment {
    private ClipmapEnvironment() {}

    static State from(Level level, float partialTick) {
        Objects.requireNonNull(level, "level");
        return from(level.getSeaLevel(), level.getSunAngle(partialTick),
                level.getRainLevel(partialTick), level.getThunderLevel(partialTick));
    }

    static State from(int seaLevel, float sunAngle, float rain, float thunder) {
        float safeAngle = Float.isFinite(sunAngle) ? sunAngle : (float) Math.PI;
        float sunX = (float) -Math.sin(safeAngle);
        float sunY = (float) Math.cos(safeAngle);
        float daylight = clamp01((sunY + 0.10f) / 1.10f);
        float rainAmount = clamp01(rain);
        float thunderAmount = clamp01(thunder);
        float weatherTransmission = clamp01(
                1.0f - 0.65f * rainAmount - 0.35f * thunderAmount);

        // Keep a readable night-time ambient floor. Clouds soften ambient
        // brightness slightly, while rain and thunder strongly attenuate sun.
        float ambient = Math.max(0.16f,
                0.20f + 0.30f * daylight - 0.04f * rainAmount - 0.03f * thunderAmount);
        float direct = 0.50f * daylight * weatherTransmission;
        return new State(seaLevel, sunX, sunY, 0.0f, ambient, direct);
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) return 0.0f;
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    public record State(int seaLevel, float sunX, float sunY, float sunZ,
            float ambientLight, float directLight) {
        public State {
            if (!Float.isFinite(sunX) || !Float.isFinite(sunY)
                    || !Float.isFinite(sunZ) || !Float.isFinite(ambientLight)
                    || !Float.isFinite(directLight)
                    || ambientLight < 0.0f || ambientLight > 1.0f
                    || directLight < 0.0f || directLight > 1.0f) {
                throw new IllegalArgumentException("Clipmap environment values must be finite and within [0, 1]");
            }
        }
    }
}
