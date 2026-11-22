package org.realscale.neoforge.client.clipmap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class GeometryClipmapLayoutTest {
    private final GeometryClipmapLayout planner = new GeometryClipmapLayout();

    @Test
    void defaultLayoutUsesTenChunkNearFieldAndSixDoublingLevels() {
        GeometryClipmapLayout.Layout layout = planner.compute(0.0, 0.0);

        assertEquals(10L * 16L, layout.nearRadiusBlocks());
        assertEquals(6, layout.levels().size());
        long[] expectedSpacing = {4, 8, 16, 32, 64, 128};
        long[] expectedHalfExtent = {256, 512, 1024, 2048, 4096, 8192};
        for (int i = 0; i < expectedSpacing.length; i++) {
            GeometryClipmapLayout.Level level = layout.levels().get(i);
            assertEquals(expectedSpacing[i], level.cellSpacingBlocks());
            assertEquals(expectedHalfExtent[i],
                    level.outerBounds().widthX() / 2L);
        }
        assertEquals(16_384L, layout.maxAxisExtentBlocks() * 2L);
    }

    @Test
    void everyCoarserWindowContainsThePreviousLevelWindow() {
        GeometryClipmapLayout.Layout layout = planner.compute(127.2, -91.5);
        for (GeometryClipmapLayout.Level level : layout.levels()) {
            assertTrue(level.outerBounds().contains(level.innerBounds()),
                    "level " + level.index() + " must contain its inner window");
        }
    }

    @Test
    void negativeCoordinatesSnapUsingFloorDivision() {
        GeometryClipmapLayout.Layout layout = planner.compute(-0.1, -4.1);
        assertEquals(-4L, layout.levels().get(0).centerX());
        assertEquals(-8L, layout.levels().get(0).centerZ());
        assertEquals(-8L, layout.levels().get(1).centerX());
        assertEquals(-8L, layout.levels().get(1).centerZ());
    }

    @Test
    void movementOnlyUpdatesLevelsWhoseGridCellWasCrossed() {
        GeometryClipmapLayout.Layout before = planner.compute(0.0, 0.0);
        GeometryClipmapLayout.Layout after = planner.compute(4.1, 0.0, before);

        GeometryClipmapLayout.UpdatePlan fine = after.levels().get(0).updatePlan();
        assertEquals(1, fine.shiftCellsX());
        assertEquals(0, fine.shiftCellsZ());
        assertEquals(129L, fine.newSampleVertices());
        assertFalse(fine.fullRefresh());

        for (int i = 1; i < after.levels().size(); i++) {
            assertEquals(0L, after.levels().get(i).updatePlan().newSampleVertices(),
                    "coarser level " + i + " should not update before its larger snap interval");
        }
    }

    @Test
    void diagonalMovementProducesOneLShapedUpdateRegionWithoutDoubleCountingCorner() {
        GeometryClipmapLayout.Layout before = planner.compute(0.0, 0.0);
        GeometryClipmapLayout.Layout after = planner.compute(4.0, 4.0, before);
        GeometryClipmapLayout.UpdatePlan update = after.levels().get(0).updatePlan();

        assertEquals(1, update.shiftCellsX());
        assertEquals(1, update.shiftCellsZ());
        assertEquals(257L, update.newSampleVertices());
        assertFalse(update.fullRefresh());
    }

    @Test
    void teleportForcesFullRefreshForLevelsWhoseWindowsCannotBeIncrementallyShifted() {
        GeometryClipmapLayout.Layout before = planner.compute(0.0, 0.0);
        GeometryClipmapLayout.Layout after = planner.compute(8192.0, 0.0, before);
        GeometryClipmapLayout.UpdatePlan update = after.levels().get(0).updatePlan();

        assertTrue(update.fullRefresh());
        assertEquals(129L * 129L, update.newSampleVertices());
    }

    @Test
    void rejectsIncompatiblePreviousLayout() {
        GeometryClipmapLayout.Layout before = planner.compute(0.0, 0.0);
        GeometryClipmapLayout other = new GeometryClipmapLayout(
                128, 5, 4, 10);
        assertThrows(IllegalArgumentException.class,
                () -> other.compute(4.0, 0.0, before));
    }

    @Test
    void rejectsInvalidGridAndNearFieldConfigurations() {
        assertThrows(IllegalArgumentException.class,
                () -> new GeometryClipmapLayout(100, 6, 4, 10));
        assertThrows(IllegalArgumentException.class,
                () -> new GeometryClipmapLayout(128, 6, 4, 20));
    }
}
