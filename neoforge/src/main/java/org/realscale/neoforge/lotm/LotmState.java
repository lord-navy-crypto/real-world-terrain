package org.realscale.neoforge.lotm;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Server-owned Sequence 9/8 spirituality and digestion, preserving legacy NBT keys. */
public final class LotmState {
    public static final String SPIRIT_KEY = "realscale_lotm_spirituality";
    public static final String PRACTICE_KEY = "realscale_lotm_practice";
    public static final String INSTABILITY_KEY = "realscale_lotm_characteristic_instability";
    private static String skillMasteryKey(LotmPath path, int slot) {
        return "realscale_lotm_skill_mastery_" + path.id() + "_" + slot;
    }
    private LotmState() {}

    private static void initialize(Player player, LotmPath path) {
        CompoundTag tag = player.getPersistentData();
        tag.putString(LotmPath.NBT_PATH, path.id());
        tag.putInt(LotmPath.NBT_SEQUENCE, 9);
        tag.putInt(SPIRIT_KEY, LotmBalance.MAX_SPIRIT);
        tag.putInt(PRACTICE_KEY, 0);
        tag.putInt(INSTABILITY_KEY, 0);
        for (int slot = 1; slot <= 2; slot++) tag.putInt(skillMasteryKey(path, slot), 0);
        if (player instanceof ServerPlayer serverPlayer) LotmPlayerAbilitySync.sync(serverPlayer);
    }

    /**
     * The only state-changing progression entry point. Revalidates the complete
     * transition here so future callers cannot bypass pathway, rank or digestion gates.
     * Returns the same stable reason codes used by the potion feedback messages.
     */
    public static String advance(Player player, LotmPath path, int sequence, boolean direct) {
        if (!LotmConfig.ENABLED.get()) return "disabled";
        if (path == null) return "path";
        LotmPath current = LotmPath.of(player);
        String validation = LotmProgression.validate(current == null ? null : current.id(),
                sequence(player), path.id(), sequence, practice(player), direct,
                LotmConfig.POTION_PRACTICE.get(), LotmConfig.CHARACTERISTIC_PRACTICE.get());
        if (!"ready".equals(validation)) return validation;

        if (sequence == 9) {
            initialize(player, path);
            if (direct) player.getPersistentData().putInt(INSTABILITY_KEY, 1);
        } else {
            var tag = player.getPersistentData();
            tag.putInt(LotmPath.NBT_SEQUENCE, sequence);
            tag.putInt(PRACTICE_KEY, 0);
            tag.putInt(SPIRIT_KEY, direct ? 30 : LotmBalance.MAX_SPIRIT);
            if (direct) tag.putInt(INSTABILITY_KEY,
                    LotmBalance.addInstability(tag.getInt(INSTABILITY_KEY), 1));
            if (player instanceof ServerPlayer serverPlayer) LotmPlayerAbilitySync.sync(serverPlayer);
        }
        return "ready";
    }

    public static int sequence(Player player) {
        return LotmPath.of(player) == null ? -1
                : player.getPersistentData().getInt(LotmPath.NBT_SEQUENCE);
    }

    public static int instability(Player player) {
        CompoundTag tag = player.getPersistentData();
        int stored = tag.getInt(INSTABILITY_KEY);
        int bounded = LotmBalance.clampInstability(stored);
        if (tag.contains(INSTABILITY_KEY) && stored != bounded) {
            tag.putInt(INSTABILITY_KEY, bounded);
        }
        return bounded;
    }

    /** Reduce accumulated direct-characteristic instability without going below zero. */
    public static int reduceInstability(Player player, int amount) {
        int sequence = sequence(player);
        if (LotmPath.of(player) == null || (sequence != 8 && sequence != 9) || amount <= 0) return 0;
        int current = instability(player);
        int reduced = Math.min(current, amount);
        if (reduced > 0) {
            player.getPersistentData().putInt(INSTABILITY_KEY, current - reduced);
        }
        return reduced;
    }

