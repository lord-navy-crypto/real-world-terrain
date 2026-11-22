package org.realscale.neoforge.client.clipmap;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

final class ClipmapHeightGridTest {
    private final GeometryClipmapLayout planner = new GeometryClipmapLayout();

    @Test
    void samplesRingVerticesDirectlyWithoutCreatingChunks() {
        GeometryClipmapLayout.Level level = planner.compute(0.0, 0.0).levels().get(0);
        AtomicInteger calls = new AtomicInteger();

        ClipmapHeightGrid grid = ClipmapHeightGrid.sample(level, 128,
                (x, z) -> {
                    calls.incrementAndGet();
                    return x * 0.01f + z * 0.02f;
                });

        assertEquals(calls.get(), grid.sampledVertexCount());
        assertEquals(10_400, grid.sampledVertexCount());
        assertTrue(grid.sampledVertexCount() < 129 * 129);
        assertEquals(-256L, grid.originX());
        assertEquals(-256L, grid.originZ());
        assertEquals(4, grid.cellSpacingBlocks());
        assertEquals(-252.0f * 0.01f + -248.0f * 0.02f,
                grid.heightAt(1, 2), 1.0e-5f);
    }

    @Test
    void skipsInteriorAlreadyCoveredByTheFinerLevel() {
        GeometryClipmapLayout.Level level = planner.compute(0.0, 0.0).levels().get(0);
        ClipmapHeightGrid grid = ClipmapHeightGrid.sample(level, 128,
                (x, z) -> 123.0f);

        // Grid coordinate (64,64) is at the camera and belongs to the
        // vanilla near field, so this ring intentionally has no sample there.
        assertFalse(grid.isSampledAt(64, 64));
        assertTrue(Float.isNaN(grid.copyHeights()[64 * 129 + 64]));
        assertThrows(IllegalStateException.class,
                () -> grid.heightAt(64, 64));
        assertTrue(grid.isSampledAt(0, 0));
    }

    @Test
    void rejectsNonFiniteHeightSamples() {
        GeometryClipmapLayout.Level level = planner.compute(0.0, 0.0).levels().get(0);
        assertThrows(IllegalArgumentException.class,
                () -> ClipmapHeightGrid.sample(level, 128, (x, z) -> Float.NaN));
    }

    @Test
    void rejectsMismatchedGridDimensions() {
        GeometryClipmapLayout.Level level = planner.compute(0.0, 0.0).levels().get(0);
        assertThrows(IllegalArgumentException.class,
                () -> ClipmapHeightGrid.sample(level, 64, (x, z) -> 0.0f));
    }
}
