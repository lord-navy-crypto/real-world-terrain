package org.realscale.neoforge.network;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ClipmapRequestPayloadTest {
    @Test
    void acceptsPositiveRequestGenerationAndSupportedDistance() {
        var payload = new ClipmapRequestPayload(10, 42L);
        assertEquals(10, payload.nearRenderDistanceChunks());
        assertEquals(42L, payload.requestId());
    }

    @Test
    void rejectsUnsupportedDistanceAndNonPositiveRequestGeneration() {
        assertThrows(IllegalArgumentException.class,
                () -> new ClipmapRequestPayload(1, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> new ClipmapRequestPayload(65, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> new ClipmapRequestPayload(10, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new ClipmapRequestPayload(10, -1L));
    }
}
