package org.realscale.neoforge.lotm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LotmProgressionTest {
    @Test void allFourPathsStartAtSequenceNine() {
        for (LotmPath path : LotmPath.values()) {
            assertEquals("ready", LotmProgression.validate(null, -1, path.id(),
                    9, 0, false, 70, 90));
            assertEquals("sequence", LotmProgression.validate(null, -1, path.id(),
                    8, 100, true, 70, 90));
        }
    }

    @Test void everyPathCanAdvanceNineToEightButSequenceSevenIsArchived() {
        for (LotmPath path : LotmPath.values()) {
            assertEquals("ready", LotmProgression.validate(path.id(), 9,
                    path.id(), 8, 70, false, 70, 90));
            assertEquals("outside_scope", LotmProgression.validate(path.id(), 8,
                    path.id(), 7, 100, false, 70, 90));
            assertEquals("outside_scope", LotmProgression.validate(path.id(), 7,
                    path.id(), 6, 100, false, 70, 90));
            assertEquals("outside_scope", LotmProgression.validate(path.id(), 9,
                    path.id(), 7, 100, false, 70, 90));
        }
    }

    @Test void directSequenceEightCharacteristicNeedsHigherDigestion() {
        assertEquals("digestion", LotmProgression.validate("seer", 9, "seer", 8,
                70, true, 70, 90));
        assertEquals("ready", LotmProgression.validate("seer", 9, "seer", 8,
                90, true, 70, 90));
        assertEquals("digestion", LotmProgression.validate("seer", 9, "seer", 8,
                65, false, 70, 90));
    }

    @Test void firstSequenceNineCharacteristicCanEstablishAPath() {
        assertEquals("ready", LotmProgression.validate(null, -1, "seer",
                9, 0, true, 70, 90));
    }

    @Test void preparedPotionAndDirectCharacteristicUseExactConfiguredThresholdEdges() {
        assertEquals("digestion", LotmProgression.validate("seer", 9, "seer", 8,
                69, false, 70, 90));
        assertEquals("ready", LotmProgression.validate("seer", 9, "seer", 8,
                70, false, 70, 90));
        assertEquals("digestion", LotmProgression.validate("seer", 9, "seer", 8,
                89, true, 70, 90));
        assertEquals("ready", LotmProgression.validate("seer", 9, "seer", 8,
                90, true, 70, 90));
    }

    @Test void sequenceCapNeverFallsBelowEight() {
        assertEquals(8, LotmProgression.nextSequence(9));
        assertEquals(8, LotmProgression.nextSequence(8));
        assertEquals(8, LotmProgression.nextSequence(7));
    }

    @Test void cannotChangePathWithForeignCharacteristic() {
        assertEquals("path", LotmProgression.validate("sleepless", 9, "seer",
                8, 100, true, 70, 90));
    }
}
