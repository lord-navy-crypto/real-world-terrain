package org.realscale.neoforge.client.clipmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Plans incremental updates for a toroidal height texture.
 *
 * <p>The texture stores {@code gridCells + 1} vertices on each axis. When the
 * snapped world window moves by whole sample cells, the texture offset moves
 * in the opposite coordinate mapping. Exposed outer strips and former
 * inner-hole vertices that become visible in the new ring are dirty.
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
        return between(gridCells, spacing, previousOriginX, previousOriginZ,
                previousOffsetX, previousOffsetZ, nextOriginX, nextOriginZ, 0);
    }

    /** The strictly interior unsampled inner-hole radius is in grid cells. */
    public static Plan between(int gridCells, int spacing,
            long previousOriginX, long previousOriginZ,
            int previousOffsetX, int previousOffsetZ,
            long nextOriginX, long nextOriginZ, int innerHalfCells) {
        validateGrid(gridCells);
        if (spacing <= 0) throw new IllegalArgumentException("spacing must be positive");
        if (innerHalfCells < 0 || innerHalfCells >= gridCells / 2) {
            throw new IllegalArgumentException("inner hole must fit inside height grid");
        }
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

        List<Rect> rectangles = new ArrayList<>(6);
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

        // Previously uncomputed samples inside the OLD ring's inner hole
        // can become visible after a snapped recenter. The earlier planner
        // reused their zero-filled texels and produced artificial trenches.
        if (innerHalfCells > 0) {
            appendFormerHoleExposures(rectangles, gridCells, innerHalfCells,
                    Math.toIntExact(shiftX), Math.toIntExact(shiftZ));
        }

        long plannedSamples = rectangles.stream()
                .mapToLong(rect -> (long) rect.width() * rect.height()).sum();
        if (plannedSamples < dirtySamples) {
            throw new IllegalStateException("outer dirty strips are missing");
        }
        // Counts actual uploads, including rare overlaps, for telemetry.
        if (plannedSamples * 10L >= fullSamples * 7L) {
            return fullUpload(gridCells);
        }
        return new Plan(false, nextOffsetX, nextOffsetZ,
                rectangles, plannedSamples);
    }

    private static void appendFormerHoleExposures(List<Rect> out,
            int cells, int halfHole, int shiftX, int shiftZ) {
        int side = cells + 1;
        int centre = cells / 2;
        // ClipmapHeightGrid.sample deliberately omits STRICTLY interior vertices.
        int holeMin = centre - halfHole + 1;
        int holeMaxExclusive = centre + halfHole;
        int ax0 = Math.max(0, Math.min(side, holeMin - shiftX));
        int ax1 = Math.max(0, Math.min(side, holeMaxExclusive - shiftX));
        int az0 = Math.max(0, Math.min(side, holeMin - shiftZ));
        int az1 = Math.max(0, Math.min(side, holeMaxExclusive - shiftZ));
        if (ax1 <= ax0 || az1 <= az0) return;

        // old hole, projected into new logical tile, MINUS the new inner hole.
        // The rectangular difference fits into four disjoint strips.
        int mx0 = Math.max(ax0, holeMin);
        int mx1 = Math.min(ax1, holeMaxExclusive);
        int mz0 = Math.max(az0, holeMin);
        int mz1 = Math.min(az1, holeMaxExclusive);
        if (mx0 >= mx1 || mz0 >= mz1) {
            addRectangle(out, ax0, az0, ax1, az1);
            return;
        }
        addRectangle(out, ax0, az0, mx0, az1);
        addRectangle(out, mx1, az0, ax1, az1);
        addRectangle(out, mx0, az0, mx1, mz0);
        addRectangle(out, mx0, mz1, mx1, az1);
    }

    private static void addRectangle(List<Rect> rectangles,
            int x0, int z0, int x1, int z1) {
        if (x1 > x0 && z1 > z0) {
            rectangles.add(new Rect(x0, z0, x1 - x0, z1 - z0));
        }
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
