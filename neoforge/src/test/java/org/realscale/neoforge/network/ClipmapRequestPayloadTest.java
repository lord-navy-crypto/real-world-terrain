package org.realscale.neoforge.network;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ClipmapRequestPayloadTest {
    @Test
    void acceptsPositiveRequestGenerationAndSupportedDistance() {
        var payload = new ClipmapRequestPayload(10, 256, 42L);
        assertEquals(10, payload.nearRenderDistanceChunks());
        assertEquals(256, payload.gridCells());
        assertEquals(42L, payload.requestId());
    }

    @Test
    void rejectsUnsupportedDistanceAndNonPositiveRequestGeneration() {
        assertThrows(IllegalArgumentException.class,
                () -> new ClipmapRequestPayload(1, 256, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> new ClipmapRequestPayload(65, 256, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> new ClipmapRequestPayload(10, 32, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> new ClipmapRequestPayload(10, 512, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> new ClipmapRequestPayload(10, 256, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new ClipmapRequestPayload(10, 256, -1L));
    }
}
