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

    // When a running client temporarily has malformed/out-of-range config state,
    // keep the panel usable with conservative defaults and an explicit reset.
    private static int bounded(ModConfigSpec.IntValue value, int fallback, int minimum, int maximum) {
        try {
            return Math.max(minimum, Math.min(maximum, value.get()));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static boolean enabled(ModConfigSpec.BooleanValue value, boolean fallback) {
        try {
            return value.get();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    public static int scalePercent() {
        return bounded(PANEL_SCALE_PERCENT, 100, 75, 125);
    }

    public static int opacityPercent() {
        return bounded(PANEL_OPACITY_PERCENT, 92, 35, 100);
    }

    public static boolean compactLayout() {
        return enabled(COMPACT_LAYOUT, false);
    }

    public static boolean showSkillDetails() {
        return enabled(SHOW_SKILL_DETAILS, true);
    }

    public static boolean showJournalTimestamps() {
        return enabled(SHOW_JOURNAL_TIMESTAMPS, true);
    }

    public static boolean newestFirst() {
        return enabled(JOURNAL_NEWEST_FIRST, true);
    }

    /** Saving should not crash the entire game if the config file is inaccessible. */
    public static boolean save() {
        try {
            SPEC.save();
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /** Restore defaults from inside the panel without changing server game balance. */
    public static boolean restoreDefaults() {
        try {
            PANEL_SCALE_PERCENT.set(100);
            PANEL_OPACITY_PERCENT.set(92);
            COMPACT_LAYOUT.set(false);
            SHOW_SKILL_DETAILS.set(true);
            SHOW_JOURNAL_TIMESTAMPS.set(true);
            JOURNAL_NEWEST_FIRST.set(true);
            return save();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private LotmClientConfig() {}
}
