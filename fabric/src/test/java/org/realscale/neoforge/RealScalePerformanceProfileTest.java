package org.realscale.neoforge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class RealScalePerformanceProfileTest {
    @Test
    void profileNamesAreCaseInsensitiveAndSupportHyphenatedForms() {
        assertEquals(RealScalePerformanceProfile.Profile.BALANCED,
                RealScalePerformanceProfile.parseProfile(null));
        assertEquals(RealScalePerformanceProfile.Profile.LOW_MEMORY,
                RealScalePerformanceProfile.parseProfile("LOW-MEMORY"));
        assertEquals(RealScalePerformanceProfile.Profile.SMOOTH_RENDER,
                RealScalePerformanceProfile.parseProfile("smooth_render"));
        assertEquals(RealScalePerformanceProfile.Profile.LOW_MEMORY_SMOOTH_RENDER,
                RealScalePerformanceProfile.parseProfile("low-memory-smooth-render"));
    }

    @Test
    void compactProfileReducesBoundedPowerOfTwoCacheCapacities() {
        var balanced = RealScalePerformanceProfile.Profile.BALANCED;
        var compact = RealScalePerformanceProfile.Profile.LOW_MEMORY;
        assertEquals(4096, RealScalePerformanceProfile.cacheSlots(
                balanced, "dem", 4096));
        assertEquals(1024, RealScalePerformanceProfile.cacheSlots(
                compact, "dem", 4096));
        assertEquals(2048, RealScalePerformanceProfile.cacheSlots(
                compact, "measured", 8192));
        assertEquals(512, RealScalePerformanceProfile.cacheSlots(
                compact, "biome_result", 2048));
        assertEquals(16, RealScalePerformanceProfile.cacheSlots(
                compact, "mountain", 64));
        assertEquals(64, RealScalePerformanceProfile.cacheSlots(
                compact, "sky", 256));
        assertEquals(256, RealScalePerformanceProfile.cacheSlots(
                compact, "archetype_horizontal", 1024));
        assertEquals(256, RealScalePerformanceProfile.cacheSlots(
                compact, "archetype_volcanic", 1024));
        assertEquals(256, RealScalePerformanceProfile.cacheSlots(
                compact, "archetype_karst", 1024));
        assertThrows(IllegalArgumentException.class, () ->
                RealScalePerformanceProfile.cacheSlots(compact, "invalid", 12));
    }

    @Test
    void compactCacheArrayEstimateIsLowerWithoutChangingBalancedEstimate() {
        long balanced = RealScalePerformanceProfile.estimatedCacheArrayBytesOneSet(
                RealScalePerformanceProfile.Profile.BALANCED);
        long compact = RealScalePerformanceProfile.estimatedCacheArrayBytesOneSet(
                RealScalePerformanceProfile.Profile.LOW_MEMORY);
        assertEquals(551_680L, balanced);
        assertEquals(137_992L, compact);
        assertTrue(compact < balanced);
    }

    @Test
    void smoothRenderIsExplicitAndIndependentFromCacheProfile() {
        assertFalse(RealScalePerformanceProfile.usesSmoothRender(
                RealScalePerformanceProfile.Profile.BALANCED));
        assertFalse(RealScalePerformanceProfile.usesSmoothRender(
                RealScalePerformanceProfile.Profile.LOW_MEMORY));
        assertTrue(RealScalePerformanceProfile.usesSmoothRender(
                RealScalePerformanceProfile.Profile.SMOOTH_RENDER));
        assertTrue(RealScalePerformanceProfile.usesSmoothRender(
                RealScalePerformanceProfile.Profile.LOW_MEMORY_SMOOTH_RENDER));
    }
}
