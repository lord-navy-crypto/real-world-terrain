package org.realscale.neoforge.client.clipmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Plans incremental updates for a toroidal height texture.
 *
 * <p>The texture stores {@code gridCells + 1} vertices on each axis. When the
 * snapped world window moves by whole sample cells, the texture offset moves
 * in the opposite coordinate mapping and only newly exposed strips are dirty.
 * Rectangles are expressed in the new tile's logical grid coordinates; the
 * renderer maps them to wrapped physical texture coordinates.
 */
public final class ClipmapTextureUpdatePlan {
    private ClipmapTextureUpdatePlan() {}

    public static Plan fullUpload(int gridCells) {
        validateGrid(gridCells);
        int side = gridCells + 1;
        return new Plan(true, 0, 0,
                List.of(new Rect(0, 0, side, side)), (long) side * side);
    }

    public static Plan between(int gridCells, int spacing,
            long previousOriginX, long previousOriginZ,
            int previousOffsetX, int previousOffsetZ,
            long nextOriginX, long nextOriginZ) {
        validateGrid(gridCells);
        if (spacing <= 0) throw new IllegalArgumentException("spacing must be positive");
        int side = gridCells + 1;
        if (previousOffsetX < 0 || previousOffsetX >= side
                || previousOffsetZ < 0 || previousOffsetZ >= side) {
            return fullUpload(gridCells);
        }

        long deltaX;
        long deltaZ;
        try {
            deltaX = Math.subtractExact(nextOriginX, previousOriginX);
            deltaZ = Math.subtractExact(nextOriginZ, previousOriginZ);
        } catch (ArithmeticException overflow) {
            return fullUpload(gridCells);
        }
        if (Math.floorMod(deltaX, spacing) != 0L
                || Math.floorMod(deltaZ, spacing) != 0L) {
            return fullUpload(gridCells);
        }

        long shiftX = deltaX / spacing;
        long shiftZ = deltaZ / spacing;
        long absX = Math.abs(shiftX);
        long absZ = Math.abs(shiftZ);
        if (absX > gridCells || absZ > gridCells) {
            return fullUpload(gridCells);
        }

        long dirtySamples = absX * side + absZ * side - absX * absZ;
        long fullSamples = (long) side * side;
        // For large jumps, one full transfer is cheaper than many small calls.
        if (dirtySamples * 10L >= fullSamples * 7L) {
            return fullUpload(gridCells);
        }

        int nextOffsetX = (int) Math.floorMod(
                (long) previousOffsetX + shiftX, (long) side);
        int nextOffsetZ = (int) Math.floorMod(
                (long) previousOffsetZ + shiftZ, (long) side);
        if (dirtySamples == 0L) {
            return new Plan(false, nextOffsetX, nextOffsetZ, List.of(), 0L);
        }

        List<Rect> rectangles = new ArrayList<>(2);
        if (shiftX > 0L) {
            int width = Math.toIntExact(shiftX);
            rectangles.add(new Rect(gridCells - width + 1, 0, width, side));
        } else if (shiftX < 0L) {
            int width = Math.toIntExact(-shiftX);
            rectangles.add(new Rect(0, 0, width, side));
        }

        if (shiftZ != 0L) {
            int height = Math.toIntExact(absZ);
            int z = shiftZ > 0L ? gridCells - height + 1 : 0;
            int x = 0;
            int width = side;
            if (shiftX > 0L) {
                width = side - Math.toIntExact(absX);
            } else if (shiftX < 0L) {
                x = Math.toIntExact(absX);
                width = side - x;
            }
            if (width > 0) rectangles.add(new Rect(x, z, width, height));
        }

        long plannedSamples = rectangles.stream()
                .mapToLong(rect -> (long) rect.width() * rect.height()).sum();
        if (plannedSamples != dirtySamples) {
            throw new IllegalStateException("incremental texture plan does not cover the dirty sample set");
        }
        return new Plan(false, nextOffsetX, nextOffsetZ,
                rectangles, dirtySamples);
    }

    private static void validateGrid(int gridCells) {
        if (gridCells < 16 || gridCells > 256
                || (gridCells & (gridCells - 1)) != 0) {
            throw new IllegalArgumentException("gridCells must be a power of two in 16..256");
        }
    }

    public record Plan(boolean fullRefresh, int offsetX, int offsetZ,
            List<Rect> rectangles, long uploadedSamples) {
        public Plan {
            rectangles = List.copyOf(rectangles);
            if (offsetX < 0 || offsetZ < 0 || uploadedSamples < 0L) {
                throw new IllegalArgumentException("invalid texture update plan");
            }
        }
    }

    public record Rect(int x, int z, int width, int height) {
        public Rect {
            if (x < 0 || z < 0 || width < 1 || height < 1) {
                throw new IllegalArgumentException("texture update rectangle must be positive");
            }
        }
    }
}
