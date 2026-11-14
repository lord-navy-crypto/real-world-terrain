package org.realscale.neoforge.lotm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LotmSkillsTest {
    @Test
    void nullEntityTypeIsRejectedBeforeVanillaTagLookup() {
        assertFalse(LotmSkills.isUndeadType(null));
    }
}
