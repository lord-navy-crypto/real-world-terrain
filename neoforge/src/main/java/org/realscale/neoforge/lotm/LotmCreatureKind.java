package org.realscale.neoforge.lotm;

import java.util.Set;
import net.minecraft.world.entity.MobCategory;

/**
 * Stable metadata for the ten active LOTM creatures.
 *
 * Sequence 7 creatures are intentionally absent: their old item IDs and
 * language keys remain archived elsewhere, but they are not natural spawns.
 */
public enum LotmCreatureKind {
    STARLIT_SQUID("starlit_squid", 9, MobCategory.WATER_CREATURE,
            0.95F, 0.75F, 18.0D, 0.24D, 0.0D, 1.0D,
            false, true, false, false, false, false, 4, 1, 2,
            "lavas_squid_essence",
            Set.of("minecraft:deep_ocean", "minecraft:deep_cold_ocean",
                    "minecraft:deep_lukewarm_ocean", "minecraft:deep_frozen_ocean")),

    NIGHTMARE_EYE("nightmare_eye", 9, MobCategory.MONSTER,
            1.20F, 0.95F, 24.0D, 0.28D, 4.5D, 1.0D,
            true, false, false, false, true, false, 3, 1, 2,
            "nightmare_eye_shard",
            Set.of("minecraft:dark_forest", "realscale:oak_to_dark_forest",
                    "realscale:pine_oak_mosaic")),

    NIGHT_OWL("night_owl", 9, MobCategory.CREATURE,
            0.75F, 1.15F, 13.0D, 0.27D, 0.0D, 0.0D,
            false, false, false, true, true, false, 3, 1, 2,
            "night_owl_token",
            Set.of("minecraft:forest", "minecraft:dark_forest", "minecraft:birch_forest",
                    "minecraft:taiga", "minecraft:old_growth_spruce_taiga",
                    "minecraft:old_growth_pine_taiga", "realscale:oak_to_dark_forest",
                    "realscale:pine_oak_mosaic", "realscale:subalpine_woodland",
                    "realscale:woodland_meadow")),

    BLACK_SPOTTED_FROG("black_spotted_frog", 9, MobCategory.CREATURE,
            0.78F, 0.58F, 12.0D, 0.25D, 0.0D, 0.0D,
            false, false, true, false, false, false, 4, 1, 3,
            "black_spotted_frog_token",
            Set.of("minecraft:swamp", "minecraft:mangrove_swamp",
                    "realscale:marshy_woodland", "realscale:seasonal_floodforest",
                    "realscale:reed_marsh_edge", "realscale:mountain_wetland")),

    SHROUDMAN("shroudman", 9, MobCategory.MONSTER,
            0.72F, 1.95F, 26.0D, 0.21D, 5.0D, 2.0D,
            true, false, false, false, true, false, 2, 1, 2,
            "shroudman_crystal",
            Set.of("minecraft:swamp", "minecraft:mangrove_swamp",
                    "realscale:marshy_woodland", "realscale:seasonal_floodforest",
                    "realscale:reed_marsh_edge")),

    HORNACIS_GOAT("hornacis_goat", 8, MobCategory.CREATURE,
            1.05F, 1.40F, 26.0D, 0.32D, 3.5D, 3.0D,
            false, false, false, false, false, true, 3, 1, 3,
            "hornacis_goat_horn",
            Set.of("minecraft:stony_peaks", "minecraft:jagged_peaks",
                    "minecraft:windswept_hills", "minecraft:meadow",
                    "realscale:rocky_alpine_meadow", "realscale:montane_spruce_meadow",
                    "realscale:subalpine_woodland")),

    IRONARM_BEAST("ironarm_beast", 8, MobCategory.MONSTER,
            1.45F, 1.55F, 36.0D, 0.20D, 6.0D, 6.0D,
            true, false, false, false, false, false, 2, 1, 2,
            "ironarm_spine",
            Set.of("minecraft:desert", "minecraft:badlands", "minecraft:wooded_badlands")),

