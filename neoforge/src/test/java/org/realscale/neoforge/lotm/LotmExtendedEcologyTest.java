package org.realscale.neoforge.lotm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LotmExtendedEcologyTest {
    @Test void everySequenceEightAndSevenCreatureHasAnObtainableMaterial() {
        assertEquals("hornacis_goat", LotmEcologyRules.pickVariant(
                "goat", "minecraft:stony_peaks", 28));
        assertEquals("ironarm_beast", LotmEcologyRules.pickVariant(
                "husk", "minecraft:desert", 38));
        assertEquals("four_eyed_turtle", LotmEcologyRules.pickVariant(
                "turtle", "minecraft:beach", 25));
        assertEquals("crimson_howler", LotmEcologyRules.pickVariant(
                "wolf", "minecraft:taiga", 38));
        assertEquals("mist_treant", LotmEcologyRules.pickVariant(
                "zombie", "minecraft:forest", 54));
        assertEquals("evil_pattern_panther", LotmEcologyRules.pickVariant(
                "ocelot", "minecraft:jungle", 30));
        assertEquals("stag_beast", LotmEcologyRules.pickVariant(
                "cow", "minecraft:forest", 45));
        assertEquals("dream_crow", LotmEcologyRules.pickVariant(
                "phantom", "minecraft:plains", 38));
        assertEquals("nightmare_shadow", LotmEcologyRules.pickVariant(
                "enderman", "minecraft:dark_forest", 45));
        assertEquals("spectral_cat", LotmEcologyRules.pickVariant(
                "cat", "minecraft:plains", 36));
        assertEquals("death_crow", LotmEcologyRules.pickVariant(
                "parrot", "minecraft:jungle", 45));
        assertEquals("grey_grandmother", LotmEcologyRules.pickVariant(
                "parrot", "minecraft:jungle", 47));
    }

    @Test void spawnRateIsTunableAndDropsAreExplicit() {
        assertNull(LotmEcologyRules.pickVariant(
                "goat", "minecraft:stony_peaks", 28, 50));
        assertEquals("hornacis_goat", LotmEcologyRules.pickVariant(
                "goat", "minecraft:stony_peaks", 28, 200));
        assertEquals("hornacis_goat_horn", LotmEcologyRules.DROPS.get("hornacis_goat"));
        assertEquals("spectral_cat_paw", LotmEcologyRules.DROPS.get("spectral_cat"));
        assertNull(LotmEcologyRules.pickVariant(
                "husk", "minecraft:deep_ocean", 0));
    }
}
