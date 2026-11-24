package org.realscale.neoforge.client.clipmap;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class ClipmapTextureUpdatePlanTest {
    private static final int CELLS = 128;
    private static final int SIDE = CELLS + 1;
    private static final int SPACING = 8;

    @Test
    void stationaryWindowDoesNotUploadAnySamples() {
        var plan = ClipmapTextureUpdatePlan.between(
                CELLS, SPACING, -512, -512, 17, 91, -512, -512);
        assertFalse(plan.fullRefresh());
        assertEquals(17, plan.offsetX());
        assertEquals(91, plan.offsetZ());
        assertTrue(plan.rectangles().isEmpty());
        assertEquals(0L, plan.uploadedSamples());
    }

    @Test
    void positiveAndNegativeShiftsUploadOnlyNewlyExposedEdges() {
        var east = ClipmapTextureUpdatePlan.between(
                CELLS, SPACING, 0, 0, 0, 0, SPACING, 0);
        assertEquals(1, east.offsetX());
        assertEquals(0, east.offsetZ());
        assertEquals(1, east.rectangles().size());
        assertEquals(new ClipmapTextureUpdatePlan.Rect(CELLS, 0, 1, SIDE),
                east.rectangles().get(0));

        var west = ClipmapTextureUpdatePlan.between(
                CELLS, SPACING, 0, 0, 0, 0, -SPACING, 0);
        assertEquals(SIDE - 1, west.offsetX());
        assertEquals(new ClipmapTextureUpdatePlan.Rect(0, 0, 1, SIDE),
                west.rectangles().get(0));
    }

    @Test
    void diagonalShiftCoversTheDirtyUnionWithoutDuplicatingTheCorner() {
        int shiftX = 3;
        int shiftZ = -2;
        var plan = ClipmapTextureUpdatePlan.between(
                CELLS, SPACING, 0, 0, 7, 11,
                shiftX * (long) SPACING, shiftZ * (long) SPACING);

        assertFalse(plan.fullRefresh());
        assertEquals(7 + shiftX, plan.offsetX());
        assertEquals(11 + shiftZ, plan.offsetZ());
        assertEquals(2, plan.rectangles().size());
        assertEquals((long) shiftX * SIDE + (long) -shiftZ * SIDE
                - (long) shiftX * -shiftZ, plan.uploadedSamples());

        Set<Long> covered = new HashSet<>();
        for (var rect : plan.rectangles()) {
            for (int z = rect.z(); z < rect.z() + rect.height(); z++) {
                for (int x = rect.x(); x < rect.x() + rect.width(); x++) {
                    assertTrue(covered.add(((long) z << 32) | (x & 0xffffffffL)),
                            "dirty rectangles must not overlap");
                }
            }
        }
        assertEquals(plan.uploadedSamples(), covered.size());
    }

    @Test
    void textureOffsetWrapsAcrossThe129VertexRing() {
        var plan = ClipmapTextureUpdatePlan.between(
                CELLS, SPACING, 0, 0, SIDE - 1, SIDE - 2,
                SPACING, -SPACING);
        assertEquals(0, plan.offsetX());
        assertEquals(SIDE - 3, plan.offsetZ());
    }

    @Test
    void largeOrUnalignedMovesFallBackToOneFullUpload() {
        var large = ClipmapTextureUpdatePlan.between(
                CELLS, SPACING, 0, 0, 0, 0, 100L * SPACING, 100L * SPACING);
        assertTrue(large.fullRefresh());
        assertEquals(0, large.offsetX());
        assertEquals(0, large.offsetZ());
        assertEquals((long) SIDE * SIDE, large.uploadedSamples());
        assertEquals(new ClipmapTextureUpdatePlan.Rect(0, 0, SIDE, SIDE),
                large.rectangles().get(0));

        var unaligned = ClipmapTextureUpdatePlan.between(
                CELLS, SPACING, 0, 0, 0, 0, 1, 0);
        assertTrue(unaligned.fullRefresh());
    }


    @Test
    void movingTheWindowRepairsPreviouslyUnsampledInnerHole() {
        var plan = ClipmapTextureUpdatePlan.between(
                CELLS, SPACING, 0, 0, 17, 93,
                8L * SPACING, 0L, 32);
        assertFalse(plan.fullRefresh());
        assertEquals(25, plan.offsetX());
        assertEquals(93, plan.offsetZ());
        assertTrue(covers(plan, 30, CELLS / 2),
                "old hole texel must not become a fake terrain trench");
        assertTrue(covers(plan, CELLS, CELLS / 2));
        assertTrue(plan.uploadedSamples() < (long) SIDE * SIDE);
    }

    @Test
    void newRingNeverReadsPreviouslyUncomputedSamplesInAnyDirection() {
        int[] shifts = {-32, -16, -2, -1, 0, 1, 2, 16, 32};
        for (int halfHole : new int[] {1, 4, 16, 32}) {
            for (int dx : shifts) {
                for (int dz : shifts) {
                    var plan = ClipmapTextureUpdatePlan.between(
                            CELLS, SPACING, 0, 0, SIDE - 1, 0,
                            (long) dx * SPACING, (long) dz * SPACING,
                            halfHole);
                    for (int z = 0; z <= CELLS; z++) {
                        for (int x = 0; x <= CELLS; x++) {
                            if (insideHole(x, z, halfHole)) continue;
                            int oldX = x + dx;
                            int oldZ = z + dz;
                            boolean missing = oldX < 0 || oldX > CELLS
                                    || oldZ < 0 || oldZ > CELLS
                                    || insideHole(oldX, oldZ, halfHole);
                            if (missing) {
                                assertTrue(covers(plan, x, z),
                                        "stale texel hole=" + halfHole
                                            + " shift=" + dx + "," + dz
                                            + " grid=" + x + "," + z);
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean insideHole(int x, int z, int halfHole) {
        return Math.abs(x - CELLS / 2) < halfHole
                && Math.abs(z - CELLS / 2) < halfHole;
    }

    private static boolean covers(ClipmapTextureUpdatePlan.Plan plan,
            int x, int z) {
        for (var rect : plan.rectangles()) {
            if (x >= rect.x() && x < rect.x() + rect.width()
                    && z >= rect.z() && z < rect.z() + rect.height()) {
                return true;
            }
        }
        return false;
    }

    @Test
    void invalidGridAndSpacingAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ClipmapTextureUpdatePlan.fullUpload(100));
        assertThrows(IllegalArgumentException.class,
                () -> ClipmapTextureUpdatePlan.between(
                        CELLS, 0, 0, 0, 0, 0, 0, 0));
    }
}
