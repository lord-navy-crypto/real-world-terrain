package org.realscale.neoforge.lotm;

import java.util.Set;
import java.util.Map;

/** Pure, deterministic biome selectors for vanilla mob stand-ins of occult beasts. */
public final class LotmEcologyRules {
    private static final Set<String> OCEANS = Set.of("minecraft:deep_ocean",
        "minecraft:deep_cold_ocean", "minecraft:deep_lukewarm_ocean",
        "minecraft:deep_frozen_ocean", "realscale:coastal_heath");
    private static final Set<String> FORESTS = Set.of("minecraft:forest", "minecraft:dark_forest",
        "minecraft:birch_forest", "minecraft:taiga", "minecraft:old_growth_spruce_taiga",
        "minecraft:old_growth_pine_taiga", "realscale:oak_to_dark_forest",
        "realscale:pine_oak_mosaic", "realscale:subalpine_woodland",
        "realscale:birch_conifer_transition", "realscale:woodland_meadow");
    private static final Set<String> DARK = Set.of("minecraft:dark_forest",
        "realscale:oak_to_dark_forest", "realscale:pine_oak_mosaic");
    private static final Set<String> WETLAND = Set.of("minecraft:swamp",
        "minecraft:mangrove_swamp", "realscale:marshy_woodland",
        "realscale:seasonal_floodforest", "realscale:reed_marsh_edge",
        "realscale:mountain_wetland");
    private static final Set<String> ALPINE = Set.of("minecraft:stony_peaks",
        "minecraft:jagged_peaks", "minecraft:windswept_hills",
        "minecraft:meadow", "realscale:rocky_alpine_meadow",
        "realscale:montane_spruce_meadow", "realscale:subalpine_woodland");
    private static final Set<String> TROPICAL = Set.of("minecraft:jungle",
        "minecraft:sparse_jungle", "minecraft:bamboo_jungle",
        "realscale:tropical_littoral_forest");
    private static final Set<String> COAST = Set.of("minecraft:beach",
        "minecraft:snowy_beach", "realscale:coastal_dune_meadow",
        "realscale:gravel_shore_fringe");
    private static final Set<String> RUINS = Set.of("minecraft:plains",
        "minecraft:taiga", "minecraft:forest", "minecraft:dark_forest",
        "minecraft:swamp", "realscale:woodland_meadow",
        "realscale:oak_to_dark_forest", "realscale:seasonal_floodforest");

    public static boolean ruinBiome(String id) { return RUINS.contains(id); }

    public static final Map<String,String> DROPS = Map.ofEntries(
        Map.entry("starlit_squid","lavas_squid_essence"),
        Map.entry("nightmare_eye","nightmare_eye_shard"),
        Map.entry("night_owl","night_owl_token"),
        Map.entry("black_spotted_frog","black_spotted_frog_token"),
        Map.entry("shroudman","shroudman_crystal"),
        Map.entry("hornacis_goat","hornacis_goat_horn"),
        Map.entry("ironarm_beast","ironarm_spine"),
        Map.entry("four_eyed_turtle","four_eyed_turtle_heart"),
        Map.entry("crimson_howler","crimson_howler_voice"),
        Map.entry("death_crow","death_crow_eye"),
        Map.entry("mist_treant","mist_treant_root"),
        Map.entry("evil_pattern_panther","evil_pattern_panther_essence"),
        Map.entry("stag_beast","stag_beast_antler"),
        Map.entry("grey_grandmother","grey_grandmother_eye"),
        Map.entry("dream_crow","dream_crow_heart"),
        Map.entry("nightmare_shadow","nightmare_shadow_core"),
        Map.entry("spectral_cat","spectral_cat_paw")
    );
    private LotmEcologyRules() {}

    public static String pickVariant(String type, String biome, int roll) {
        return pickVariant(type, biome, roll, 100);
    }
    private static boolean hit(int roll, int denominator, int percent) {
        return roll % Math.max(1, denominator * 100 / Math.max(1, percent)) == 0;
    }
    public static String pickVariant(String type, String biome, int roll, int percent) {
        if (type == null || biome == null || roll < 0 || percent < 1) return null;
        if (type.equals("squid") && OCEANS.contains(biome) && hit(roll,24,percent)) return "starlit_squid";
        if (type.equals("spider") && DARK.contains(biome) && hit(roll,32,percent)) return "nightmare_eye";
        if (type.equals("bat") && FORESTS.contains(biome) && hit(roll,24,percent)) return "night_owl";
        if (type.equals("frog") && WETLAND.contains(biome) && hit(roll,20,percent)) return "black_spotted_frog";
        if (type.equals("zombie") && WETLAND.contains(biome) && hit(roll,36,percent)) return "shroudman";
        if (type.equals("zombie") && FORESTS.contains(biome) && hit(roll,54,percent)) return "mist_treant";
        if (type.equals("goat") && ALPINE.contains(biome) && hit(roll,28,percent)) return "hornacis_goat";
        if (type.equals("husk") && (biome.equals("minecraft:desert") || biome.equals("minecraft:badlands"))
                && hit(roll,38,percent)) return "ironarm_beast";
        if (type.equals("turtle") && COAST.contains(biome) && hit(roll,25,percent)) return "four_eyed_turtle";
        if (type.equals("wolf") && FORESTS.contains(biome) && hit(roll,38,percent)) return "crimson_howler";
        if (type.equals("parrot") && TROPICAL.contains(biome) && hit(roll,45,percent)) return "death_crow";
        if (type.equals("parrot") && TROPICAL.contains(biome) && hit(roll,47,percent)) return "grey_grandmother";
        if (type.equals("ocelot") && TROPICAL.contains(biome) && hit(roll,30,percent)) return "evil_pattern_panther";
        if (type.equals("cow") && FORESTS.contains(biome) && hit(roll,45,percent)) return "stag_beast";
        if (type.equals("phantom") && hit(roll,38,percent)) return "dream_crow";
        if (type.equals("enderman") && DARK.contains(biome) && hit(roll,45,percent)) return "nightmare_shadow";
        if (type.equals("cat") && (FORESTS.contains(biome) || biome.equals("minecraft:plains"))
                && hit(roll,36,percent)) return "spectral_cat";
        return null;
    }
}
