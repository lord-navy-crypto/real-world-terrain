package org.realscale.neoforge.network;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ClipmapStatusPayloadTest {
    @Test
    void acceptsAllDefinedServerStatesAndProgress() {
        for (int state = ClipmapStatusPayload.REQUEST_ACCEPTED;
                state <= ClipmapStatusPayload.STALLED; state++) {
            var payload = new ClipmapStatusPayload("minecraft:overworld",
                    42L, state, state == ClipmapStatusPayload.SAMPLING ? 3 : 0,
                    ClipmapStatusPayload.LEVEL_COUNT, 2, "status");
            assertEquals(42L, payload.requestId());
            assertEquals(state, payload.state());
        }
    }

    @Test
    void rejectsInvalidRequestStateProgressAndText() {
        assertThrows(IllegalArgumentException.class, () ->
                new ClipmapStatusPayload("minecraft:overworld", 0L, 0, 0, 6, 0, ""));
        assertThrows(IllegalArgumentException.class, () ->
                new ClipmapStatusPayload("minecraft:overworld", 1L, 8, 0, 6, 0, ""));
        assertThrows(IllegalArgumentException.class, () ->
                new ClipmapStatusPayload("minecraft:overworld", 1L, 0, 7, 6, 0, ""));
        assertThrows(IllegalArgumentException.class, () ->
                new ClipmapStatusPayload("minecraft:overworld", 1L, 0, 0, 5, 0, ""));
        assertThrows(IllegalArgumentException.class, () ->
                new ClipmapStatusPayload("minecraft:overworld", 1L, 0, 0, 6, 1025, ""));
        assertThrows(IllegalArgumentException.class, () ->
                new ClipmapStatusPayload("minecraft:overworld", 1L, 0, 0, 6, 0, "x".repeat(257)));
    }
}
