package org.realscale.neoforge.client.clipmap;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class VisibleSurfaceProxyTest {
    @Test
    void retainsCompactPrimitiveExteriorProxyData() {
        VisibleSurfaceProxy proxy = new VisibleSurfaceProxy(
                100, 64, -200, 5.0f, 12.0f, 0xFF123456,
                VisibleSurfaceProxy.TREE_CANOPY);
        assertEquals(100, proxy.x());
        assertEquals(64, proxy.y());
        assertEquals(-200, proxy.z());
        assertEquals(5.0f, proxy.radius());
        assertEquals(12.0f, proxy.height());
        assertEquals(0x00123456, proxy.rgb());
        assertEquals(VisibleSurfaceProxy.TREE_CANOPY, proxy.type());
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
