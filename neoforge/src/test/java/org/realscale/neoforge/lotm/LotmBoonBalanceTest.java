package org.realscale.neoforge.lotm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LotmBoonBalanceTest {
    @Test void taintIsBoundedAndSaturatesWithoutOverflow() {
        assertEquals(0, LotmBoonBalance.clampTaint(-10));
        assertEquals(0, LotmBoonBalance.clampTaint(0));
        assertEquals(100, LotmBoonBalance.clampTaint(100));
        assertEquals(100, LotmBoonBalance.clampTaint(Integer.MAX_VALUE));
        assertEquals(0, LotmBoonBalance.addTaint(5, -20));
        assertEquals(100, LotmBoonBalance.addTaint(95, 50));
        assertEquals(16, LotmBoonBalance.addTaint(12, 4));
    }

    @Test void boonActivationIsSeparateAndHasClearSafetyThresholds() {
        assertTrue(LotmBoonBalance.canActivate(9, 0, false, 100));
        assertTrue(LotmBoonBalance.canActivate(8, 20, false, 80));
        assertFalse(LotmBoonBalance.canActivate(7, 0, false, 100));
        assertFalse(LotmBoonBalance.canActivate(9, 96, false, 100));
        assertFalse(LotmBoonBalance.canActivate(9, 20, true, 100));
        assertFalse(LotmBoonBalance.canActivate(8, 20, false, 9));
    }

    @Test void sequenceSpecificInsightCostsArePredictable() {
        assertEquals(5, LotmBoonBalance.insightTaintCost(9));
        assertEquals(8, LotmBoonBalance.insightTaintCost(8));
        assertEquals(0, LotmBoonBalance.insightTaintCost(7));
        assertEquals(2, LotmBoonBalance.insightSanityCost(9));
        assertEquals(3, LotmBoonBalance.insightSanityCost(8));
        assertEquals(0, LotmBoonBalance.insightSanityCost(7));
        assertEquals(200, LotmBoonBalance.INSIGHT_COOLDOWN_TICKS);
    }

    @Test void sequenceEightRequiresExplorationSanityAndLowTaint() {
        assertTrue(LotmBoonBalance.canAdvanceToSequence8(3, 50, 40));
        assertTrue(LotmBoonBalance.canAdvanceToSequence8(5, 100, 0));
        assertFalse(LotmBoonBalance.canAdvanceToSequence8(2, 100, 0));
        assertFalse(LotmBoonBalance.canAdvanceToSequence8(3, 49, 0));
        assertFalse(LotmBoonBalance.canAdvanceToSequence8(3, 100, 41));
    }
}
