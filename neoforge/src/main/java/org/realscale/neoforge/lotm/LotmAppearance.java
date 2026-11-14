package org.realscale.neoforge.lotm;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Optional, low-cost cosmetic aura; it never changes hitboxes, armor or player attributes. */
public final class LotmAppearance {
    private static final String ENABLED_KEY = "realscale_lotm_appearance_enabled";
    private LotmAppearance() {}

    public static boolean toggle(ServerPlayer player) {
        boolean enabled = !player.getPersistentData().getBoolean(ENABLED_KEY);
        player.getPersistentData().putBoolean(ENABLED_KEY, enabled);
        return enabled;
    }

    public static boolean enabled(Player player) {
        return player.getPersistentData().getBoolean(ENABLED_KEY);
    }

    public static boolean shouldEmit(int tickCount, boolean preferenceEnabled,
            boolean featureEnabled, boolean hasPath, int intervalTicks) {
        int interval = Math.max(1, intervalTicks);
        return preferenceEnabled && featureEnabled && hasPath
                && Math.floorMod(tickCount, interval) == 0;
    }

    public static void tick(ServerPlayer player) {
        LotmPath path = LotmPath.of(player);
        if (!shouldEmit(player.tickCount, enabled(player), LotmConfig.ENABLED.get()
                && LotmConfig.APPEARANCE_EFFECTS.get(), path != null,
                LotmConfig.APPEARANCE_INTERVAL_TICKS.get())) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        int sequence = LotmState.sequence(player);
        if (sequence < 8 || sequence > 9) return;
        int mastery = Math.max(LotmState.skillMastery(player, path, 1),
                LotmState.skillMastery(player, path, 2));
        int count = 1 + Math.min(2, mastery);
        var particle = switch (path) {
            case SEER -> ParticleTypes.ENCHANT;
            case MYSTERY_PRYER -> ParticleTypes.END_ROD;
            case SLEEPLESS -> ParticleTypes.SOUL;
            case CORPSE_COLLECTOR -> ParticleTypes.SOUL_FIRE_FLAME;
        };
        level.sendParticles(particle, player.getX(), player.getY() + 1.0D,
                player.getZ(), count, 0.22D, 0.38D, 0.22D, 0.005D);
    }

    public static void copy(Player original, Player clone) {
        CompoundTag source = original.getPersistentData();
        if (source.contains(ENABLED_KEY)) {
            clone.getPersistentData().putBoolean(ENABLED_KEY, source.getBoolean(ENABLED_KEY));
        }
    }
}