    public static int spirit(Player player) {
        CompoundTag tag = player.getPersistentData();
        return tag.contains(SPIRIT_KEY)
                ? LotmBalance.clamp(tag.getInt(SPIRIT_KEY), LotmBalance.MAX_SPIRIT)
                : LotmBalance.MAX_SPIRIT; // Safe migration from the first pilot.
    }

    public static int practice(Player player) {
        return LotmBalance.clamp(player.getPersistentData().getInt(PRACTICE_KEY),
                LotmBalance.MAX_PRACTICE);
    }

    /** RPG-facing name; reads the legacy practice key for existing saves. */
    public static int digestion(Player player) {
        return practice(player);
    }

    /** RPG-facing name; preserves the legacy persisted NBT key. */
    public static void recordDigestion(Player player, int amount) {
        recordPractice(player, amount);
    }

    public static int skillMastery(Player player, LotmPath path, int slot) {
        if (path == null || slot < 1 || slot > 2 || LotmPath.of(player) != path) return 0;
        String key = skillMasteryKey(path, slot);
        int stored = player.getPersistentData().getInt(key);
        int bounded = LotmBalance.clampSkillMastery(stored);
        if (player.getPersistentData().contains(key) && stored != bounded) {
            player.getPersistentData().putInt(key, bounded);
        }
        return bounded;
    }

    /** Read a pathway manual; this improves an already-authorized skill, never grants an ability. */
    public static String learnSkillManual(Player player, LotmPath path, int slot) {
        if (!LotmConfig.ENABLED.get() || !LotmConfig.SKILLS_ENABLED.get()
                || !LotmConfig.SKILL_BOOKS_ENABLED.get()) return "disabled";
        if (path == null || slot < 1 || slot > 2 || LotmPath.of(player) != path) return "path";
        int sequence = sequence(player);
        if (sequence != 9 && sequence != 8) return "sequence";
        int cap = sequence == 9 ? 1 : LotmConfig.SKILL_MASTERY_CAP.get();
        int current = skillMastery(player, path, slot);
        if (current >= cap) return "mastered";
        player.getPersistentData().putInt(skillMasteryKey(path, slot), current + 1);
        return "learned";
    }

    public static boolean consume(Player player, int amount) {
        if (LotmPath.of(player) == null || amount < 0) return false;
        int current = spirit(player);
        if (current < amount) return false;
        player.getPersistentData().putInt(SPIRIT_KEY, current - amount);
        return true;
    }

    public static void regenerate(Player player, int amount) {
        if (LotmPath.of(player) == null) return;
        player.getPersistentData().putInt(SPIRIT_KEY,
                LotmBalance.regenerate(spirit(player), amount));
    }

    public static void recordPractice(Player player, int amount) {
        if (LotmPath.of(player) == null) return;
        player.getPersistentData().putInt(PRACTICE_KEY,
                LotmBalance.practice(practice(player), amount));
    }

    public static void copy(Player original, Player clone) {
        LotmHud.copy(original, clone);
        LotmAppearance.copy(original, clone);
        CompoundTag source = original.getPersistentData();
        if (!source.contains(LotmPath.NBT_PATH)) return;
        CompoundTag target = clone.getPersistentData();
        target.putString(LotmPath.NBT_PATH, source.getString(LotmPath.NBT_PATH));
        target.putInt(LotmPath.NBT_SEQUENCE, source.getInt(LotmPath.NBT_SEQUENCE));
        target.putInt(SPIRIT_KEY, spirit(original));
        target.putInt(PRACTICE_KEY, practice(original));
        target.putInt(INSTABILITY_KEY, instability(original));
        LotmPath path = LotmPath.of(original);
        if (path != null) {
            for (int slot = 1; slot <= 2; slot++) {
                target.putInt(skillMasteryKey(path, slot), skillMastery(original, path, slot));
            }
        }
        LotmActing.copy(original, clone);
        if (clone instanceof ServerPlayer serverPlayer) LotmPlayerAbilitySync.sync(serverPlayer);
    }
}
