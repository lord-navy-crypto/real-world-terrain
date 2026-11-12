package org.realscale.neoforge.lotm;

/** Pure habitat checks shared by natural-spawn predicates and unit tests. */
public final class LotmCreatureSpawnRules {
    private LotmCreatureSpawnRules() {}

    /**
     * Returns true only for a known biome explicitly listed for the creature.
     * Null, blank, and unknown IDs fail closed.
     */
    public static boolean canSpawnInBiome(LotmCreatureKind kind, String biomeId) {
        return kind != null && biomeId != null && !biomeId.isBlank()
                && kind.biomes().contains(biomeId);
    }
}
