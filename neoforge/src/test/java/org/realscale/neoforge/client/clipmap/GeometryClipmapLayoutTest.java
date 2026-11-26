package org.realscale.neoforge.client.clipmap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class GeometryClipmapLayoutTest {
    private final GeometryClipmapLayout planner = new GeometryClipmapLayout();

    @Test
    void defaultLayoutUsesBalanced128GridAndSixAlignedLevels() {
        GeometryClipmapLayout.Layout layout = planner.compute(0.0, 0.0);

        assertEquals(128, layout.gridCells());
        assertEquals(160L, layout.nearRadiusBlocks());
        assertEquals(96L, layout.effectiveInnerRadiusBlocks());
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
        GeometryClipmapLayout.Layout layout = planner.compute(200.2, -200.5);
        for (GeometryClipmapLayout.Level level : layout.levels()) {
            assertEquals(layout.levels().get(0).centerX(), level.centerX());
            assertEquals(layout.levels().get(0).centerZ(), level.centerZ());
        }
        assertEquals(256L, layout.levels().get(0).centerX());
        assertEquals(-256L, layout.levels().get(0).centerZ());
    }

    @Test
    void negativeCoordinatesUseSymmetricNearestSnapping() {
        GeometryClipmapLayout.Layout layout = planner.compute(-128.1, -127.9);
        assertEquals(-128L, layout.levels().get(0).centerX());
        assertEquals(-128L, layout.levels().get(0).centerZ());
    }

    @Test
    void movementInsideSharedSnapCellDoesNotRebuildAnyLevel() {
        GeometryClipmapLayout.Layout before = planner.compute(0.0, 0.0);
        GeometryClipmapLayout.Layout after = planner.compute(63.9, -63.9, before);
        for (GeometryClipmapLayout.Level level : after.levels()) {
            assertEquals(0L, level.updatePlan().newSampleVertices());
            assertFalse(level.updatePlan().fullRefresh());
        }
    }

    @Test
    void recenterProducesAlignedLShapedIncrementalUpdatesAtEveryLevel() {
        GeometryClipmapLayout.Layout before = planner.compute(0.0, 0.0);
        GeometryClipmapLayout.Layout after = planner.compute(64.1, 0.0, before);

        long[] expectedShifts = {32, 16, 8, 4, 2, 1};
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

        assertEquals(32, update.shiftCellsX());
        assertEquals(32, update.shiftCellsZ());
        assertEquals(32L * 129L + 32L * 129L - 32L * 32L,
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
        assertEquals(16, GeometryClipmapLayout.forRenderDistanceChunks(33)
                .compute(0, 0).baseCellSpacingBlocks());
        assertTrue(twenty.compute(0, 0).effectiveInnerRadiusBlocks()
                < twenty.compute(0, 0).levels().get(0).outerBounds().widthX() / 2);
    }

    @Test
    void everyNearViewDistanceKeepsTheClipmapHoleInsideVanillaRange() {
        for (int chunks = 2; chunks <= 64; chunks++) {
            GeometryClipmapLayout candidate =
                    GeometryClipmapLayout.forRenderDistanceChunks(chunks);
            GeometryClipmapLayout.Layout initial = candidate.compute(0.0, 0.0);
            long snap = initial.levels().get(initial.levels().size() - 1)
                    .cellSpacingBlocks();
            assertTrue(initial.effectiveInnerRadiusBlocks() > 0L);
            assertTrue(initial.effectiveInnerRadiusBlocks() + snap / 2L
                            <= initial.nearRadiusBlocks(),
                    "Blank near/far gap possible at " + chunks + " chunks");
            GeometryClipmapLayout.Layout moved =
                    candidate.compute(snap / 2.0 - 0.1, 0.0, initial);
            assertEquals(initial.levels().get(0).centerX(),
                    moved.levels().get(0).centerX());
        }
        assertThrows(IllegalArgumentException.class,
                () -> new GeometryClipmapLayout(256, 6, 8, 8));
    }


    @Test
    void rangePresetsScaleTheOuterExtentAndSampleGrid() {
        GeometryClipmapLayout.Layout shortRange =
                GeometryClipmapLayout.forRenderDistanceChunks(10, 64).compute(0.0, 0.0);
        GeometryClipmapLayout.Layout mediumRange =
                GeometryClipmapLayout.forRenderDistanceChunks(10, 128).compute(0.0, 0.0);
        GeometryClipmapLayout.Layout longRange =
                GeometryClipmapLayout.forRenderDistanceChunks(10, 256).compute(0.0, 0.0);

        assertEquals(64, shortRange.gridCells());
        assertEquals(128, mediumRange.gridCells());
        assertEquals(256, longRange.gridCells());
        assertEquals(4096L, shortRange.maxAxisExtentBlocks());
        assertEquals(8192L, mediumRange.maxAxisExtentBlocks());
        assertEquals(16384L, longRange.maxAxisExtentBlocks());
    }

    @Test
    void rangePresetAutomaticallyExpandsIfVanillaNearDistanceNeedsMoreCoverage() {
        GeometryClipmapLayout shortRequest =
                GeometryClipmapLayout.forRenderDistanceChunks(12, 64);
        GeometryClipmapLayout mediumRequest =
                GeometryClipmapLayout.forRenderDistanceChunks(12, 128);

        assertEquals(128, shortRequest.gridCells());
        assertEquals(128, mediumRequest.gridCells());
        assertEquals(8192L, shortRequest.compute(0.0, 0.0).maxAxisExtentBlocks());
    }

    @Test
    void allRangePresetsPreserveTheVanillaNearFieldForEverySupportedViewDistance() {
        for (int chunks = 2; chunks <= 64; chunks++) {
            for (int requestedGrid : new int[]{64, 128, 256}) {
                GeometryClipmapLayout planner =
                        GeometryClipmapLayout.forRenderDistanceChunks(chunks, requestedGrid);
                GeometryClipmapLayout.Layout layout = planner.compute(0.0, 0.0);
                long snap = layout.levels().get(layout.levels().size() - 1)
                        .cellSpacingBlocks();
                assertTrue(layout.effectiveInnerRadiusBlocks() + snap / 2L
                                <= layout.nearRadiusBlocks(),
                        "Blank near/far gap possible at " + chunks
                                + " chunks and grid " + requestedGrid);
                assertTrue(layout.gridCells() >= requestedGrid);
            }
        }
    }

    @Test
    void rejectsIncompatiblePreviousLayoutAndInvalidGrid() {
        GeometryClipmapLayout.Layout before = planner.compute(0.0, 0.0);
        GeometryClipmapLayout other = new GeometryClipmapLayout(256, 5, 4, 10);
        assertThrows(IllegalArgumentException.class,
                () -> other.compute(64.0, 0.0, before));
        assertThrows(IllegalArgumentException.class,
                () -> new GeometryClipmapLayout(100, 6, 4, 10));
    }
}
