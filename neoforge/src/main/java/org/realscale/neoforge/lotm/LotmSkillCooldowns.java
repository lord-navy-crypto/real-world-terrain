package org.realscale.neoforge.lotm;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Read-only display mirror of successful Player Abilities activations.
 * Player Abilities remains the sole authority for permitting skill use and
 * applying cooldowns; this only shows time since the server accepted a use.
 */
public final class LotmSkillCooldowns {
    private static final String PREFIX = "realscale_lotm_skill_display_cooldown_";

    private LotmSkillCooldowns() {}

    private static String key(LotmPath path, int slot) {
        return PREFIX + path.id() + "_" + slot;
    }

    private static long serverTick(ServerPlayer player) {
        return player.getServer().overworld().getGameTime();
    }

    public static void recordSuccessfulUse(ServerPlayer player, LotmPath path, int slot, int sequence) {
        if (path == null || slot < 1 || slot > 2 || (sequence != 9 && sequence != 8)) return;
        long end = serverTick(player) + LotmBalance.skillCooldownTicks(slot, sequence);
        player.getPersistentData().putLong(key(path, slot), end);
    }

    public static int remaining(ServerPlayer player, LotmPath path, int slot) {
        if (path == null || slot < 1 || slot > 2) return 0;
        long end = player.getPersistentData().getLong(key(path, slot));
        long left = end - serverTick(player);
        return (int) Math.max(0L, Math.min(Short.MAX_VALUE, left));
    }

    public static void copy(Player original, Player clone) {
        CompoundTag source = original.getPersistentData();
        CompoundTag target = clone.getPersistentData();
        for (LotmPath path : LotmPath.values()) {
            for (int slot = 1; slot <= 2; slot++) {
                String key = key(path, slot);
                if (source.contains(key)) target.putLong(key, source.getLong(key));
            }
        }
    }
}
