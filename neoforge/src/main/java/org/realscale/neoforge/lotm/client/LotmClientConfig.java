package org.realscale.neoforge.lotm.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Local display preferences; these never alter server-owned progression or balance. */
public final class LotmClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue PANEL_SCALE_PERCENT;
    public static final ModConfigSpec.IntValue PANEL_OPACITY_PERCENT;
    public static final ModConfigSpec.BooleanValue COMPACT_LAYOUT;
    public static final ModConfigSpec.BooleanValue SHOW_SKILL_DETAILS;
    public static final ModConfigSpec.BooleanValue SHOW_JOURNAL_TIMESTAMPS;
    public static final ModConfigSpec.BooleanValue JOURNAL_NEWEST_FIRST;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("lotm_panel");
        PANEL_SCALE_PERCENT = builder
                .comment("Scale the LOTM panel independently of the global Minecraft GUI scale.")
                .defineInRange("panelScalePercent", 100, 75, 125);
        PANEL_OPACITY_PERCENT = builder
                .comment("Panel background opacity percent; lower values reveal more of the world behind it.")
                .defineInRange("panelOpacityPercent", 92, 35, 100);
        COMPACT_LAYOUT = builder
                .comment("Use a denser panel layout and show more journal entries per page.")
                .define("compactLayout", false);
        SHOW_SKILL_DETAILS = builder
                .comment("Show full skill effect details in hover tooltips.")
                .define("showSkillDetails", true);
        SHOW_JOURNAL_TIMESTAMPS = builder
                .comment("Show in-game day and time in research journal tooltips.")
                .define("showJournalTimestamps", true);
        JOURNAL_NEWEST_FIRST = builder
                .comment("Sort journal entries newest first when enabled; otherwise oldest first.")
                .define("journalNewestFirst", true);
        builder.pop();
        SPEC = builder.build();
    }

    public static void save() {
        SPEC.save();
    }

    private LotmClientConfig() {}
}
