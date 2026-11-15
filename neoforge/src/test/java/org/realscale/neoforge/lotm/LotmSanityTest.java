package org.realscale.neoforge.lotm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LotmSanityTest {
    @Test void sanityChangesAreClampedAndOverflowSafe() {
        assertEquals(100, LotmBalance.clampSanity(100));
        assertEquals(100, LotmBalance.clampSanity(Integer.MAX_VALUE));
        assertEquals(0, LotmBalance.clampSanity(Integer.MIN_VALUE));
        assertEquals(100, LotmBalance.changeSanity(95, 50));
        assertEquals(0, LotmBalance.changeSanity(10, -50));
        assertEquals(88, LotmBalance.changeSanity(100, -12));
        assertEquals(100, LotmBalance.changeSanity(0, Integer.MAX_VALUE));
        assertEquals(0, LotmBalance.changeSanity(100, Integer.MIN_VALUE));
    }

    @Test void lowSanityAddsBoundedSkillCostWithoutChangingBaseProgression() {
        assertEquals(0, LotmBalance.sanitySkillSurcharge(100));
        assertEquals(0, LotmBalance.sanitySkillSurcharge(70));
        assertEquals(2, LotmBalance.sanitySkillSurcharge(69));
        assertEquals(2, LotmBalance.sanitySkillSurcharge(40));
        assertEquals(4, LotmBalance.sanitySkillSurcharge(39));
        assertEquals(4, LotmBalance.sanitySkillSurcharge(20));
        assertEquals(8, LotmBalance.sanitySkillSurcharge(19));
        assertEquals(8, LotmBalance.sanitySkillSurcharge(0));
        assertEquals(22, LotmBalance.skillCost("seer", 1, 9, false, 0, 0));
        assertEquals(14, LotmBalance.skillCost("seer", 1, 9, false, 0, 100));
        assertEquals(100, LotmBalance.skillCost("unknown", 1, 9, false, 0, 0));
    }

    @Test void wrongPathAndUndigestedPotionBacklashAreBoundedAndDistinct() {
        assertEquals(12, LotmBalance.potionBacklashSanityLoss(false, 0));
        assertEquals(24, LotmBalance.potionBacklashSanityLoss(true, 0));
        assertTrue(LotmBalance.potionBacklashSanityLoss(true, 100) <= 45);
        assertTrue(LotmBalance.potionBacklashSanityLoss(true, 0)
                > LotmBalance.potionBacklashSanityLoss(false, 0));
        assertEquals(1.5F, LotmBalance.potionBacklashDamage(false, 0));
        assertEquals(3.0F, LotmBalance.potionBacklashDamage(true, 0));
        assertEquals(4.0F, LotmBalance.potionBacklashDamage(true, 100));
    }

    @Test void poorSanityNarrowsDivinationWithoutProducingImpossibleRanges() {
        assertEquals(12, LotmBalance.divinationRadiusForSanity(12, 100));
        assertEquals(10, LotmBalance.divinationRadiusForSanity(12, 69));
        assertEquals(8, LotmBalance.divinationRadiusForSanity(12, 39));
        assertEquals(6, LotmBalance.divinationRadiusForSanity(12, 19));
        assertEquals(3, LotmBalance.divinationRadiusForSanity(3, 0));
        assertEquals(0, LotmBalance.divinationRadiusForSanity(0, 0));
    }
}
