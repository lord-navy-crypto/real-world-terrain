package org.realscale.neoforge.client.clipmap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ClipmapFogColorTest {
    @Test
    void preservesFiniteMinecraftFogChannelsWithinNormalizedRange() {
        assertEquals(0.2f, ClipmapFogColor.channel(new float[]{0.2f, 0.4f, 0.8f}, 0, 0.0f));
        assertEquals(1.0f, ClipmapFogColor.channel(new float[]{1.4f, 0.4f, 0.8f}, 0, 0.0f));
        assertEquals(0.0f, ClipmapFogColor.channel(new float[]{-0.2f, 0.4f, 0.8f}, 0, 0.5f));
    }

    @Test
    void fallsBackForMissingOrNonFiniteFogChannels() {
        assertEquals(0.58f, ClipmapFogColor.channel(null, 0, 0.58f));
        assertEquals(0.69f, ClipmapFogColor.channel(new float[]{0.2f}, 1, 0.69f));
        assertEquals(0.80f, ClipmapFogColor.channel(
                new float[]{0.2f, 0.4f, Float.NaN}, 2, 0.80f));
    }
}
