package org.realscale.neoforge.lotm;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.nbt.CompoundTag;

/**
 * Opt-in, server-authoritative compact status HUD using Minecraft's accessible action bar.
 * No custom packet or client-only state is required.
 */
public final class LotmHud {
    private static final String ENABLED_KEY = "realscale_lotm_hud_enabled";
    private static final int DEFAULT_REFRESH_INTERVAL_TICKS = 40;

    private LotmHud() {}

    public static boolean toggle(ServerPlayer player) {
        boolean enabled = !player.getPersistentData().getBoolean(ENABLED_KEY);
        player.getPersistentData().putBoolean(ENABLED_KEY, enabled);
        return enabled;
    }

    public static boolean enabled(Player player) {
        return player.getPersistentData().getBoolean(ENABLED_KEY);
    }

    public static boolean shouldRender(int tickCount, boolean hudEnabled,
            boolean lotmEnabled, boolean hasPath) {
        return shouldRender(tickCount, hudEnabled, lotmEnabled, hasPath, DEFAULT_REFRESH_INTERVAL_TICKS);
    }

    public static boolean shouldRender(int tickCount, boolean hudEnabled,
            boolean lotmEnabled, boolean hasPath, int refreshInterval) {
        int interval = Math.max(1, refreshInterval);
        return hudEnabled && lotmEnabled && hasPath && Math.floorMod(tickCount, interval) == 0;
    }

    public static void tick(ServerPlayer player) {
        LotmPath path = LotmPath.of(player);
        if (!shouldRender(player.tickCount, enabled(player), LotmConfig.ENABLED.get(),
                path != null, LotmConfig.HUD_REFRESH_TICKS.get())) return;
        int sequence = LotmState.sequence(player);
        if (sequence < 8 || sequence > 9) return;
        Component line = Component.translatable("message.realscale.lotm.hud.line",
                Component.translatable("lotm.realscale.path." + path.id()),
                sequence, LotmState.spirit(player), LotmBalance.MAX_SPIRIT,
                LotmState.digestion(player), LotmBalance.MAX_PRACTICE,
                LotmState.instability(player));
        player.displayClientMessage(line, true);
    }

    /** Preserve the player's HUD preference through death and respawn. */
    public static void copy(Player original, Player clone) {
        CompoundTag source = original.getPersistentData();
        if (source.contains(ENABLED_KEY)) {
            clone.getPersistentData().putBoolean(ENABLED_KEY, source.getBoolean(ENABLED_KEY));
        }
    }
}
