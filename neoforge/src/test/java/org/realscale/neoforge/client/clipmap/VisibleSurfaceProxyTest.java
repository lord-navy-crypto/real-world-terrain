package org.realscale.neoforge.client.clipmap;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class VisibleSurfaceProxyTest {
    @Test
    void computesAndRetainsImmutableWorldBoundsOnce() {
        VisibleSurfaceProxy proxy = new VisibleSurfaceProxy(
                100, 64, -200, 5.0f, 12.0f, 0xFF123456,
                VisibleSurfaceProxy.TREE_CANOPY);
        assertSame(proxy.bounds(), proxy.bounds());
        assertEquals(95.0, proxy.bounds().minX());
        assertEquals(64.0, proxy.bounds().minY());
        assertEquals(-205.0, proxy.bounds().minZ());
        assertEquals(105.0, proxy.bounds().maxX());
        assertEquals(76.0, proxy.bounds().maxY());
        assertEquals(-195.0, proxy.bounds().maxZ());
    }

    @Test
    void preservesRecordValueSemanticsAndRgbMasking() {
        VisibleSurfaceProxy first = new VisibleSurfaceProxy(
                1, 2, 3, 4.0f, 5.0f, 0xFFABCDEF,
                VisibleSurfaceProxy.BUILDING_EXTERIOR);
        VisibleSurfaceProxy second = new VisibleSurfaceProxy(
                1, 2, 3, 4.0f, 5.0f, 0x00ABCDEF,
                VisibleSurfaceProxy.BUILDING_EXTERIOR);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals(0x00ABCDEF, first.rgb());
    }

    @Test
    void rejectsInvalidProxyDimensionsAndTypes() {
        assertThrows(IllegalArgumentException.class,
                () -> new VisibleSurfaceProxy(0, 0, 0, 0.0f, 1.0f, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new VisibleSurfaceProxy(0, 0, 0, 1.0f, Float.NaN, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new VisibleSurfaceProxy(0, 0, 0, 1.0f, 1.0f, 0, 5));
    }
}
