package org.realscale.neoforge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class VirtualUndergroundBoundaryTest {
    @Test void flatSurfaceProducesAStableVirtualBoundary() {
        int boundary=VirtualUndergroundBoundary.conservativeBoundaryY(
                96,96,96,96,96);
        assertEquals(96,boundary);
        assertTrue(VirtualUndergroundBoundary.isBelowBoundary(84,boundary));
        assertFalse(VirtualUndergroundBoundary.isBelowBoundary(85,boundary));
    }

    @Test void minimumSurfaceHeightRequiresClearanceInEveryProbeColumn() {
        int boundary=VirtualUndergroundBoundary.conservativeBoundaryY(
                110,104,108,101,106);
        assertEquals(101,boundary);
        assertTrue(VirtualUndergroundBoundary.isBelowBoundary(89,boundary));
        assertFalse(VirtualUndergroundBoundary.isBelowBoundary(90,boundary));
    }

    @Test void steepLocalSurfaceDiscontinuityFailsOpen() {
        int boundary=VirtualUndergroundBoundary.conservativeBoundaryY(
                100,125,99,101,98);
        assertEquals(VirtualUndergroundBoundary.UNKNOWN_BOUNDARY,boundary);
        assertFalse(VirtualUndergroundBoundary.isBelowBoundary(40,boundary));
    }

    @Test void exactSpreadLimitIsAcceptedButOneBlockMoreIsUnknown() {
        assertEquals(70,VirtualUndergroundBoundary.conservativeBoundaryY(
                70,94,80,83,76));
        assertEquals(VirtualUndergroundBoundary.UNKNOWN_BOUNDARY,
                VirtualUndergroundBoundary.conservativeBoundaryY(
                        70,95,80,83,76));
    }

    @Test void negativeBuildHeightsAndLargeCoordinatesRemainSafe() {
        int boundary=VirtualUndergroundBoundary.conservativeBoundaryY(
                -20,-18,-19,-17,-20);
        assertEquals(-20,boundary);
        assertTrue(VirtualUndergroundBoundary.isBelowBoundary(-32,boundary));
        assertFalse(VirtualUndergroundBoundary.isBelowBoundary(
                Integer.MIN_VALUE,VirtualUndergroundBoundary.UNKNOWN_BOUNDARY));
    }

    @Test void explicitClearanceIsCheckedWithoutIntegerOverflow() {
        assertTrue(VirtualUndergroundBoundary.isBelowBoundary(
                Integer.MIN_VALUE+32,Integer.MIN_VALUE+64,32));
        assertFalse(VirtualUndergroundBoundary.isBelowBoundary(
                Integer.MAX_VALUE-4,Integer.MAX_VALUE,12));
        assertFalse(VirtualUndergroundBoundary.isBelowBoundary(0,100,-1));
    }
}
