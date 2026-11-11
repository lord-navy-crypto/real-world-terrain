package org.realscale.neoforge.lotm;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side gameplay tuning. Existing terrain and biome selectors are unchanged. */
public final class LotmConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.BooleanValue CREATURE_VARIANTS;
    public static final ModConfigSpec.BooleanValue RELIC_SITES;
    public static final ModConfigSpec.IntValue VARIANT_RARITY_PERCENT;
    public static final ModConfigSpec.IntValue RELIC_RARITY;
    public static final ModConfigSpec.IntValue POTION_PRACTICE;
    public static final ModConfigSpec.IntValue CHARACTERISTIC_PRACTICE;
    public static final ModConfigSpec.IntValue SPIRIT_RECOVERY;
    public static final ModConfigSpec.IntValue ALCHEMY_SEQUENCE9_TICKS;
    public static final ModConfigSpec.IntValue ALCHEMY_SEQUENCE8_TICKS;
    public static final ModConfigSpec.IntValue ALCHEMY_SEQUENCE7_TICKS;
    public static final ModConfigSpec.IntValue DIGESTION_REWARD_PERCENT;
    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("lotm");
        ENABLED = b.comment("Enable LOTM gameplay and low-sequence progression.")
                .define("enabled", true);
        CREATURE_VARIANTS = b.comment("Enable rare adapted vanilla creatures with occult drops.")
                .define("creatureVariants", true);
        RELIC_SITES = b.comment("Enable small ruined sites yielding genuine non-craftable characteristics.")
                .define("relicSites", true);
        VARIANT_RARITY_PERCENT = b.comment("Variant frequency percent relative to default (50 = half, 200 = double).")
                .defineInRange("variantRatePercent", 100, 1, 300);
        RELIC_RARITY = b.comment("Secondary per-feature relic chance denominator; larger = rarer.")
                .defineInRange("relicRarity", 8, 1, 128);
        POTION_PRACTICE = b.comment("Practice required before prepared Sequence 8/7 potion.")
                .defineInRange("preparedPractice", 70, 0, 100);
        CHARACTERISTIC_PRACTICE = b.comment("Practice required for direct Sequence 8/7 characteristic; no ingredients.")
                .defineInRange("characteristicPractice", 90, 0, 100);
        SPIRIT_RECOVERY = b.comment("Spirituality regained each 40 server ticks.")
                .defineInRange("spiritRecovery", 2, 0, 20);
        ALCHEMY_SEQUENCE9_TICKS = b.comment("Sequence 9 potion processing time in game ticks.")
                .defineInRange("alchemySequence9Ticks", 120, 20, 2400);
        ALCHEMY_SEQUENCE8_TICKS = b.comment("Sequence 8 potion processing time in game ticks.")
                .defineInRange("alchemySequence8Ticks", 240, 20, 4800);
        ALCHEMY_SEQUENCE7_TICKS = b.comment("Sequence 7 potion processing time in game ticks.")
                .defineInRange("alchemySequence7Ticks", 360, 20, 7200);
        DIGESTION_REWARD_PERCENT = b.comment("Scale NPC-free acting rewards (100 = default).")
                .defineInRange("digestionRewardPercent", 100, 0, 300);
        b.pop();
        SPEC = b.build();
    }
    private LotmConfig() {}
}
