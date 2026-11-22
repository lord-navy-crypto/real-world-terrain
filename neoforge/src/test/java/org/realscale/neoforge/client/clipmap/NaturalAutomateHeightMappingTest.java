package org.realscale.neoforge.client.clipmap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class NaturalAutomateHeightMappingTest {
    @Test
    void zeroHeightSignalMapsExactlyToTheConfiguredSeaLevel() {
        assertEquals(63.0, NaturalAutomateHeightMapping.surfaceY(0.0), 0.0);
        assertEquals(63.0, NaturalAutomateHeightMapping.SEA_LEVEL, 0.0);
    }

    @Test
    void positiveAndNegativeHeightSignalsMapToSurfaceAndBathymetry() {
        assertEquals(191.0, NaturalAutomateHeightMapping.surfaceY(1.0), 0.0);
        assertEquals(-1.0, NaturalAutomateHeightMapping.surfaceY(-0.5), 0.0);
        assertFalse(NaturalAutomateHeightMapping.isOcean(0.25));
        assertTrue(NaturalAutomateHeightMapping.isOcean(-0.25));
    }

    @Test
    void rejectsNonFiniteHeightSignals() {
        assertThrows(IllegalArgumentException.class,
                () -> NaturalAutomateHeightMapping.surfaceY(Double.NaN));
    }
}
