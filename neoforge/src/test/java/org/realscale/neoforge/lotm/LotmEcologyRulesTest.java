package org.realscale.neoforge.lotm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LotmEcologyRulesTest {
    @Test void activeSequenceNineAndEightVariantsUseTheirIntendedHabitats() {
        assertEquals("nightmare_eye", LotmEcologyRules.pickVariant(
                "spider", "realscale:oak_to_dark_forest", 32));
        assertEquals("black_spotted_frog", LotmEcologyRules.pickVariant(
                "frog", "realscale:marshy_woodland", 40));
        assertEquals("shroudman", LotmEcologyRules.pickVariant(
                "zombie", "realscale:seasonal_floodforest", 36));
        assertEquals("starlit_squid", LotmEcologyRules.pickVariant(
                "squid", "minecraft:deep_ocean", 24));
        assertEquals("hornacis_goat", LotmEcologyRules.pickVariant(
                "goat", "minecraft:stony_peaks", 28));
    }

    @Test void archivedSequenceSevenVariantsCannotSpawn() {
        assertEquals(10, LotmEcologyRules.ACTIVE_VARIANTS.size());
        assertEquals(7, LotmEcologyRules.ARCHIVED_SEQUENCE7_VARIANTS.size());
        assertNull(LotmEcologyRules.pickVariant("zombie", "minecraft:forest", 54));
        assertNull(LotmEcologyRules.pickVariant("ocelot", "minecraft:jungle", 30));
        assertNull(LotmEcologyRules.pickVariant("cow", "minecraft:forest", 45));
        assertNull(LotmEcologyRules.pickVariant("parrot", "minecraft:jungle", 47));
        assertNull(LotmEcologyRules.pickVariant("phantom", "minecraft:plains", 38));
        assertNull(LotmEcologyRules.pickVariant("enderman", "minecraft:dark_forest", 45));
        assertNull(LotmEcologyRules.pickVariant("cat", "minecraft:plains", 36));
    }

    @Test void incompatibleHabitatsNeverGenerateOccultVariants() {
        assertNull(LotmEcologyRules.pickVariant("squid", "minecraft:desert", 0));
        assertNull(LotmEcologyRules.pickVariant("zombie", "minecraft:nether_wastes", 0));
        assertNull(LotmEcologyRules.pickVariant("frog", "minecraft:plains", 0));
        assertNull(LotmEcologyRules.pickVariant("chicken", "minecraft:swamp", 0));
        assertNull(LotmEcologyRules.pickVariant(null, "minecraft:swamp", 0));
        assertNull(LotmEcologyRules.pickVariant("frog", null, 0));
        assertNull(LotmEcologyRules.pickVariant("frog", "minecraft:swamp", -1));
    }

    @Test void chanceGateRequiresValidRoll() {
        assertNull(LotmEcologyRules.pickVariant("spider", "minecraft:dark_forest", 1));
        assertEquals("night_owl", LotmEcologyRules.pickVariant("bat", "minecraft:taiga", 48));
        assertNull(LotmEcologyRules.pickVariant("bat", "minecraft:taiga", 47));
    }
}
