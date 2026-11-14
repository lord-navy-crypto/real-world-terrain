package org.realscale.neoforge.lotm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LotmBalanceTest {
    @Test void spiritualityCannotUnderflowOrOverflow() {
        assertEquals(100, LotmBalance.regenerate(99, 1000));
        assertEquals(0, LotmBalance.clamp(-200, LotmBalance.MAX_SPIRIT));
        assertEquals(100, LotmBalance.regenerate(200, 0));
        assertEquals(0, LotmBalance.regenerate(-5, 0));
    }

    @Test void practiceIsBoundedAndDoesNotGrantHigherSequences() {
        assertEquals(100, LotmBalance.practice(97, 12));
        assertEquals(0, LotmBalance.practice(0, -10));
        assertEquals(100, LotmBalance.practice(400, 0));
    }

    @Test void costsReflectDifferentSensoryPathways() {
        assertEquals(14, LotmBalance.focusCost("seer"));
        assertEquals(18, LotmBalance.focusCost("mystery_pryer"));
        assertEquals(10, LotmBalance.focusCost("sleepless"));
        assertEquals(12, LotmBalance.focusCost("corpse_collector"));
        assertEquals(100, LotmBalance.focusCost("unrecognized"));
    }

    @Test void skillCostsAndCooldownsAreBoundedByPathAndSequence() {
        assertEquals(14, LotmBalance.skillCost("seer", 1, 9));
        assertEquals(19, LotmBalance.skillCost("seer", 1, 8));
        assertEquals(18, LotmBalance.skillCost("seer", 2, 9));
        assertEquals(22, LotmBalance.skillCost("seer", 2, 8));
        assertEquals(16, LotmBalance.skillCost("mystery_pryer", 2, 9));
        assertEquals(100, LotmBalance.skillCost("unknown", 1, 9));
        assertEquals(100, LotmBalance.skillCost("seer", 3, 9));
        assertEquals(160, LotmBalance.skillCooldownTicks(1, 9));
        assertEquals(135, LotmBalance.skillCooldownTicks(1, 8));
        assertEquals(220, LotmBalance.skillCooldownTicks(2, 9));
        assertEquals(180, LotmBalance.skillCooldownTicks(2, 8));
    }

    @Test void bearingUsesCardinalAndVerticalDirections() {
        assertEquals("north", LotmBalance.bearing(2, -4, 0));
        assertEquals("east", LotmBalance.bearing(8, 1, 0));
        assertEquals("west", LotmBalance.bearing(-2, 1, 0));
        assertEquals("south", LotmBalance.bearing(0, 3, 0));
        assertEquals("up", LotmBalance.bearing(0, 1, 8));
        assertEquals("down", LotmBalance.bearing(0, 2, -8));
        assertEquals("near", LotmBalance.bearing(0, 0, 0));
    }
    @Test void spiritFocusImprovesOnlyConfiguredRecoveryIntervals() {
        assertEquals(2, LotmBalance.spiritRecovery(2, false));
        assertEquals(3, LotmBalance.spiritRecovery(2, true));
        assertEquals(0, LotmBalance.spiritRecovery(-5, false));
        assertEquals(1, LotmBalance.spiritRecovery(-5, true));
    }

    @Test void divinationPendulumOnlyDiscountsSeerSecondarySkill() {
        assertEquals(18, LotmBalance.skillCost("seer", 2, 9, false));
        assertEquals(14, LotmBalance.skillCost("seer", 2, 9, true));
        assertEquals(22, LotmBalance.skillCost("seer", 2, 8, false));
        assertEquals(18, LotmBalance.skillCost("seer", 2, 8, true));
        assertEquals(16, LotmBalance.skillCost("mystery_pryer", 2, 9, true));
        assertEquals(14, LotmBalance.skillCost("seer", 1, 9, true));
    }

    @Test void divinationPendulumAddsOnlyAModestLocalOmenRadius() {
        assertEquals(10, LotmBalance.divinationRadius(9, false));
        assertEquals(12, LotmBalance.divinationRadius(9, true));
        assertEquals(12, LotmBalance.divinationRadius(8, false));
        assertEquals(14, LotmBalance.divinationRadius(8, true));
        assertEquals(0, LotmBalance.divinationRadius(7, true));
    }

    @Test void directCharacteristicInstabilityAddsACappedReversibleSkillSurcharge() {
        assertEquals(0, LotmBalance.instabilitySurcharge(-1));
        assertEquals(0, LotmBalance.instabilitySurcharge(0));
        assertEquals(2, LotmBalance.instabilitySurcharge(1));
        assertEquals(4, LotmBalance.instabilitySurcharge(2));
        assertEquals(8, LotmBalance.instabilitySurcharge(20));
        assertEquals(20, LotmBalance.skillCost("seer", 1, 9, false, 3));
        assertEquals(18, LotmBalance.skillCost("seer", 2, 9, true, 1));
    }

}
