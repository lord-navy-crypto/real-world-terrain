package org.realscale.neoforge.client.clipmap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ClipmapSurfacePaletteTest {
    @Test
    void flatLowlandUsesStableGrassWithoutTriangleSpecificNoise() {
        int first = ClipmapSurfacePalette.colorAt(100.0f, 1.0f, 63.0f, false);
        int second = ClipmapSurfacePalette.colorAt(100.0f, 1.0f, 63.0f, false);
        assertEquals(0x789052, first);
        assertEquals(first, second);
    }

    @Test
    void shorelineMaterialTransitionIsContinuous() {
        int before = ClipmapSurfacePalette.colorAt(75.0f, 1.0f, 63.0f, false);
        int after = ClipmapSurfacePalette.colorAt(76.0f, 1.0f, 63.0f, false);
        assertChannelDeltaAtMost(before, after, 5);
    }

    @Test
    void steepSlopeRockTransitionIsContinuous() {
        float normalBefore = 1.0f - 0.355f;
        float normalAfter = 1.0f - 0.356f;
        int before = ClipmapSurfacePalette.colorAt(300.0f, normalBefore, 63.0f, false);
        int after = ClipmapSurfacePalette.colorAt(300.0f, normalAfter, 63.0f, false);
        assertChannelDeltaAtMost(before, after, 2);
    }

    @Test
    void snowlineTransitionIsContinuous() {
        int before = ClipmapSurfacePalette.colorAt(800.0f, 1.0f, 63.0f, false);
        int after = ClipmapSurfacePalette.colorAt(801.0f, 1.0f, 63.0f, false);
        assertChannelDeltaAtMost(before, after, 2);
    }

    @Test
    void seamSkirtsRemainRockColoredAndWaterHasOneStableColor() {
        assertEquals(0x85857F,
                ClipmapSurfacePalette.colorAt(1200.0f, 1.0f, 63.0f, true));
        assertEquals(0x64869A, ClipmapSurfacePalette.waterColor());
    }

    @Test
    void rejectsNonFinitePaletteInputs() {
        assertThrows(IllegalArgumentException.class, () ->
                ClipmapSurfacePalette.colorAt(Float.NaN, 1.0f, 63.0f, false));
    }

    private static void assertChannelDeltaAtMost(int first, int second, int maximum) {
        assertTrue(Math.abs(((first >>> 16) & 0xFF) - ((second >>> 16) & 0xFF)) <= maximum);
        assertTrue(Math.abs(((first >>> 8) & 0xFF) - ((second >>> 8) & 0xFF)) <= maximum);
        assertTrue(Math.abs((first & 0xFF) - (second & 0xFF)) <= maximum);
    }
}
