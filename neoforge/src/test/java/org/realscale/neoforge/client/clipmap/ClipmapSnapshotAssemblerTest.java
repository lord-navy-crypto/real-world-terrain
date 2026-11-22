package org.realscale.neoforge.client.clipmap;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ClipmapSnapshotAssemblerTest {
    private static void add(ClipmapSnapshotAssembler<String> a,
            int level, long centerX, long centerZ, String marker) {
        int spacing = 8 << level;
        assertTrue(level >= 0 && level < 6);
        a.accept(level, 128, spacing, centerX - 64L * spacing,
                centerZ - 64L * spacing, 10, "minecraft:overworld", marker);
    }

    @Test
    void neverPublishesAnIncompleteOrMixedOriginFrame() {
        var a = new ClipmapSnapshotAssembler<String>(6);
        for (int level = 0; level < 5; level++) {
            add(a, level, -256, 512, "old-" + level);
            assertTrue(a.activeTiles().isEmpty());
        }
        add(a, 5, -256, 512, "old-5");
        assertEquals(6, a.activeTiles().size());
        assertEquals("old-0", a.activeTiles().get(0));
        assertEquals(-256, a.activeKey().centerX());
        assertEquals(512, a.activeKey().centerZ());

        for (int level = 0; level < 5; level++) {
            add(a, level, 1024, 512, "new-" + level);
            assertEquals("old-0", a.activeTiles().get(0));
            assertEquals(-256, a.activeKey().centerX());
        }
        add(a, 5, 1024, 512, "new-5");
        assertEquals("new-0", a.activeTiles().get(0));
        assertEquals(1024, a.activeKey().centerX());
        assertEquals("new-5", a.activeTiles().get(5));
    }

    @Test
    void resetsStagedTilesOnMismatchedCenterDimensionOrDistance() {
        var a = new ClipmapSnapshotAssembler<String>(6);
        add(a, 0, 0, 0, "first");
        add(a, 1, 256, 0, "other");
        for (int level = 2; level < 6; level++) {
            add(a, level, 256, 0, "other");
        }
        assertTrue(a.activeTiles().isEmpty());
        add(a, 0, 256, 0, "other");
        assertEquals(6, a.activeTiles().size());

        a.clear();
        add(a, 0, 0, 0, "tile");
        a.accept(1, 128, 16, -1024, -1024, 11, "minecraft:overworld", "wrong-near");
        assertTrue(a.activeTiles().isEmpty());
        a.clear();
        assertNull(a.activeKey());
    }

    @Test
    void rejectsMalformedSpacingAndInvalidLevel() {
        var a = new ClipmapSnapshotAssembler<String>(6);
        assertThrows(IllegalArgumentException.class,
                () -> a.accept(6, 128, 8, 0, 0, 10, "a", "x"));
        assertThrows(IllegalArgumentException.class,
                () -> a.accept(2, 128, 30, 0, 0, 10, "a", "x"));
        assertThrows(IllegalArgumentException.class,
                () -> a.accept(2, 128, 12, 0, 0, 10, "a", "x"));
        assertThrows(NullPointerException.class,
                () -> a.accept(0, 128, 8, 0, 0, 10, "a", null));
    }
}
