package org.realscale.neoforge.lotm;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LotmCreatureBehaviorTest {
    @Test
    void everyActiveCreatureHasOneDistinctSpecialBehaviorProfile() {
        assertEquals(LotmCreatureBehavior.INK_CLOAK, LotmCreatureBehavior.forKind(LotmCreatureKind.STARLIT_SQUID));
        assertEquals(LotmCreatureBehavior.DISORIENTING_GAZE, LotmCreatureBehavior.forKind(LotmCreatureKind.NIGHTMARE_EYE));
        assertEquals(LotmCreatureBehavior.NOCTURNAL_EVASION, LotmCreatureBehavior.forKind(LotmCreatureKind.NIGHT_OWL));
        assertEquals(LotmCreatureBehavior.SPRING_ESCAPE, LotmCreatureBehavior.forKind(LotmCreatureKind.BLACK_SPOTTED_FROG));
        assertEquals(LotmCreatureBehavior.MIST_GRIP, LotmCreatureBehavior.forKind(LotmCreatureKind.SHROUDMAN));
        assertEquals(LotmCreatureBehavior.HIGHLAND_COUNTERCHARGE, LotmCreatureBehavior.forKind(LotmCreatureKind.HORNACIS_GOAT));
        assertEquals(LotmCreatureBehavior.ARMORED_BRUTE, LotmCreatureBehavior.forKind(LotmCreatureKind.IRONARM_BEAST));
        assertEquals(LotmCreatureBehavior.FOUR_EYED_GUARD, LotmCreatureBehavior.forKind(LotmCreatureKind.FOUR_EYED_TURTLE));
        assertEquals(LotmCreatureBehavior.PACK_HOWL, LotmCreatureBehavior.forKind(LotmCreatureKind.CRIMSON_HOWLER));
        assertEquals(LotmCreatureBehavior.DIVE_HUNTER, LotmCreatureBehavior.forKind(LotmCreatureKind.DEATH_CROW));
    }


    @Test
    void schoolingAndPackCohesionAreLimitedToTheirIntendedSpecies() {
        assertTrue(LotmCreatureBehavior.followsSameKind(LotmCreatureKind.STARLIT_SQUID));
        assertTrue(LotmCreatureBehavior.followsSameKind(LotmCreatureKind.CRIMSON_HOWLER));
        for (LotmCreatureKind kind : LotmCreatureKind.values()) {
            if (kind != LotmCreatureKind.STARLIT_SQUID
                    && kind != LotmCreatureKind.CRIMSON_HOWLER) {
                assertFalse(LotmCreatureBehavior.followsSameKind(kind), kind.id());
            }
        }
    }

    @Test
    void deathCrowDiveRequiresHeightRangeAndCooldown() {
        assertTrue(LotmCreatureBehavior.shouldDive(100, 100, 2.0D, 100.0D));
        assertTrue(LotmCreatureBehavior.shouldDive(100, 99, 8.0D, 16.0D));
        assertFalse(LotmCreatureBehavior.shouldDive(99, 100, 8.0D, 16.0D));
        assertFalse(LotmCreatureBehavior.shouldDive(100, 100, 1.99D, 16.0D));
        assertFalse(LotmCreatureBehavior.shouldDive(100, 100, 8.0D, 100.01D));
        assertFalse(LotmCreatureBehavior.shouldDive(100, 100, Double.NaN, 16.0D));
        assertFalse(LotmCreatureBehavior.shouldDive(100, 100, 8.0D, Double.POSITIVE_INFINITY));
    }

    @Test
    void nocturnalMetadataIsLimitedToTheIntendedFourSpecies() {
        Set<LotmCreatureKind> nocturnal = EnumSet.noneOf(LotmCreatureKind.class);
        for (LotmCreatureKind kind : LotmCreatureKind.values()) {
            if (kind.isNocturnal()) nocturnal.add(kind);
        }
        assertEquals(EnumSet.of(
                LotmCreatureKind.NIGHTMARE_EYE,
                LotmCreatureKind.NIGHT_OWL,
                LotmCreatureKind.SHROUDMAN,
                LotmCreatureKind.DEATH_CROW), nocturnal);
    }

    @Test
    void behaviorProfilesAreNotAccidentallyShared() {
        Set<LotmCreatureBehavior> profiles = EnumSet.noneOf(LotmCreatureBehavior.class);
        for (LotmCreatureKind kind : LotmCreatureKind.values()) {
            profiles.add(LotmCreatureBehavior.forKind(kind));
        }
        assertEquals(LotmCreatureKind.values().length, profiles.size());
    }
}
