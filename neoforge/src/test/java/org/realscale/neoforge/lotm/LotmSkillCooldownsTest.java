package org.realscale.neoforge.lotm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pure checks for save rollback and corrupted cooldown display timestamps. */
class LotmSkillCooldownsTest {
    @Test void acceptedSkillsShowRemainingTimeWithoutNegativeValues() {
        assertEquals(160, LotmSkillCooldowns.boundedRemaining(1160L, 1000L, 160));
        assertEquals(60, LotmSkillCooldowns.boundedRemaining(1160L, 1100L, 160));
        assertEquals(0, LotmSkillCooldowns.boundedRemaining(1160L, 1160L, 160));
        assertEquals(0, LotmSkillCooldowns.boundedRemaining(1160L, 1200L, 160));
    }

    @Test void staleAndMalformedCooldownsFailClosed() {
        assertEquals(0, LotmSkillCooldowns.boundedRemaining(1160L, 500L, 160));
        assertEquals(0, LotmSkillCooldowns.boundedRemaining(Long.MAX_VALUE, 500L, 160));
        assertEquals(0, LotmSkillCooldowns.boundedRemaining(-1L, 0L, 160));
        assertEquals(0, LotmSkillCooldowns.boundedRemaining(1160L, -1L, 160));
        assertEquals(0, LotmSkillCooldowns.boundedRemaining(1160L, 1000L, 0));
        assertEquals(0, LotmSkillCooldowns.boundedRemaining(1160L, 1000L, -2));
    }

    @Test void sequenceSpecificMaximumIsRespected() {
        assertEquals(135, LotmSkillCooldowns.boundedRemaining(1135L, 1000L, 135));
        assertEquals(0, LotmSkillCooldowns.boundedRemaining(1160L, 1000L, 135));
        assertEquals(180, LotmSkillCooldowns.boundedRemaining(1180L, 1000L, 180));
        assertEquals(0, LotmSkillCooldowns.boundedRemaining(1181L, 1000L, 180));
    }
}
