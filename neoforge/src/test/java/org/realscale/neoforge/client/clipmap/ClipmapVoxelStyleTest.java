package org.realscale.neoforge.client.clipmap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ClipmapVoxelStyleTest {
    @Test
    void nearestRingIsBlockyAcrossTheVanillaOverlap() {
        assertEquals(0, ClipmapVoxelStyle.transitionLevel(256, 4, 10));
        assertEquals(0, ClipmapVoxelStyle.transitionLevel(128, 4, 10));
        assertEquals(1, ClipmapVoxelStyle.transitionLevel(64, 4, 10));
        assertEquals(-1, ClipmapVoxelStyle.transitionLevel(64, 8, 32));
        assertEquals(-1, ClipmapVoxelStyle.transitionLevel(128, 16, 64));
        assertEquals(0.0f,
                ClipmapVoxelStyle.blend(0, 160.0, 10, 4, 4, 256), 1.0e-6f);
        assertEquals(0.0f,
                ClipmapVoxelStyle.blend(0, 220.0, 10, 4, 4, 256), 1.0e-6f);
        assertEquals(1.0f,
                ClipmapVoxelStyle.blend(0, 240.0, 10, 4, 4, 256), 1.0e-6f);
    }

    @Test
    void nearestRingSmoothlyTransitionsBeforeTheCoarseRings() {
        float near = ClipmapVoxelStyle.blend(0, 240.0, 10, 4, 4, 256);
        float middle = ClipmapVoxelStyle.blend(0, 316.0, 10, 4, 4, 256);
        float far = ClipmapVoxelStyle.blend(0, 392.0, 10, 4, 4, 256);
        assertTrue(near > middle);
        assertTrue(middle > far);
        assertEquals(0.5f, middle, 1.0e-5f);
        assertEquals(0.0f, far, 1.0e-6f);
    }

    @Test
    void onlySelectedNearRingUsesVoxelStyle() {
        assertEquals(0.0f,
                ClipmapVoxelStyle.blend(1, 180.0, 10, 4, 8, 256), 1.0e-6f);
        assertEquals(0.0f,
                ClipmapVoxelStyle.blend(1, 200.0, 10, 4, 8, 64), 1.0e-6f);
        assertEquals(1.0f,
                ClipmapVoxelStyle.blend(1, 244.0, 10, 4, 8, 64), 1.0e-6f);
        assertEquals(0.0f,
                ClipmapVoxelStyle.blend(2, 260.0, 10, 4, 16, 64), 1.0e-6f);
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
                () -> ClipmapVoxelStyle.blend(6, 100.0, 10, 4, 4, 256));
        assertThrows(IllegalArgumentException.class,
                () -> ClipmapVoxelStyle.blend(0, Double.NaN, 10, 4, 4, 256));
        assertThrows(IllegalArgumentException.class,
                () -> ClipmapVoxelStyle.quantizeHeight(Float.POSITIVE_INFINITY));
    }
}
