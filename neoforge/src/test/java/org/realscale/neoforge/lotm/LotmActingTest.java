package org.realscale.neoforge.lotm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LotmActingTest {
    @Test void dailyActingCategoriesHaveAStableThirtyFivePointCap() {
        assertEquals(12, LotmActing.dailyCap("exploration"));
        assertEquals(8, LotmActing.dailyCap("skill"));
        assertEquals(9, LotmActing.dailyCap("research"));
        assertEquals(6, LotmActing.dailyCap("fieldwork"));
        assertEquals(35, LotmActing.dailyCap("exploration")
                + LotmActing.dailyCap("skill")
                + LotmActing.dailyCap("research")
                + LotmActing.dailyCap("fieldwork"));
        assertEquals(0, LotmActing.dailyCap("unknown"));
    }
}
