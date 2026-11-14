package org.realscale.neoforge.lotm;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Server-owned Sequence 9/8 spirituality and digestion, preserving legacy NBT keys. */
public final class LotmState {
    public static final String SPIRIT_KEY = "realscale_lotm_spirituality";
    public static final String PRACTICE_KEY = "realscale_lotm_practice";
    public static final String INSTABILITY_KEY = "realscale_lotm_characteristic_instability";
    private LotmState() {}

    public static void initialize(Player player, LotmPath path) {
        CompoundTag tag = player.getPersistentData();
        tag.putString(LotmPath.NBT_PATH, path.id());
        tag.putInt(LotmPath.NBT_SEQUENCE, 9);
        tag.putInt(SPIRIT_KEY, LotmBalance.MAX_SPIRIT);
        tag.putInt(PRACTICE_KEY, 0);
        tag.putInt(INSTABILITY_KEY, 0);
        if (player instanceof ServerPlayer serverPlayer) LotmPlayerAbilitySync.sync(serverPlayer);
    }

    public static void advance(Player player, LotmPath path, int sequence, boolean direct) {
        if (sequence < 8 || sequence > 9) return; // Sequence 7+ is archived in this release.
        if (sequence == 9) {
            initialize(player, path);
            if (direct) player.getPersistentData().putInt(INSTABILITY_KEY, 1);
            return;
        }
        var tag = player.getPersistentData();
        tag.putInt(LotmPath.NBT_SEQUENCE, sequence);
        tag.putInt(PRACTICE_KEY, 0);
        tag.putInt(SPIRIT_KEY, direct ? 30 : LotmBalance.MAX_SPIRIT);
        if (direct) tag.putInt(INSTABILITY_KEY, tag.getInt(INSTABILITY_KEY) + 1);
        if (player instanceof ServerPlayer serverPlayer) LotmPlayerAbilitySync.sync(serverPlayer);
    }

    public static int sequence(Player player) {
        return LotmPath.of(player) == null ? -1
                : player.getPersistentData().getInt(LotmPath.NBT_SEQUENCE);
    }

    public static int instability(Player player) {
        return player.getPersistentData().getInt(INSTABILITY_KEY);
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
        CompoundTag source = original.getPersistentData();
        if (!source.contains(LotmPath.NBT_PATH)) return;
        CompoundTag target = clone.getPersistentData();
        target.putString(LotmPath.NBT_PATH, source.getString(LotmPath.NBT_PATH));
        target.putInt(LotmPath.NBT_SEQUENCE, source.getInt(LotmPath.NBT_SEQUENCE));
        target.putInt(SPIRIT_KEY, spirit(original));
        target.putInt(PRACTICE_KEY, practice(original));
        target.putInt(INSTABILITY_KEY, instability(original));
        LotmActing.copy(original, clone);
        if (clone instanceof ServerPlayer serverPlayer) LotmPlayerAbilitySync.sync(serverPlayer);
    }
}
