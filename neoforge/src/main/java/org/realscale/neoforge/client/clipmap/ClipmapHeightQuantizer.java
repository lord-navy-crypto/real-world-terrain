package org.realscale.neoforge.client.clipmap;

/** Quantizes world-space heights into a normalized 16-bit GPU texture channel. */
public final class ClipmapHeightQuantizer {
    public static final int MAX_UNSIGNED_SHORT = 65_535;

    private ClipmapHeightQuantizer() {
    }

    public static int encode(double height, double minHeight, double maxHeight) {
        validateRange(minHeight, maxHeight);
        if (!Double.isFinite(height)) {
            throw new IllegalArgumentException("height must be finite");
        }
        double normalized = (height - minHeight) / (maxHeight - minHeight);
        normalized = Math.max(0.0, Math.min(1.0, normalized));
        return (int) Math.round(normalized * MAX_UNSIGNED_SHORT);
    }

    public static double decode(int encoded, double minHeight, double maxHeight) {
        validateRange(minHeight, maxHeight);
        if (encoded < 0 || encoded > MAX_UNSIGNED_SHORT) {
            throw new IllegalArgumentException("encoded height must be an unsigned 16-bit value");
        }
        return minHeight + (encoded / (double) MAX_UNSIGNED_SHORT)
                * (maxHeight - minHeight);
    }

    private static void validateRange(double minHeight, double maxHeight) {
        if (!Double.isFinite(minHeight) || !Double.isFinite(maxHeight)
                || maxHeight <= minHeight) {
            throw new IllegalArgumentException("height range must be finite and increasing");
        }
    }
}
