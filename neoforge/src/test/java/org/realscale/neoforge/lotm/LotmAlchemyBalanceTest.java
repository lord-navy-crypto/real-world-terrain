package org.realscale.neoforge.lotm;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class LotmAlchemyBalanceTest {
    @Test
    void selectsConfiguredSequenceNineAndEightProcessingTimes() {
        assertEquals(120, LotmBalance.alchemyTicks(9, 120, 240));
        assertEquals(240, LotmBalance.alchemyTicks(8, 120, 240));
    }

    @Test
    void emptyOutputRejectsInvalidOrOverstackedRecipeResults() {
        assertTrue(LotmBalance.canFitAlchemyOutput(true, false, 0, 1, 1, 1));
        assertFalse(LotmBalance.canFitAlchemyOutput(true, false, 0, 1, 2, 1));
        assertFalse(LotmBalance.canFitAlchemyOutput(true, false, 0, 0, 1, 0));
        assertFalse(LotmBalance.canFitAlchemyOutput(true, false, 0, 1, 0, 1));
    }

    @Test
    void occupiedOutputRequiresMatchingComponentsAndAvailableCapacity() {
        assertTrue(LotmBalance.canFitAlchemyOutput(false, true, 1, 16, 1, 16));
        assertTrue(LotmBalance.canFitAlchemyOutput(false, true, 15, 16, 1, 16));
        assertFalse(LotmBalance.canFitAlchemyOutput(false, true, 16, 16, 1, 16));
        assertFalse(LotmBalance.canFitAlchemyOutput(false, false, 1, 16, 1, 16));
        assertFalse(LotmBalance.canFitAlchemyOutput(false, true, 0, 16, 1, 16));
    }

    @Test
    void outputCountAdditionCannotOverflow() {
        assertFalse(LotmBalance.canFitAlchemyOutput(false, true,
                Integer.MAX_VALUE - 1, Integer.MAX_VALUE, 10, 64));
        assertFalse(LotmBalance.canFitAlchemyOutput(false, true,
                1, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    @Test
    void rejectsArchivedAndOutOfScopeRanks() {
        assertEquals(0, LotmBalance.alchemyTicks(7, 120, 240));
        assertEquals(0, LotmBalance.alchemyTicks(6, 120, 240));
        assertEquals(0, LotmBalance.alchemyTicks(10, 120, 240));
    }
}
