package org.realscale.neoforge.lotm;

import java.util.Objects;

/** One explicit signature behavior per active creature; no archived Sequence 7 entries. */
public enum LotmCreatureBehavior {
    INK_CLOAK,
    DISORIENTING_GAZE,
    NOCTURNAL_EVASION,
    SPRING_ESCAPE,
    MIST_GRIP,
    HIGHLAND_COUNTERCHARGE,
    ARMORED_BRUTE,
    FOUR_EYED_GUARD,
    PACK_HOWL,
    DIVE_HUNTER;

    public static LotmCreatureBehavior forKind(LotmCreatureKind kind) {
        Objects.requireNonNull(kind, "kind");
        return switch (kind) {
            case STARLIT_SQUID -> INK_CLOAK;
            case NIGHTMARE_EYE -> DISORIENTING_GAZE;
            case NIGHT_OWL -> NOCTURNAL_EVASION;
            case BLACK_SPOTTED_FROG -> SPRING_ESCAPE;
            case SHROUDMAN -> MIST_GRIP;
            case HORNACIS_GOAT -> HIGHLAND_COUNTERCHARGE;
            case IRONARM_BEAST -> ARMORED_BRUTE;
            case FOUR_EYED_TURTLE -> FOUR_EYED_GUARD;
            case CRIMSON_HOWLER -> PACK_HOWL;
            case DEATH_CROW -> DIVE_HUNTER;
        };
    }
}
