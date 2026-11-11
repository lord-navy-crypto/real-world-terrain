package org.realscale.neoforge.lotm;

import java.util.Set;

/** Pure biome-to-variant decisions, preserving the real terrain's ecology. */
public final class LotmEcologyRules {
    private static final Set<String> OCEANS = Set.of(
            "minecraft:deep_ocean", "minecraft:deep_cold_ocean",
            "minecraft:deep_lukewarm_ocean", "minecraft:deep_frozen_ocean",
            "minecraft:deep_warm_ocean", "realscale:coastal_heath",
            "realscale:gravel_shore_fringe", "realscale:coastal_dune_meadow");
    private static final Set<String> SHADOWED_FORESTS = Set.of(
            "minecraft:dark_forest", "minecraft:old_growth_pine_taiga",
            "minecraft:taiga", "minecraft:old_growth_spruce_taiga",
            "realscale:oak_to_dark_forest", "realscale:pine_oak_mosaic",
            "realscale:birch_conifer_transition", "realscale:subalpine_woodland");
    private static final Set<String> WETLANDS = Set.of(
            "minecraft:swamp", "minecraft:mangrove_swamp",
            "realscale:marshy_woodland", "realscale:seasonal_floodforest",
            "realscale:reed_marsh_edge", "realscale:mountain_wetland");
    private LotmEcologyRules() {}

    /** roll is nonnegative; all rules fail closed in non-matching dimensions/biomes. */
    public static String pickVariant(String creatureType, String biomeId, int roll) {
        if (roll < 0 || biomeId == null || creatureType == null) return null;
        if (creatureType.equals("squid") && OCEANS.contains(biomeId) && roll % 24 == 0)
            return "starlit_squid";
        if (creatureType.equals("spider") && SHADOWED_FORESTS.contains(biomeId) && roll % 32 == 0)
            return "nightmare_eye";
        if (creatureType.equals("bat") && SHADOWED_FORESTS.contains(biomeId) && roll % 24 == 0)
            return "night_owl";
        if (creatureType.equals("frog") && WETLANDS.contains(biomeId) && roll % 20 == 0)
            return "black_spotted_frog";
        if (creatureType.equals("zombie") && WETLANDS.contains(biomeId) && roll % 36 == 0)
            return "shroudman";
        return null;
    }
}
