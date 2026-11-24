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
    void texturedTintIsWorldAnchoredAndVariesAcrossRegionalNoise() {
        int first = ClipmapSurfacePalette.texturedTintAt(
                100.0f, 1.0f, 63.0f, false, 128L, 256L);
        int repeat = ClipmapSurfacePalette.texturedTintAt(
                100.0f, 1.0f, 63.0f, false, 128L, 256L);
        int distant = ClipmapSurfacePalette.texturedTintAt(
                100.0f, 1.0f, 63.0f, false, 2048L, 4096L);
        assertEquals(first, repeat);
        assertNotEquals(first, distant);
        assertTrue(((first >>> 16) & 0xFF) >= 150);
        assertTrue(((first >>> 8) & 0xFF) >= 150);
    }

    @Test
    void textureTintPreservesTheAuthoredBlockPalette() {
        int tint = ClipmapSurfacePalette.texturedTintAt(
                100.0f, 1.0f, 63.0f, false, 128L, 256L);
        assertTrue(((tint >>> 16) & 0xFF) >= 210,
                "red multiplier must not brown out authored block textures");
        assertTrue(((tint >>> 8) & 0xFF) >= 210,
                "green multiplier must preserve vegetation colors");
        assertTrue((tint & 0xFF) >= 210,
                "blue multiplier must preserve cool materials");
    }

    @Test
    void textureMaterialSelectionFollowsSlopeBeachAndSnowRegions() {
        assertEquals(ClipmapSurfacePalette.MATERIAL_GRASS,
                ClipmapSurfacePalette.materialAt(100.0f, 1.0f, 63.0f));
        assertEquals(ClipmapSurfacePalette.MATERIAL_SAND,
                ClipmapSurfacePalette.materialAt(64.0f, 1.0f, 63.0f));
        assertEquals(ClipmapSurfacePalette.MATERIAL_ROCK,
                ClipmapSurfacePalette.materialAt(300.0f, 0.4f, 63.0f));
        assertEquals(ClipmapSurfacePalette.MATERIAL_SNOW,
                ClipmapSurfacePalette.materialAt(1500.0f, 1.0f, 63.0f));
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
