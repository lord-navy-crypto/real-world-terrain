package org.realscale.neoforge;

import java.util.function.IntPredicate;

/**
 * Pure, deterministic cave-floor sampling order. No chunk access; the caller
 * supplies a predicate that inspects a support block and a dry air pocket.
 */
public final class HabitatFloorSearch {
    public static final int NOT_FOUND = Integer.MIN_VALUE;

    private HabitatFloorSearch() {}

    public static int nearest(int centerY, int minimumY, int maximumY,
                              int radius, IntPredicate supportedDryPocket) {
        if (minimumY > maximumY || radius < 0) return NOT_FOUND;
        for (int delta = 0; delta <= radius; ++delta) {
            int down = centerY - delta;
            if (down >= minimumY && down <= maximumY && supportedDryPocket.test(down))
                return down;
            if (delta == 0) continue;
            int up = centerY + delta;
            if (up >= minimumY && up <= maximumY && supportedDryPocket.test(up))
                return up;
        }
        return NOT_FOUND;
    }
}
