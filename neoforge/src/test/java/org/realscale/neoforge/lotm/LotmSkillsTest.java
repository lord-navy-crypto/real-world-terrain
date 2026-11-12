package org.realscale.neoforge.lotm;

import net.minecraft.world.entity.EntityType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LotmSkillsTest {
    @Test
    void usesVanillaUndeadEntityTypeTagForClassification() {
        assertTrue(LotmSkills.isUndeadType(EntityType.ZOMBIE));
        assertTrue(LotmSkills.isUndeadType(EntityType.SKELETON));
        assertTrue(LotmSkills.isUndeadType(EntityType.PHANTOM));
        assertTrue(LotmSkills.isUndeadType(EntityType.WITHER));
        assertFalse(LotmSkills.isUndeadType(EntityType.COW));
        assertFalse(LotmSkills.isUndeadType(EntityType.CREEPER));
        assertFalse(LotmSkills.isUndeadType(null));
    }
}
