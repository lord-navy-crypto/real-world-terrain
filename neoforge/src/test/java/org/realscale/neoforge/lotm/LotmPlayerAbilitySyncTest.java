package org.realscale.neoforge.lotm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LotmPlayerAbilitySyncTest {
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
