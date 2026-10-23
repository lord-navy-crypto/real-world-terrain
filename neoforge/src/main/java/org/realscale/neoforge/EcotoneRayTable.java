package org.realscale.neoforge;

/**
 * Immutable, tiny lookup of the eight fixed ecotone ray headings.
 * Only integer geometry is cached: there are no biomes, seeds, climates,
 * world positions or worker-specific values in this table.
 */
final class EcotoneRayTable {
    private final int[][] distanceSquared;

    EcotoneRayTable(int[][] directions, int maximumStep) {
        if (maximumStep < 1)
            throw new IllegalArgumentException("Expected positive ray length");
        distanceSquared = new int[directions.length][maximumStep + 1];
        for (int heading = 0; heading < directions.length; heading++) {
            for (int step = 1; step <= maximumStep; step++) {
                distanceSquared[heading][step] = EcotoneMath.quartDistanceSquared(
                        directions[heading][0], directions[heading][1], step);
            }
        }
    }

    int squared(int heading, int step) {
        return distanceSquared[heading][step];
    }

    /**
     * Algebraically identical to EcotoneMath.withinRadialBand for the
     * validated nonzero headings and positive steps used in production.
     * Uses the squared distance already read for nearest-ray precedence.
     */
    static boolean withinBand(int squared, int widthBlocks) {
        return widthBlocks >= 16 && widthBlocks <= 64
                && 4L * squared <= (long) widthBlocks * widthBlocks;
    }
}
