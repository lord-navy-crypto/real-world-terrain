package org.realscale.neoforge.client.clipmap;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ClipmapFogRangeTest {
    @Test
    void defaultSixLevelFogEndsInsideOutermostRing() {
        ClipmapFogRange fog = ClipmapFogRange.forGrid(128, 8, 6);
        assertEquals(16384.0f * 0.62f, fog.startBlocks(), 0.001f);
        assertEquals(16384.0f * 0.98f, fog.endBlocks(), 0.001f);
    }

    @Test
    void viewDistanceChangesDoNotLeaveAVisibleHardHorizon() {
        ClipmapFogRange small = ClipmapFogRange.forGrid(128, 1, 6);
        ClipmapFogRange medium = ClipmapFogRange.forGrid(128, 8, 6);
        ClipmapFogRange large = ClipmapFogRange.forGrid(128, 32, 6);
        assertTrue(small.endBlocks() < medium.startBlocks());
        assertTrue(large.startBlocks() > medium.endBlocks());
        assertEquals(8.0f * small.endBlocks(), medium.endBlocks(), 0.001f);
    }

    @Test
    void partialFirstRingHasItsOwnFogRange() {
        assertEquals(512.0f * 0.98f, ClipmapFogRange.forGrid(128, 8, 1).endBlocks(), 0.001f);
    }

    @Test
    void rejectsInvalidDimensionsAndNonFiniteRanges() {
        assertThrows(IllegalArgumentException.class,
                () -> ClipmapFogRange.forGrid(127, 8, 6));
        assertThrows(IllegalArgumentException.class,
                () -> ClipmapFogRange.forGrid(128, 3, 6));
        assertThrows(IllegalArgumentException.class,
                () -> ClipmapFogRange.forGrid(128, 8, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ClipmapFogRange(Float.NaN, 123.0f));
        assertThrows(IllegalArgumentException.class,
                () -> new ClipmapFogRange(200.0f, 100.0f));
    }
}
