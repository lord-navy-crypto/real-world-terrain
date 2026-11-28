package org.realscale.neoforge.client.clipmap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ClipmapProxySnapshotBufferTest {
    private static ClipmapSnapshotAssembler.SnapshotKey key(
            long requestId, long centerX, long centerZ) {
        return new ClipmapSnapshotAssembler.SnapshotKey(
                requestId, "minecraft:overworld", 10, 128, 8, centerX, centerZ);
    }

    @Test
    void proxiesArrivingBeforeHeightLayersAppearOnlyWhenSnapshotCompletes() {
        var buffer = new ClipmapProxySnapshotBuffer<String>();
        var target = key(4, 256, -512);
        buffer.accept(4, "minecraft:overworld", 256, -512, "tree", null);
        assertNull(buffer.activeFor(target));
        buffer.onHeightSnapshot(target);
        assertEquals("tree", buffer.activeFor(target));
    }

    @Test
    void centreChangesNeverShowStaleExteriorProxies() {
        var buffer = new ClipmapProxySnapshotBuffer<String>();
        var first = key(4, 0, 0);
        var second = key(4, 256, 0);
        buffer.accept(4, "minecraft:overworld", 0, 0, "first", first);
        assertEquals("first", buffer.activeFor(first));
        buffer.onHeightSnapshot(second);
        assertNull(buffer.activeFor(second));
        buffer.accept(4, "minecraft:overworld", 256, 0, "second", second);
        assertEquals("second", buffer.activeFor(second));
        assertNull(buffer.activeFor(first));
    }

    @Test
    void oldRequestOrDimensionCannotAttachToNewHeightGeneration() {
        var buffer = new ClipmapProxySnapshotBuffer<String>();
        var current = key(5, 0, 0);
        buffer.accept(4, "minecraft:overworld", 0, 0, "obsolete", null);
        buffer.onHeightSnapshot(current);
        assertNull(buffer.activeFor(current));
        buffer.accept(5, "minecraft:the_nether", 0, 0, "wrong", current);
        assertNull(buffer.activeFor(current));
        buffer.clear();
        assertNull(buffer.activeFor(current));
    }

    @Test
    void rejectsInvalidMetadataAndNullPayloads() {
        var buffer = new ClipmapProxySnapshotBuffer<String>();
        assertThrows(IllegalArgumentException.class,
                () -> buffer.accept(0, "minecraft:overworld", 0, 0, "x", null));
        assertThrows(IllegalArgumentException.class,
                () -> buffer.accept(1, "", 0, 0, "x", null));
        assertThrows(NullPointerException.class,
                () -> buffer.accept(1, "minecraft:overworld", 0, 0, null, null));
    }
}
