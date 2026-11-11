package org.realscale.neoforge.lotm;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class LotmConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.BooleanValue CREATURE_VARIANTS;
    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("lotm");
        ENABLED = builder.comment("Enable Sequence 9 abilities and potion advancement.")
                .define("enabled", true);
        CREATURE_VARIANTS = builder.comment("Enable rare magical variants of vanilla animals.")
                .define("creatureVariants", true);
        builder.pop();
        SPEC = builder.build();
    }
    private LotmConfig() {}
}
