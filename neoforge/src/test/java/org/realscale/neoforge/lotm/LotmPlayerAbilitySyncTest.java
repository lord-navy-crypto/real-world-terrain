package org.realscale.neoforge.lotm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LotmPlayerAbilitySyncTest {
    @Test
    void everyPathwayHasTwoDistinctSequenceGatedAbilities() {
        assertEquals(8, LotmPlayerAbilityRegistry.all().size());
        assertEquals(8, new java.util.HashSet<>(LotmPlayerAbilityRegistry.all()).size());
        for (LotmPath path : LotmPath.values()) {
            LotmPathwayAbility primary = LotmPlayerAbilityRegistry.primary(path);
            LotmPathwayAbility secondary = LotmPlayerAbilityRegistry.secondary(path);
            assertNotSame(primary, secondary);
            assertSame(path, primary.pathway());
            assertSame(path, secondary.pathway());
            assertEquals(1, primary.slot());
            assertEquals(2, secondary.slot());
            assertEquals("realscale:lotm_" + path.id(), primary.getCategory().toString());
            assertEquals(2, primary.getMaxLevel());
            assertEquals(160, primary.getCooldownTicks(1));
            assertEquals(135, primary.getCooldownTicks(2));
            assertEquals(220, secondary.getCooldownTicks(1));
            assertEquals(180, secondary.getCooldownTicks(2));
            assertEquals(0, primary.getCooldownTicks(3));
        }
    }

    @Test
    void fallbackAuditRunsOncePerTwentyPlayerTicks() {
        for (int tick = 0; tick < 200; tick++) {
            assertEquals(tick % 20 == 0,
                    LotmPlayerAbilitySync.shouldAuditUnauthorizedGrants(tick), "tick " + tick);
        }
    }

    @Test
    void auditCadenceHandlesNegativeAndLargeTickValuesDeterministically() {
        assertTrue(LotmPlayerAbilitySync.shouldAuditUnauthorizedGrants(-20));
        assertFalse(LotmPlayerAbilitySync.shouldAuditUnauthorizedGrants(-19));
        assertTrue(LotmPlayerAbilitySync.shouldAuditUnauthorizedGrants(Integer.MAX_VALUE - 7));
    }
}
