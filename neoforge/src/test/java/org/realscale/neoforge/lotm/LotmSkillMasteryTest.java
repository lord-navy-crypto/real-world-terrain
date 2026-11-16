package org.realscale.neoforge.lotm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LotmSkillMasteryTest {
    @Test
    void masteryIsBoundedAndReducesCostWithoutMakingSkillsFree() {
        assertEquals(0, LotmBalance.clampSkillMastery(-10));
        assertEquals(0, LotmBalance.clampSkillMastery(0));
        assertEquals(1, LotmBalance.clampSkillMastery(1));
        assertEquals(2, LotmBalance.clampSkillMastery(99));

        assertEquals(16, LotmBalance.skillCostWithMastery(16, 0, 100));
        assertEquals(15, LotmBalance.skillCostWithMastery(16, 1, 100));
        assertEquals(14, LotmBalance.skillCostWithMastery(16, 2, 100));
        assertEquals(7, LotmBalance.skillCostWithMastery(16, 2, 50));
        assertEquals(32, LotmBalance.skillCostWithMastery(16, 0, 200));
        assertEquals(1, LotmBalance.skillCostWithMastery(1, 2, 25));
    }

    @Test
    void masteryCostScalingClampsConfigurationExtremes() {
        assertEquals(14, LotmBalance.skillCostWithMastery(16, 2, -100));
        assertEquals(32, LotmBalance.skillCostWithMastery(16, 0, 900));
        assertEquals(1, LotmBalance.skillCostWithMastery(0, 99, 25));
    }
}
