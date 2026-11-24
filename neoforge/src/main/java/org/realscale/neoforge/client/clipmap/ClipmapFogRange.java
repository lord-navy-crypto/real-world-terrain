package org.realscale.neoforge.client.clipmap;

/**
 * Distance-relative fog band for the square Geometry Clipmap horizon.
 * The outermost terrain ring must fade out before its hard rectangular
 * boundary becomes visible, including short or large view distances.
 */
public record ClipmapFogRange(float startBlocks, float endBlocks) {
    public static ClipmapFogRange forGrid(int gridCells,
            int finestSpacingBlocks, int levelCount) {
        if (gridCells < 16 || (gridCells & (gridCells - 1)) != 0
                || finestSpacingBlocks < 1
                || (finestSpacingBlocks & (finestSpacingBlocks - 1)) != 0
                || levelCount < 1 || levelCount > 8) {
            throw new IllegalArgumentException("invalid clipmap fog layout");
        }
        double farHalfExtent = (gridCells / 2.0)
                * finestSpacingBlocks * (1L << (levelCount - 1));
        if (!Double.isFinite(farHalfExtent)
                || farHalfExtent > Float.MAX_VALUE / 2.0) {
            throw new IllegalArgumentException("clipmap fog range overflow");
        }
        return new ClipmapFogRange((float) (0.62 * farHalfExtent),
                (float) (0.98 * farHalfExtent));
    }

    public ClipmapFogRange {
        if (!Float.isFinite(startBlocks) || !Float.isFinite(endBlocks)
                || startBlocks <= 0.0f || endBlocks <= startBlocks) {
            throw new IllegalArgumentException("fog end must exceed positive start");
        }
    }
}
