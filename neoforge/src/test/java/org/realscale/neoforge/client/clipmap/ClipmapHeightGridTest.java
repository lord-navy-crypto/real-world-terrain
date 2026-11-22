package org.realscale.neoforge.client.clipmap;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

final class ClipmapHeightGridTest {
    private final GeometryClipmapLayout planner = new GeometryClipmapLayout();

    @Test
    void samplesOnlyVerticesOutsideTheFinerWindow() {
        GeometryClipmapLayout.Level level = planner.compute(0.0, 0.0).levels().get(0);
        AtomicInteger calls = new AtomicInteger();

        ClipmapHeightGrid grid = ClipmapHeightGrid.sample(level, 128,
                (x, z) -> {
                    calls.incrementAndGet();
                    return x * 0.01f + z * 0.02f;
                });

        assertEquals(calls.get(), grid.sampledVertexCount());
        assertEquals(grid.sampledVertexCount(), grid.newlySampledVertexCount());
        assertEquals(11_600, grid.sampledVertexCount());
        assertTrue(grid.sampledVertexCount() < 129 * 129);
        assertEquals(-512L, grid.originX());
        assertEquals(-512L, grid.originZ());
        assertEquals(8, grid.cellSpacingBlocks());
        assertEquals(-504.0f * 0.01f + -496.0f * 0.02f,
                grid.heightAt(1, 2), 1.0e-5f);
    }

    @Test
    void skipsInteriorAlreadyCoveredByTheFinerLevel() {
        GeometryClipmapLayout.Level level = planner.compute(0.0, 0.0).levels().get(0);
        ClipmapHeightGrid grid = ClipmapHeightGrid.sample(level, 128,
                (x, z) -> 123.0f);

        assertFalse(grid.isSampledAt(64, 64));
        assertTrue(Float.isNaN(grid.copyHeights()[64 * 129 + 64]));
        assertThrows(IllegalStateException.class, () -> grid.heightAt(64, 64));
        assertTrue(grid.isSampledAt(0, 0));
    }

    @Test
    void reusesUnchangedWorldVerticesAfterClipmapRecenters() {
        GeometryClipmapLayout.Layout before = planner.compute(0.0, 0.0);
        AtomicInteger firstCalls = new AtomicInteger();
        ClipmapHeightGrid first = ClipmapHeightGrid.sample(before.levels().get(0),
                128, (x, z) -> {
                    firstCalls.incrementAndGet();
                    return x * 0.01f + z * 0.02f;
                });

        GeometryClipmapLayout.Layout after = planner.compute(128.1, 0.0, before);
        AtomicInteger secondCalls = new AtomicInteger();
        ClipmapHeightGrid second = ClipmapHeightGrid.sample(after.levels().get(0),
                128, (x, z) -> {
                    secondCalls.incrementAndGet();
                    return x * 0.01f + z * 0.02f;
                }, first);

        assertEquals(second.newlySampledVertexCount(), secondCalls.get());
        assertTrue(secondCalls.get() < firstCalls.get());
        assertTrue(second.sampledVertexCount() > second.newlySampledVertexCount());
        // (400, 400) moves into the finer level's covered interior after recentering.
        assertTrue(first.isSampledAt(114, 114));
        assertFalse(second.isSampledAt(82, 82));

        // (-128, 400) remains in both visible rings and must be reused without resampling.
        assertEquals(-128L, first.originX() + 48L * first.cellSpacingBlocks());
        assertEquals(400L, first.originZ() + 114L * first.cellSpacingBlocks());
        assertEquals(-128L, second.originX() + 16L * second.cellSpacingBlocks());
        assertEquals(400L, second.originZ() + 82L * second.cellSpacingBlocks());

        float expectedHeight = first.heightAt(48, 114);
        float actualHeight = second.heightAt(16, 82);
        assertEquals(expectedHeight, actualHeight, 0.0f,
                () -> "The same world vertex must retain its exact sampled height: expected "
                        + expectedHeight + ", actual " + actualHeight);
    }

    @Test
    void rejectsNonFiniteHeightSamplesAndMismatchedGridDimensions() {
        GeometryClipmapLayout.Level level = planner.compute(0.0, 0.0).levels().get(0);
        assertThrows(IllegalArgumentException.class,
                () -> ClipmapHeightGrid.sample(level, 128, (x, z) -> Float.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> ClipmapHeightGrid.sample(level, 64, (x, z) -> 0.0f));
    }
}