    FOUR_EYED_TURTLE("four_eyed_turtle", 8, MobCategory.CREATURE,
            1.30F, 0.82F, 28.0D, 0.18D, 0.0D, 4.0D,
            false, false, true, false, false, false, 3, 1, 2,
            "four_eyed_turtle_heart",
            Set.of("minecraft:beach", "minecraft:snowy_beach", "minecraft:stony_shore",
                    "realscale:coastal_dune_meadow", "realscale:gravel_shore_fringe",
                    "realscale:coastal_heath")),

    CRIMSON_HOWLER("crimson_howler", 8, MobCategory.MONSTER,
            1.25F, 1.15F, 30.0D, 0.33D, 5.0D, 2.0D,
            true, false, false, false, false, false, 3, 1, 3,
            "crimson_howler_voice",
            Set.of("minecraft:forest", "minecraft:dark_forest", "minecraft:birch_forest",
                    "minecraft:taiga", "minecraft:old_growth_spruce_taiga",
                    "minecraft:old_growth_pine_taiga", "realscale:oak_to_dark_forest",
                    "realscale:pine_oak_mosaic", "realscale:woodland_meadow")),

    DEATH_CROW("death_crow", 8, MobCategory.MONSTER,
            0.82F, 0.95F, 18.0D, 0.30D, 3.5D, 0.0D,
            true, false, false, true, true, false, 2, 1, 3,
            "death_crow_eye",
            Set.of("minecraft:jungle", "minecraft:sparse_jungle", "minecraft:bamboo_jungle",
                    "realscale:tropical_littoral_forest"));

    private final String id;
    private final int sequence;
    private final MobCategory spawnCategory;
    private final float width;
    private final float height;
    private final double maxHealth;
    private final double movementSpeed;
    private final double attackDamage;
    private final double armor;
    private final boolean hostile;
    private final boolean aquatic;
    private final boolean amphibious;
    private final boolean flying;
    private final boolean nocturnal;
    private final boolean retaliates;
    private final int spawnWeight;
    private final int minGroupSize;
    private final int maxGroupSize;
    private final String dropId;
    private final Set<String> biomes;

    LotmCreatureKind(String id, int sequence, MobCategory spawnCategory,
            float width, float height, double maxHealth, double movementSpeed,
            double attackDamage, double armor, boolean hostile, boolean aquatic,
            boolean amphibious, boolean flying, boolean nocturnal, boolean retaliates,
            int spawnWeight, int minGroupSize, int maxGroupSize, String dropId,
            Set<String> biomes) {
        this.id = id;
        this.sequence = sequence;
        this.spawnCategory = spawnCategory;
        this.width = width;
        this.height = height;
        this.maxHealth = maxHealth;
        this.movementSpeed = movementSpeed;
        this.attackDamage = attackDamage;
        this.armor = armor;
        this.hostile = hostile;
        this.aquatic = aquatic;
        this.amphibious = amphibious;
        this.flying = flying;
        this.nocturnal = nocturnal;
        this.retaliates = retaliates;
        this.spawnWeight = spawnWeight;
        this.minGroupSize = minGroupSize;
        this.maxGroupSize = maxGroupSize;
        this.dropId = dropId;
        this.biomes = Set.copyOf(biomes);
    }

    public String id() { return id; }
    public int sequence() { return sequence; }
    public MobCategory spawnCategory() { return spawnCategory; }
    public float width() { return width; }
    public float height() { return height; }
    public double maxHealth() { return maxHealth; }
    public double movementSpeed() { return movementSpeed; }
    public double attackDamage() { return attackDamage; }
    public double armor() { return armor; }
    public boolean isHostile() { return hostile; }
    public boolean isAquatic() { return aquatic; }
    public boolean isAmphibious() { return amphibious; }
    public boolean isFlying() { return flying; }
    public boolean isNocturnal() { return nocturnal; }
    public boolean retaliates() { return retaliates; }
    public int spawnWeight() { return spawnWeight; }
    public int minGroupSize() { return minGroupSize; }
    public int maxGroupSize() { return maxGroupSize; }
    public String dropId() { return dropId; }
    public Set<String> biomes() { return biomes; }
}
