package org.realscale.neoforge.lotm;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/** Per-player LOTM presentation preferences, persisted with the player and copied on respawn. */
public final class LotmPreferences {
    private static final String SOUND_KEY = "realscale_lotm_sound_feedback";
    private static final String SKILL_FEEDBACK_KEY = "realscale_lotm_skill_feedback";
    private static final String JOURNAL_NOTIFICATIONS_KEY = "realscale_lotm_journal_notifications";
    private static final String PARTICLES_KEY = "realscale_lotm_particles";

    private LotmPreferences() {}

    private static boolean enabled(Player player, String key) {
        CompoundTag tag = player.getPersistentData();
        return !tag.contains(key) || tag.getBoolean(key);
    }

    private static boolean toggle(Player player, String key) {
        boolean next = !enabled(player, key);
        player.getPersistentData().putBoolean(key, next);
        return next;
    }

    public static boolean soundEnabled(Player player) {
        return enabled(player, SOUND_KEY);
    }

    public static boolean skillFeedbackEnabled(Player player) {
        return enabled(player, SKILL_FEEDBACK_KEY);
    }

    public static boolean journalNotificationsEnabled(Player player) {
        return enabled(player, JOURNAL_NOTIFICATIONS_KEY);
    }

    public static boolean particlesEnabled(Player player) {
        return enabled(player, PARTICLES_KEY);
    }

    public static boolean toggleSound(Player player) {
        return toggle(player, SOUND_KEY);
    }

    public static boolean toggleSkillFeedback(Player player) {
        return toggle(player, SKILL_FEEDBACK_KEY);
    }

    public static boolean toggleJournalNotifications(Player player) {
        return toggle(player, JOURNAL_NOTIFICATIONS_KEY);
    }

    public static boolean toggleParticles(Player player) {
        return toggle(player, PARTICLES_KEY);
    }

    public static void copy(Player original, Player clone) {
        CompoundTag source = original.getPersistentData();
        CompoundTag target = clone.getPersistentData();
        for (String key : new String[] {SOUND_KEY, SKILL_FEEDBACK_KEY, JOURNAL_NOTIFICATIONS_KEY, PARTICLES_KEY}) {
            if (source.contains(key)) target.putBoolean(key, source.getBoolean(key));
        }
    }
}
