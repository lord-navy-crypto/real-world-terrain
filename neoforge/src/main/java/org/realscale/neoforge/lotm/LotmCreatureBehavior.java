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

    /**
     * Death Crow dives only from a meaningful height, within a short horizontal
     * strike window, and after its per-entity recovery interval.
     */
    public static boolean shouldDive(int tickCount, int nextDiveTick,
            double verticalDifference, double horizontalDistanceSqr) {
        return tickCount >= nextDiveTick
                && Double.isFinite(verticalDifference) && verticalDifference >= 2.0D
                && Double.isFinite(horizontalDistanceSqr)
                && horizontalDistanceSqr >= 0.0D && horizontalDistanceSqr <= 100.0D;
    }

    /** Passive school/pack cohesion for species that naturally travel together. */
    public static boolean followsSameKind(LotmCreatureKind kind) {
        Objects.requireNonNull(kind, "kind");
        return kind == LotmCreatureKind.STARLIT_SQUID
                || kind == LotmCreatureKind.CRIMSON_HOWLER;
    }

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
