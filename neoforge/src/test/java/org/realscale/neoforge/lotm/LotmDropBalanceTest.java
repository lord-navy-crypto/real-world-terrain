package org.realscale.neoforge.lotm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class LotmDropBalanceTest {
    @Test void higherSequenceMaterialsAreRarer() {
        assertEquals(40, LotmDropBalance.effectiveChance(40, 9));
        assertEquals(26, LotmDropBalance.effectiveChance(40, 8));
        assertEquals(0, LotmDropBalance.effectiveChance(0, 8));
        assertEquals(65, LotmDropBalance.effectiveChance(100, 8));
    }

    @Test void boundedDropRollIsDeterministic() {
        assertTrue(LotmDropBalance.roll(40, 9, 39));
        assertFalse(LotmDropBalance.roll(40, 9, 40));
        assertTrue(LotmDropBalance.roll(40, 8, 25));
        assertFalse(LotmDropBalance.roll(40, 8, 26));
        assertFalse(LotmDropBalance.roll(40, 8, -1));
        assertFalse(LotmDropBalance.roll(40, 8, 100));
        assertEquals(100, LotmDropBalance.effectiveChance(300, 9));
        assertEquals(0, LotmDropBalance.effectiveChance(-20, 9));
    }
}
