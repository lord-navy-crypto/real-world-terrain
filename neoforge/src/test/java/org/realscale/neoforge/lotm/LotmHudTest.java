package org.realscale.neoforge.lotm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LotmHudTest {
    @Test
    void actionbarRefreshIsOptInAndRunsEveryFortyTicks() {
        assertTrue(LotmHud.shouldRender(40, true, true, true));
        assertTrue(LotmHud.shouldRender(80, true, true, true));
        assertFalse(LotmHud.shouldRender(39, true, true, true));
        assertFalse(LotmHud.shouldRender(40, false, true, true));
        assertFalse(LotmHud.shouldRender(40, true, false, true));
        assertFalse(LotmHud.shouldRender(40, true, true, false));
    }

    @Test
    void negativeTickValuesUseStableFloorModCadence() {
        assertTrue(LotmHud.shouldRender(-40, true, true, true));
        assertFalse(LotmHud.shouldRender(-39, true, true, true));
    }
}
