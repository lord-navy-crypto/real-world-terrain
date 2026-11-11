package org.realscale.neoforge.lotm;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.event.level.BlockEvent;

/** NPC-free acting loop: unique biome exploration, skill use, research and fieldwork. */
public final class LotmActing {
    private static final String DAY_KEY = "realscale_lotm_acting_day";
    private static final String PREFIX = "realscale_lotm_acting_daily_";
    private LotmActing() {}

    public static void onPlayerTick(ServerPlayer player) {
        LotmPath path = LotmPath.of(player);
        if (path == null) return;
        String biome = player.level().getBiome(player.blockPosition()).unwrapKey()
                .map(key -> key.location().toString()).orElse("");
        if (biome.isEmpty()) return;
        String key = "realscale_lotm_seen_" + path.id() + "_" + LotmState.sequence(player);
        CompoundTag tag = player.getPersistentData();
        ListTag seen = tag.getList(key, Tag.TAG_STRING);
        StringTag id = StringTag.valueOf(biome);
        if (!seen.contains(id)) {
            seen.add(id);
            tag.put(key, seen);
            award(player, "exploration", 4);
        }
    }

    public static void recordSkillUse(Player player, int points) { award(player, "skill", points); }
    public static void recordResearch(Player player, int points) { award(player, "research", points); }

    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player.level().isClientSide() || LotmPath.of(player) == null) return;
        Block block = event.getState().getBlock();
        if (block == LotmContent.STAR_ORE.get() || block == LotmContent.CURSED_ORE.get()
                || block == LotmContent.GRAVE_ORE.get() || block == LotmContent.MIDNIGHT_BLOOM.get()
                || block == LotmContent.FACE_ROSE.get() || block == LotmContent.SOUL_BELLFLOWER.get()
                || block == LotmContent.BLOOD_ROSE.get() || block == LotmContent.RELIC_CACHE.get()) {
            award(player, "fieldwork", 2);
        }
    }

    public static int dailyUsed(Player player, String category) {
        if (LotmPath.of(player) == null || dailyCap(category) == 0) return 0;
        resetDailyIfNeeded(player);
        return Math.max(0, player.getPersistentData().getInt(PREFIX + category));
    }

    public static int dailyCap(String category) {
        return switch (category) {
            case "exploration" -> 12;
            case "skill" -> 8;
            case "research" -> 9;
            case "fieldwork" -> 6;
            default -> 0;
        };
    }

    private static void resetDailyIfNeeded(Player player) {
        CompoundTag tag = player.getPersistentData();
        long day = player.level().getDayTime() / 24000L;
        if (!tag.contains(DAY_KEY) || tag.getLong(DAY_KEY) != day) {
            tag.putLong(DAY_KEY, day);
            tag.putInt(PREFIX + "exploration", 0);
            tag.putInt(PREFIX + "skill", 0);
            tag.putInt(PREFIX + "research", 0);
            tag.putInt(PREFIX + "fieldwork", 0);
        }
    }

    private static void award(Player player, String category, int points) {
        if (points <= 0 || LotmPath.of(player) == null) return;
        int cap = dailyCap(category);
        if (cap == 0) return;
        resetDailyIfNeeded(player);
        CompoundTag tag = player.getPersistentData();
        String key = PREFIX + category;
        int used = tag.getInt(key);
        int scaled = Math.max(0, points * LotmConfig.DIGESTION_REWARD_PERCENT.get() / 100);
        int reward = Math.min(scaled, Math.max(0, cap - used));
        if (reward > 0) {
            tag.putInt(key, used + reward);
            LotmState.recordPractice(player, reward);
        }
    }
}
