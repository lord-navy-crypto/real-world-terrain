package org.realscale.neoforge.lotm;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side gameplay tuning. Existing terrain and biome selectors are unchanged. */
public final class LotmConfig {
    public static final boolean DEFAULT_CREATURE_ENTITIES = true;
    public static final boolean DEFAULT_CREATURE_VARIANTS = false;
    public static final boolean DEFAULT_RELIC_SITES = false;
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.BooleanValue CREATURE_ENTITIES;
    public static final ModConfigSpec.BooleanValue CREATURE_VARIANTS;
    public static final ModConfigSpec.BooleanValue RELIC_SITES;
    public static final ModConfigSpec.IntValue VARIANT_RARITY_PERCENT;
    public static final ModConfigSpec.IntValue RELIC_RARITY;
    public static final ModConfigSpec.IntValue POTION_PRACTICE;
    public static final ModConfigSpec.IntValue CHARACTERISTIC_PRACTICE;
    public static final ModConfigSpec.IntValue SPIRIT_RECOVERY;
    public static final ModConfigSpec.IntValue ALCHEMY_SEQUENCE9_TICKS;
    public static final ModConfigSpec.IntValue ALCHEMY_SEQUENCE8_TICKS;
    public static final ModConfigSpec.IntValue DIGESTION_REWARD_PERCENT;
    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("lotm");
        ENABLED = b.comment("Enable LOTM gameplay and low-sequence progression.")
                .define("enabled", true);
        CREATURE_ENTITIES = b.comment("Enable the ten independent LOTM creature EntityTypes and their natural spawns.")
                .define("creatureEntities", DEFAULT_CREATURE_ENTITIES);
        CREATURE_VARIANTS = b.comment("Legacy vanilla-mob stand-ins; disabled by default when independent creatures are enabled.")
                .define("creatureVariants", DEFAULT_CREATURE_VARIANTS);
        RELIC_SITES = b.comment("Legacy custom relic-site placement. Disabled; use vanilla structure loot instead.")
                .define("relicSites", DEFAULT_RELIC_SITES);
        VARIANT_RARITY_PERCENT = b.comment("Legacy stand-in frequency percent relative to default (50 = half, 200 = double).")
                .defineInRange("variantRatePercent", 100, 1, 300);
        RELIC_RARITY = b.comment("Legacy relic-site chance denominator; retained for old config compatibility.")
                .defineInRange("relicRarity", 8, 1, 128);
        POTION_PRACTICE = b.comment("Digestion required before a prepared Sequence 8 potion.")
                .defineInRange("preparedPractice", 70, 0, 100);
        CHARACTERISTIC_PRACTICE = b.comment("Digestion required for direct use of an unprepared Sequence 8 characteristic.")
                .defineInRange("characteristicPractice", 90, 0, 100);
        SPIRIT_RECOVERY = b.comment("Spirituality regained each 40 server ticks.")
                .defineInRange("spiritRecovery", 2, 0, 20);
        ALCHEMY_SEQUENCE9_TICKS = b.comment("Sequence 9 potion processing time in game ticks.")
                .defineInRange("alchemySequence9Ticks", 120, 20, 2400);
        ALCHEMY_SEQUENCE8_TICKS = b.comment("Sequence 8 potion processing time in game ticks.")
                .defineInRange("alchemySequence8Ticks", 240, 20, 4800);
        DIGESTION_REWARD_PERCENT = b.comment("Scale NPC-free acting rewards (100 = default).")
                .defineInRange("digestionRewardPercent", 100, 0, 300);
        b.pop();
        SPEC = b.build();
    }
    private LotmConfig() {}
}
