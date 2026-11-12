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
    void behaviorProfilesAreNotAccidentallyShared() {
        Set<LotmCreatureBehavior> profiles = EnumSet.noneOf(LotmCreatureBehavior.class);
        for (LotmCreatureKind kind : LotmCreatureKind.values()) {
            profiles.add(LotmCreatureBehavior.forKind(kind));
        }
        assertEquals(LotmCreatureKind.values().length, profiles.size());
    }
}
