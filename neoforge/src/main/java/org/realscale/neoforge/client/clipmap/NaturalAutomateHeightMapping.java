package org.realscale.neoforge.client.clipmap;

/**
 * Exact surface-height mapping for the current Natural Automate density graph.
 *
 * <p>terrain_density.json adds height_signal to a Y-clamped gradient spanning
 * -64..2032 with values 0.9921875..-15.3828125. The gradient slope is exactly
 * -1/128, so its zero crossing is y = 63 + 128 * height_signal.
 *
 * <p>This mapping is deliberately isolated and tested against the current
 * data-pack constants. If the noise settings change, update this contract.
 */
public final class NaturalAutomateHeightMapping {
    public static final double SEA_LEVEL = 63.0;
    public static final double HEIGHT_SIGNAL_SCALE = 128.0;

    private NaturalAutomateHeightMapping() {
    }

    public static double surfaceY(double heightSignal) {
        if (!Double.isFinite(heightSignal)) {
            throw new IllegalArgumentException("heightSignal must be finite");
        }
        return SEA_LEVEL + HEIGHT_SIGNAL_SCALE * heightSignal;
    }

    public static boolean isOcean(double heightSignal) {
        return surfaceY(heightSignal) < SEA_LEVEL;
    }
}
