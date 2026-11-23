package org.realscale.neoforge.client.clipmap;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CancellationException;
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
        assertEquals(16_592, grid.sampledVertexCount());
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
        // (256, 0) enters the new inner hole when the shared centre moves.
        assertTrue(first.isSampledAt(96, 64));
        assertFalse(second.isSampledAt(64, 64));

        // (-128, 400) remains in both visible rings and must be reused without resampling.
        assertEquals(-128L, first.originX() + 48L * first.cellSpacingBlocks());
        assertEquals(400L, first.originZ() + 114L * first.cellSpacingBlocks());
        assertEquals(-128L, second.originX() + 16L * second.cellSpacingBlocks());
        assertEquals(400L, second.originZ() + 114L * second.cellSpacingBlocks());

        float expectedHeight = first.heightAt(48, 114);
        float actualHeight = second.heightAt(16, 114);
        assertEquals(expectedHeight, actualHeight, 0.0f,
                () -> "The same world vertex must retain its exact sampled height: expected "
                        + expectedHeight + ", actual " + actualHeight);
    }

    @Test
    void cancelsDuringAHeavyRingInsteadOfWaitingForTheNextLevel() {
        GeometryClipmapLayout.Level level = planner.compute(0.0, 0.0).levels().get(0);
        AtomicInteger checks = new AtomicInteger();
        AtomicInteger samples = new AtomicInteger();
        assertThrows(CancellationException.class,
                () -> ClipmapHeightGrid.sample(level, 128,
                        (x, z) -> {
                            samples.incrementAndGet();
                            return 40.0f;
                        }, null, () -> checks.incrementAndGet() < 4));
        assertEquals(4, checks.get());
        assertTrue(samples.get() > 0);
        assertTrue(samples.get() < 129 * 129,
                "A superseded request must not finish a full obsolete ring");
    }

    @Test
    void rejectsNonFiniteHeightSamplesAndMismatchedGridDimensions() {
        GeometryClipmapLayout.Level level = planner.compute(0.0, 0.0).levels().get(0);
        assertThrows(IllegalArgumentException.class,
                () -> ClipmapHeightGrid.sample(level, 128, (x, z) -> Float.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> ClipmapHeightGrid.sample(level, 64, (x, z) -> 0.0f));
    }
    @Test
    void reportsMonotonicProgressThroughTheWholeRing() {
        GeometryClipmapLayout.Level level = planner.compute(0.0, 0.0).levels().get(0);
        AtomicInteger previous = new AtomicInteger(-1);
        AtomicInteger callbackCount = new AtomicInteger();
        AtomicInteger finalPercent = new AtomicInteger(-1);
        ClipmapHeightGrid.sample(level, 128, (x, z) -> 42.0f, null,
                () -> true, percent -> {
                    assertTrue(percent >= previous.get(),
                            "progress must never move backwards");
                    assertTrue(percent >= 0 && percent <= 100);
                    previous.set(percent);
                    finalPercent.set(percent);
                    callbackCount.incrementAndGet();
                });
        assertTrue(callbackCount.get() >= 10,
                "the first LOD ring should report multiple in-ring progress updates");
        assertEquals(100, finalPercent.get());
    }

}
