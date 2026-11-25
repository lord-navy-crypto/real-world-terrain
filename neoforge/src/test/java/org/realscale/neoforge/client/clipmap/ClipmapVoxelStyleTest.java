package org.realscale.neoforge.client.clipmap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ClipmapVoxelStyleTest {
    @Test
    void nearestRingIsBlockyAcrossTheVanillaOverlap() {
        assertEquals(1.0f, ClipmapVoxelStyle.blend(0, 96.0, 10, 4), 1.0e-6f);
        assertEquals(1.0f, ClipmapVoxelStyle.blend(0, 160.0, 10, 4), 1.0e-6f);
    }

    @Test
    void nearestRingSmoothlyTransitionsBeforeTheCoarseRings() {
        float near = ClipmapVoxelStyle.blend(0, 160.0, 10, 4);
        float middle = ClipmapVoxelStyle.blend(0, 312.0, 10, 4);
        float far = ClipmapVoxelStyle.blend(0, 392.0, 10, 4);
        assertTrue(near > middle);
        assertTrue(middle > far);
        assertEquals(0.5f, middle, 1.0e-5f);
        assertEquals(0.0f, far, 1.0e-6f);
    }

    @Test
    void everyCoarserLodRemainsContinuous() {
        for (int level = 1; level < GeometryClipmapLayout.DEFAULT_LEVEL_COUNT; level++) {
            assertEquals(0.0f,
                    ClipmapVoxelStyle.blend(level, 180.0, 10, 4), 1.0e-6f);
        }
    }

    @Test
    void voxelHeightsSnapToWholeBlockLevels() {
        assertEquals(65.0f, ClipmapVoxelStyle.quantizeHeight(64.6f));
        assertEquals(64.0f, ClipmapVoxelStyle.quantizeHeight(64.4f));
        assertEquals(-64.0f, ClipmapVoxelStyle.quantizeHeight(-63.6f));
    }

    @Test
    void geometryAndColorBlendsHaveExactEndpoints() {
        assertEquals(12.0f, ClipmapVoxelStyle.blendHeight(12.0f, 20.0f, 0.0f));
        assertEquals(20.0f, ClipmapVoxelStyle.blendHeight(12.0f, 20.0f, 1.0f));
        assertEquals(16.0f, ClipmapVoxelStyle.blendHeight(12.0f, 20.0f, 0.5f));
        assertEquals(0xFF0000,
                ClipmapVoxelStyle.blendColor(0x0000FF, 0xFF0000, 1.0f));
        assertEquals(0x0000FF,
                ClipmapVoxelStyle.blendColor(0x0000FF, 0xFF0000, 0.0f));
    }

    @Test
    void invalidTransitionInputsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ClipmapVoxelStyle.blend(6, 100.0, 10, 4));
        assertThrows(IllegalArgumentException.class,
                () -> ClipmapVoxelStyle.blend(0, Double.NaN, 10, 4));
        assertThrows(IllegalArgumentException.class,
                () -> ClipmapVoxelStyle.quantizeHeight(Float.POSITIVE_INFINITY));
    }
}
