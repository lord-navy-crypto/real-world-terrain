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
    @Test
    void dailyCountersClampMalformedPersistentValuesToTheirCategoryCap() {
        assertEquals(0, LotmActing.clampDailyUsed(-1, 12));
        assertEquals(0, LotmActing.clampDailyUsed(Integer.MIN_VALUE, 12));
        assertEquals(12, LotmActing.clampDailyUsed(100, 12));
        assertEquals(8, LotmActing.clampDailyUsed(Integer.MAX_VALUE, 8));
    }

    @Test
    void scaledActingRewardsUseLongArithmeticAndRespectRemainingCapacity() {
        assertEquals(12, LotmActing.scaledReward(Integer.MAX_VALUE, 300, 12));
        assertEquals(1, LotmActing.scaledReward(2, 50, 3));
        assertEquals(0, LotmActing.scaledReward(1, 50, 3));
        assertEquals(0, LotmActing.scaledReward(Integer.MAX_VALUE, 300, 0));
        assertEquals(0, LotmActing.scaledReward(-1, 300, 12));
    }

    @Test
    void serverActivityCyclesIgnoreSleepAndDaylightTimeManipulation() {
        assertEquals(0L, LotmActing.activityDay(0L));
        assertEquals(0L, LotmActing.activityDay(23_999L));
        assertEquals(1L, LotmActing.activityDay(24_000L));
        assertEquals(2L, LotmActing.activityDay(48_123L));
    }

}
