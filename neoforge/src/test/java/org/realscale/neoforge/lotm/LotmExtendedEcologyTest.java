package org.realscale.neoforge.lotm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LotmExtendedEcologyTest {
    @Test void allSequenceEightCreatureMaterialsRemainObtainable() {
        assertEquals("hornacis_goat", LotmEcologyRules.pickVariant(
                "goat", "minecraft:stony_peaks", 28));
        assertEquals("ironarm_beast", LotmEcologyRules.pickVariant(
                "husk", "minecraft:desert", 38));
        assertEquals("four_eyed_turtle", LotmEcologyRules.pickVariant(
                "turtle", "minecraft:beach", 25));
        assertEquals("crimson_howler", LotmEcologyRules.pickVariant(
                "wolf", "minecraft:taiga", 38));
        assertEquals("death_crow", LotmEcologyRules.pickVariant(
                "parrot", "minecraft:jungle", 45));
    }

    @Test void spawnRateIsTunableAndDropsAreExplicit() {
        assertNull(LotmEcologyRules.pickVariant(
                "goat", "minecraft:stony_peaks", 28, 50));
        assertEquals("hornacis_goat", LotmEcologyRules.pickVariant(
                "goat", "minecraft:stony_peaks", 28, 200));
        assertEquals("hornacis_goat_horn", LotmEcologyRules.DROPS.get("hornacis_goat"));
        assertEquals("spectral_cat_paw", LotmEcologyRules.DROPS.get("spectral_cat"));
        assertNull(LotmEcologyRules.pickVariant("husk", "minecraft:deep_ocean", 0));
    }
}
