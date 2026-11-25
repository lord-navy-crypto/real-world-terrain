package org.realscale.neoforge;

/**
 * A non-persistent, conservative underground boundary derived from already
 * loaded local surface-height samples. It creates no marker blocks, chunk
 * data, dimension or saved-world state.
 *
 * <p>The minimum of the local surface envelope is used rather than a chunk
 * average: all sampled columns must provide the required roof clearance.
 * Steep or discontinuous surface terrain is treated as ambiguous and fails
 * open so cliffs, ravines and cave mouths do not hide distant cave LODs.
 */
public final class VirtualUndergroundBoundary {
    public static final int UNKNOWN_BOUNDARY = Integer.MIN_VALUE;
    public static final int MAX_LOCAL_SURFACE_SPREAD_BLOCKS = 24;
    public static final int MIN_ROOF_CLEARANCE_BLOCKS = 12;

    private VirtualUndergroundBoundary() {}

    /**
     * Returns the lowest of five nearby surface heights when their local
     * spread is bounded. Returns UNKNOWN_BOUNDARY for an unstable envelope.
     */
    public static int conservativeBoundaryY(
            int center,int east,int west,int south,int north) {
        int min=Math.min(center,Math.min(east,Math.min(west,Math.min(south,north))));
        int max=Math.max(center,Math.max(east,Math.max(west,Math.max(south,north))));
        if((long)max-(long)min>MAX_LOCAL_SURFACE_SPREAD_BLOCKS)
            return UNKNOWN_BOUNDARY;
        return min;
    }

    /** True only when the camera is below every sampled surface by the roof margin. */
    public static boolean isBelowBoundary(int eyeY,int boundaryY) {
        return isBelowBoundary(eyeY,boundaryY,MIN_ROOF_CLEARANCE_BLOCKS);
    }

    public static boolean isBelowBoundary(int eyeY,int boundaryY,int clearanceBlocks) {
        if(boundaryY==UNKNOWN_BOUNDARY || clearanceBlocks<0)return false;
        return (long)boundaryY-(long)eyeY>=clearanceBlocks;
    }
}
