package org.realscale.neoforge.lotm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class LotmAlchemyBalanceTest {
    @Test
    void selectsConfiguredSequenceNineAndEightProcessingTimes() {
        assertEquals(120, LotmBalance.alchemyTicks(9, 120, 240));
        assertEquals(240, LotmBalance.alchemyTicks(8, 120, 240));
    }

    @Test
    void rejectsArchivedAndOutOfScopeRanks() {
        assertEquals(0, LotmBalance.alchemyTicks(7, 120, 240));
        assertEquals(0, LotmBalance.alchemyTicks(6, 120, 240));
        assertEquals(0, LotmBalance.alchemyTicks(10, 120, 240));
    }
}
