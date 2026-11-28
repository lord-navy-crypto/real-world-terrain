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
    void proceduralTerrainColorIsWorldAnchoredAndVariesAcrossRegionalNoise() {
        int first = ClipmapSurfacePalette.terrainColorAt(
                100.0f, 1.0f, 63.0f, false, 128L, 256L);
        int repeat = ClipmapSurfacePalette.terrainColorAt(
                100.0f, 1.0f, 63.0f, false, 128L, 256L);
        int distant = ClipmapSurfacePalette.terrainColorAt(
                100.0f, 1.0f, 63.0f, false, 2048L, 4096L);
        assertEquals(first, repeat);
        assertNotEquals(first, distant);
        assertTrue((first & 0xFF) < 150,
                "terrain must carry its own color, not a near-white atlas tint");
    }

    @Test
    void proceduralTerrainPaletteSeparatesVegetationRockSandAndSnow() {
        int grass = ClipmapSurfacePalette.terrainColorAt(
                100.0f, 1.0f, 63.0f, false, 128L, 2944L);
        int rock = ClipmapSurfacePalette.terrainColorAt(
                300.0f, 0.35f, 63.0f, false, 128L, 256L);
        int sand = ClipmapSurfacePalette.terrainColorAt(
                64.0f, 1.0f, 63.0f, false, 128L, 256L);
        int snow = ClipmapSurfacePalette.terrainColorAt(
                1500.0f, 1.0f, 63.0f, false, 128L, 256L);

        assertTrue(((grass >>> 8) & 0xFF) > ((grass >>> 16) & 0xFF),
                "vegetation must retain a green-dominant palette");
        assertTrue(Math.abs(((rock >>> 16) & 0xFF) - ((rock >>> 8) & 0xFF)) < 20,
                "steep rock should be neutral stone, not a grass/item texture");
        assertTrue(((sand >>> 16) & 0xFF) > (sand & 0xFF),
                "shoreline sand should retain a warm palette");
        assertTrue(((snow >>> 16) & 0xFF) > 190
                        && ((snow >>> 8) & 0xFF) > 190
                        && (snow & 0xFF) > 190,
                "high-altitude snow should remain bright");
    }

    @Test
    void coarseLodsFilterWorldNoiseBelowTheirSamplingResolution() {
        int fineOrigin = ClipmapSurfacePalette.terrainColorAt(
                100.0f, 1.0f, 63.0f, false, ClipmapBiomeMaterial.GRASSLAND,
                0L, 0L, 4);
        int fineOffset = ClipmapSurfacePalette.terrainColorAt(
                100.0f, 1.0f, 63.0f, false, ClipmapBiomeMaterial.GRASSLAND,
                128L, 128L, 4);
        int coarseOrigin = ClipmapSurfacePalette.terrainColorAt(
                100.0f, 1.0f, 63.0f, false, ClipmapBiomeMaterial.GRASSLAND,
                0L, 0L, 256);
        int coarseOffset = ClipmapSurfacePalette.terrainColorAt(
                100.0f, 1.0f, 63.0f, false, ClipmapBiomeMaterial.GRASSLAND,
                128L, 128L, 256);

        assertNotEquals(fineOrigin, fineOffset,
                "fine LODs should retain representable world-anchored material variation");
        assertEquals(coarseOrigin, coarseOffset,
                "coarse LODs must suppress sub-grid regional and local noise octaves");
    }

    @Test
    void rejectsNonPositivePaletteSampleSpacing() {
        assertThrows(IllegalArgumentException.class, () ->
                ClipmapSurfacePalette.terrainColorAt(
                        100.0f, 1.0f, 63.0f, false,
                        ClipmapBiomeMaterial.GRASSLAND, 0L, 0L, 0));
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
