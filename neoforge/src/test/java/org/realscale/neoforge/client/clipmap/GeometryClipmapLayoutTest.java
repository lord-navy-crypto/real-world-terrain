package org.realscale.neoforge.client.clipmap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class GeometryClipmapLayoutTest {
    private final GeometryClipmapLayout planner = new GeometryClipmapLayout();

    @Test
    void defaultLayoutUsesTenChunkNearFieldAndSixAlignedLevels() {
        GeometryClipmapLayout.Layout layout = planner.compute(0.0, 0.0);

        assertEquals(160L, layout.nearRadiusBlocks());
        assertEquals(192L, layout.effectiveInnerRadiusBlocks());
        assertEquals(6, layout.levels().size());
        long[] expectedSpacing = {4, 8, 16, 32, 64, 128};
        long[] expectedHalfExtent = {256, 512, 1024, 2048, 4096, 8192};
        for (int i = 0; i < expectedSpacing.length; i++) {
            GeometryClipmapLayout.Level level = layout.levels().get(i);
            assertEquals(expectedSpacing[i], level.cellSpacingBlocks());
            assertEquals(expectedHalfExtent[i], level.outerBounds().widthX() / 2L);
            assertTrue(level.outerBounds().contains(level.innerBounds()));
            assertEquals(0L, Math.floorMod(level.innerBounds().minX(),
                    level.cellSpacingBlocks()));
            assertEquals(0L, Math.floorMod(level.innerBounds().maxX(),
                    level.cellSpacingBlocks()));
        }
    }

    @Test
    void allLevelsShareOneCentreSnappedToPenultimateSpacing() {
        GeometryClipmapLayout.Layout layout = planner.compute(127.2, -91.5);
        for (GeometryClipmapLayout.Level level : layout.levels()) {
            assertEquals(layout.levels().get(0).centerX(), level.centerX());
            assertEquals(layout.levels().get(0).centerZ(), level.centerZ());
        }
        assertEquals(128L, layout.levels().get(0).centerX());
        assertEquals(-64L, layout.levels().get(0).centerZ());
    }

    @Test
    void negativeCoordinatesUseSymmetricNearestSnapping() {
        GeometryClipmapLayout.Layout layout = planner.compute(-32.1, -31.9);
        assertEquals(-64L, layout.levels().get(0).centerX());
        assertEquals(0L, layout.levels().get(0).centerZ());
    }

    @Test
    void movementInsideSharedSnapCellDoesNotRebuildAnyLevel() {
        GeometryClipmapLayout.Layout before = planner.compute(0.0, 0.0);
        GeometryClipmapLayout.Layout after = planner.compute(31.9, -31.9, before);
        for (GeometryClipmapLayout.Level level : after.levels()) {
            assertEquals(0L, level.updatePlan().newSampleVertices());
            assertFalse(level.updatePlan().fullRefresh());
        }
    }

    @Test
    void recenterProducesAlignedLShapedIncrementalUpdatesAtEveryLevel() {
        GeometryClipmapLayout.Layout before = planner.compute(0.0, 0.0);
        GeometryClipmapLayout.Layout after = planner.compute(64.1, 0.0, before);

        long[] expectedShifts = {16, 8, 4, 2, 1, 1};
        for (int i = 0; i < expectedShifts.length; i++) {
            GeometryClipmapLayout.UpdatePlan update = after.levels().get(i).updatePlan();
            assertEquals(expectedShifts[i], update.shiftCellsX());
            assertEquals(0, update.shiftCellsZ());
            assertFalse(update.fullRefresh());
        }
    }

    @Test
    void diagonalMovementCountsSharedCornerOnlyOnce() {
        GeometryClipmapLayout.Layout before = planner.compute(0.0, 0.0);
        GeometryClipmapLayout.Layout after = planner.compute(64.1, 64.1, before);
        GeometryClipmapLayout.UpdatePlan update = after.levels().get(0).updatePlan();

        assertEquals(16, update.shiftCellsX());
        assertEquals(16, update.shiftCellsZ());
        assertEquals(16L * 129L + 16L * 129L - 16L * 16L,
                update.newSampleVertices());
    }

    @Test
    void teleportForcesFullRefreshForLevelsWhoseWindowsCannotBeShifted() {
        GeometryClipmapLayout.Layout before = planner.compute(0.0, 0.0);
        GeometryClipmapLayout.Layout after = planner.compute(8192.0, 0.0, before);
        assertTrue(after.levels().get(0).updatePlan().fullRefresh());
        assertEquals(129L * 129L,
                after.levels().get(0).updatePlan().newSampleVertices());
    }

    @Test
    void factoryChoosesSafeFinestSpacingForLargeRenderDistances() {
        GeometryClipmapLayout ten = GeometryClipmapLayout.forRenderDistanceChunks(10);
        GeometryClipmapLayout twenty = GeometryClipmapLayout.forRenderDistanceChunks(20);
        assertEquals(4, ten.compute(0, 0).baseCellSpacingBlocks());
        assertEquals(8, twenty.compute(0, 0).baseCellSpacingBlocks());
        assertTrue(twenty.compute(0, 0).effectiveInnerRadiusBlocks()
                < twenty.compute(0, 0).levels().get(0).outerBounds().widthX() / 2);
    }

    @Test
    void rejectsIncompatiblePreviousLayoutAndInvalidGrid() {
        GeometryClipmapLayout.Layout before = planner.compute(0.0, 0.0);
        GeometryClipmapLayout other = new GeometryClipmapLayout(128, 5, 4, 10);
        assertThrows(IllegalArgumentException.class,
                () -> other.compute(64.0, 0.0, before));
        assertThrows(IllegalArgumentException.class,
                () -> new GeometryClipmapLayout(100, 6, 4, 10));
    }
}
