package org.realscale.neoforge.lotm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LotmAppearanceTest {
    @Test
    void auraIsOptInAndUsesTheConfiguredInterval() {
        assertTrue(LotmAppearance.shouldEmit(20, true, true, true, 20));
        assertTrue(LotmAppearance.shouldEmit(40, true, true, true, 20));
        assertFalse(LotmAppearance.shouldEmit(19, true, true, true, 20));
        assertFalse(LotmAppearance.shouldEmit(20, false, true, true, 20));
        assertFalse(LotmAppearance.shouldEmit(20, true, false, true, 20));
        assertFalse(LotmAppearance.shouldEmit(20, true, true, false, 20));
    }

    @Test
    void invalidIntervalsAreSafelyClamped() {
        assertTrue(LotmAppearance.shouldEmit(0, true, true, true, 0));
        assertFalse(LotmAppearance.shouldEmit(1, true, true, true, 0));
    }
}
