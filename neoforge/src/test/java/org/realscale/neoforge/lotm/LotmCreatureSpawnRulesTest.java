package org.realscale.neoforge.lotm;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LotmCreatureSpawnRulesTest {
    @Test
    void rosterContainsExactlyTheTenActiveSequenceNineAndEightCreatures() {
        assertEquals(List.of(
                "starlit_squid", "nightmare_eye", "night_owl", "black_spotted_frog", "shroudman",
                "hornacis_goat", "ironarm_beast", "four_eyed_turtle", "crimson_howler", "death_crow"),
                Arrays.stream(LotmCreatureKind.values()).map(LotmCreatureKind::id).toList());
        assertEquals(5, Arrays.stream(LotmCreatureKind.values()).filter(k -> k.sequence() == 9).count());
        assertEquals(5, Arrays.stream(LotmCreatureKind.values()).filter(k -> k.sequence() == 8).count());
        assertTrue(Arrays.stream(LotmCreatureKind.values()).allMatch(k ->
                k.dropId() != null && !k.dropId().isBlank()));
    }

    @Test
    void intendedHabitatsAreAcceptedAndUnrelatedHabitatsAreRejected() {
        assertTrue(LotmCreatureSpawnRules.canSpawnInBiome(
                LotmCreatureKind.STARLIT_SQUID, "minecraft:deep_ocean"));
        assertTrue(LotmCreatureSpawnRules.canSpawnInBiome(
                LotmCreatureKind.NIGHTMARE_EYE, "minecraft:dark_forest"));
        assertTrue(LotmCreatureSpawnRules.canSpawnInBiome(
                LotmCreatureKind.NIGHT_OWL, "minecraft:taiga"));
        assertTrue(LotmCreatureSpawnRules.canSpawnInBiome(
                LotmCreatureKind.BLACK_SPOTTED_FROG, "minecraft:swamp"));
        assertTrue(LotmCreatureSpawnRules.canSpawnInBiome(
                LotmCreatureKind.SHROUDMAN, "minecraft:mangrove_swamp"));
        assertTrue(LotmCreatureSpawnRules.canSpawnInBiome(
                LotmCreatureKind.HORNACIS_GOAT, "minecraft:stony_peaks"));
        assertTrue(LotmCreatureSpawnRules.canSpawnInBiome(
                LotmCreatureKind.IRONARM_BEAST, "minecraft:desert"));
        assertTrue(LotmCreatureSpawnRules.canSpawnInBiome(
                LotmCreatureKind.FOUR_EYED_TURTLE, "minecraft:beach"));
        assertTrue(LotmCreatureSpawnRules.canSpawnInBiome(
                LotmCreatureKind.CRIMSON_HOWLER, "minecraft:taiga"));
        assertTrue(LotmCreatureSpawnRules.canSpawnInBiome(
                LotmCreatureKind.DEATH_CROW, "minecraft:jungle"));

        for (LotmCreatureKind kind : LotmCreatureKind.values()) {
            assertFalse(LotmCreatureSpawnRules.canSpawnInBiome(kind, "minecraft:desert"),
                    kind.id() + " must not spawn in a desert by default");
        }
    }

    @Test
    void invalidInputsFailClosed() {
        assertFalse(LotmCreatureSpawnRules.canSpawnInBiome(null, "minecraft:forest"));
        assertFalse(LotmCreatureSpawnRules.canSpawnInBiome(
                LotmCreatureKind.NIGHT_OWL, null));
        assertFalse(LotmCreatureSpawnRules.canSpawnInBiome(
                LotmCreatureKind.NIGHT_OWL, ""));
        assertFalse(LotmCreatureSpawnRules.canSpawnInBiome(
                LotmCreatureKind.NIGHT_OWL, "minecraft:unknown"));
    }
}
