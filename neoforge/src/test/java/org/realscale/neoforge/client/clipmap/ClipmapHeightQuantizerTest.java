package org.realscale.neoforge.client.clipmap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ClipmapHeightQuantizerTest {
    @Test
    void roundTripsWorldHeightWithinHalfAnUnsignedSixteenBitStep() {
        double min = -64.0;
        double max = 2032.0;
        double input = 847.375;
        int encoded = ClipmapHeightQuantizer.encode(input, min, max);
        double decoded = ClipmapHeightQuantizer.decode(encoded, min, max);
        double halfStep = (max - min) / (2.0 * 65_535.0);

        assertTrue(Math.abs(decoded - input) <= halfStep + 1.0e-12);
    }

    @Test
    void clampsHeightsToConfiguredTextureRange() {
        assertEquals(0, ClipmapHeightQuantizer.encode(-100.0, -64.0, 2032.0));
        assertEquals(65_535, ClipmapHeightQuantizer.encode(3000.0, -64.0, 2032.0));
    }

    @Test
    void rejectsInvalidRangesAndEncodedValues() {
        assertThrows(IllegalArgumentException.class,
                () -> ClipmapHeightQuantizer.encode(1.0, 2.0, 2.0));
        assertThrows(IllegalArgumentException.class,
                () -> ClipmapHeightQuantizer.decode(65_536, -64.0, 2032.0));
    }
}
