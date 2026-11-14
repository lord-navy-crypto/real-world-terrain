package org.realscale.neoforge.lotm;

/** Pure habitat and time-of-day checks shared by spawn predicates and unit tests. */
public final class LotmCreatureSpawnRules {
    private static final long DAY_LENGTH_TICKS = 24_000L;
    private static final long NIGHT_START_TICK = 13_000L;
    private static final long NIGHT_END_TICK = 23_000L;

    private LotmCreatureSpawnRules() {}

    /**
     * Returns true during the vanilla Overworld night interval.
     * Absolute world time is normalized so values beyond one day are handled safely.
     */
    public static boolean isNocturnalActive(long dayTime) {
        long timeOfDay = Math.floorMod(dayTime, DAY_LENGTH_TICKS);
        return timeOfDay >= NIGHT_START_TICK && timeOfDay < NIGHT_END_TICK;
    }

    /**
     * Returns true only when the creature's declared habitat and time-of-day rules match.
     * Non-nocturnal species remain eligible throughout the full day.
     */
    public static boolean canSpawnAt(LotmCreatureKind kind, String biomeId, long dayTime) {
        return canSpawnInBiome(kind, biomeId)
                && (!kind.isNocturnal() || isNocturnalActive(dayTime));
    }

    /**
     * Returns true only for a known biome explicitly listed for the creature.
     * Null, blank, and unknown IDs fail closed.
     */
    public static boolean canSpawnInBiome(LotmCreatureKind kind, String biomeId) {
        return kind != null && biomeId != null && !biomeId.isBlank()
                && kind.biomes().contains(biomeId);
    }
}
