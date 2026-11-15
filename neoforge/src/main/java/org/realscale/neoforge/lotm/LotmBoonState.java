package org.realscale.neoforge.lotm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * Separate save data for the Fallen Mother's boon. It never overwrites the
 * player's normal LotmPath, sequence, spirituality, or digestion.
 */
public final class LotmBoonState {
    public static final int SOURCE_NONE = 0;
    public static final int SOURCE_FALLEN_MOTHER = 1;
    private static final String SOURCE_KEY = "realscale_lotm_boon_source";
    private static final String PATH_KEY = "realscale_lotm_boon_path";
    private static final String SEQUENCE_KEY = "realscale_lotm_boon_sequence";
    private static final String TAINT_KEY = "realscale_lotm_boon_taint";
    private static final String REVOKED_KEY = "realscale_lotm_boon_revoked";
    private static final String LAST_INSIGHT_KEY = "realscale_lotm_boon_last_insight";
    private static final String DISCOVERIES_KEY = "realscale_lotm_boon_discoveries";

    private LotmBoonState() {}

    public static int sourceId(Player player) {
        return player.getPersistentData().getInt(SOURCE_KEY);
    }

    public static boolean hasBoon(Player player) {
        return sourceId(player) == SOURCE_FALLEN_MOTHER;
    }

    public static String pathway(Player player) {
        return hasBoon(player) ? player.getPersistentData().getString(PATH_KEY) : "";
    }

    public static int sequence(Player player) {
        return hasBoon(player) ? Math.max(0, Math.min(9,
                player.getPersistentData().getInt(SEQUENCE_KEY))) : 0;
    }

    public static int taint(Player player) {
        CompoundTag tag = player.getPersistentData();
        int stored = tag.getInt(TAINT_KEY);
        int bounded = LotmBoonBalance.clampTaint(stored);
        if (tag.contains(TAINT_KEY) && stored != bounded) tag.putInt(TAINT_KEY, bounded);
        return bounded;
    }

    public static boolean revoked(Player player) {
        return hasBoon(player) && player.getPersistentData().getBoolean(REVOKED_KEY);
    }

    public static boolean active(Player player) {
        return hasBoon(player) && LotmBoonBalance.canActivate(
                sequence(player), taint(player), revoked(player), LotmState.sanity(player));
    }

    public static int discoveries(Player player) {
        return player.getPersistentData().getList(DISCOVERIES_KEY, Tag.TAG_STRING).size();
    }

    public static int insightCooldown(Player player) {
        long last = player.getPersistentData().getLong(LAST_INSIGHT_KEY);
        long remaining = last + LotmBoonBalance.INSIGHT_COOLDOWN_TICKS
                - player.level().getGameTime();
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, remaining));
    }

    public static void copy(Player original, Player clone) {
        CompoundTag source = original.getPersistentData();
        if (!source.contains(SOURCE_KEY)) return;
        CompoundTag target = clone.getPersistentData();
        target.putInt(SOURCE_KEY, source.getInt(SOURCE_KEY));
        target.putString(PATH_KEY, source.getString(PATH_KEY));
        target.putInt(SEQUENCE_KEY, source.getInt(SEQUENCE_KEY));
        target.putInt(TAINT_KEY, taint(original));
        target.putBoolean(REVOKED_KEY, source.getBoolean(REVOKED_KEY));
        target.putLong(LAST_INSIGHT_KEY, source.getLong(LAST_INSIGHT_KEY));
        if (source.contains(DISCOVERIES_KEY, Tag.TAG_LIST)) {
            target.put(DISCOVERIES_KEY, source.getList(DISCOVERIES_KEY, Tag.TAG_STRING).copy());
        }
    }

    /** Altar interaction: grant, advance, stabilize, or rebind the separate boon track. */
    public static String useAtAltar(ServerPlayer player) {
        if (!LotmConfig.ENABLED.get()) return "disabled";
        if (!hasBoon(player)) {
            if (!hasItems(player, LotmContent.STAR_CRYSTAL.get(), 1)
                    || !hasItems(player, LotmContent.ECHO_QUARTZ.get(), 1)
                    || !hasItems(player, LotmContent.BLOOM_ITEM.get(), 1)) return "need_initial";
            consumeItems(player, LotmContent.STAR_CRYSTAL.get(), 1);
            consumeItems(player, LotmContent.ECHO_QUARTZ.get(), 1);
            consumeItems(player, LotmContent.BLOOM_ITEM.get(), 1);
            CompoundTag tag = player.getPersistentData();
            tag.putInt(SOURCE_KEY, SOURCE_FALLEN_MOTHER);
            tag.putString(PATH_KEY, "villain");
            tag.putInt(SEQUENCE_KEY, 9);
            tag.putInt(TAINT_KEY, 12);
            tag.putBoolean(REVOKED_KEY, false);
            tag.putLong(LAST_INSIGHT_KEY, 0L);
            tag.put(DISCOVERIES_KEY, new ListTag());
            LotmState.adjustSanity(player, -4);
            return "granted";
        }

        if (revoked(player)) {
            if (!hasItems(player, LotmContent.STAR_CRYSTAL.get(), 3)
                    || !hasItems(player, LotmContent.ECHO_QUARTZ.get(), 2)
                    || !hasItems(player, LotmContent.FACE_ROSE_ITEM.get(), 1)) return "need_rebind";
            consumeItems(player, LotmContent.STAR_CRYSTAL.get(), 3);
            consumeItems(player, LotmContent.ECHO_QUARTZ.get(), 2);
            consumeItems(player, LotmContent.FACE_ROSE_ITEM.get(), 1);
            player.getPersistentData().putInt(SEQUENCE_KEY, 9);
            player.getPersistentData().putInt(TAINT_KEY, 65);
            player.getPersistentData().putBoolean(REVOKED_KEY, false);
            LotmState.adjustSanity(player, -5);
            return "rebound";
        }

        if (taint(player) >= LotmBoonBalance.UPGRADE_TAINT_LIMIT
                || LotmState.sanity(player) < LotmBoonBalance.UPGRADE_MIN_SANITY) {
            if (!hasItems(player, LotmContent.ECHO_QUARTZ.get(), 1)
                    || !hasItems(player, LotmContent.LUNAR_MICA.get(), 1)) return "need_stabilize";
            consumeItems(player, LotmContent.ECHO_QUARTZ.get(), 1);
            consumeItems(player, LotmContent.LUNAR_MICA.get(), 1);
            player.getPersistentData().putInt(TAINT_KEY, Math.max(0, taint(player) - 20));
            LotmState.adjustSanity(player, 8);
            return "stabilized";
        }

        if (sequence(player) == 9) {
            if (!LotmBoonBalance.canAdvanceToSequence8(
                    discoveries(player), LotmState.sanity(player), taint(player))) return "upgrade_requirements";
            if (!hasItems(player, LotmContent.LUNAR_MICA.get(), 2)
                    || !hasItems(player, LotmContent.SOUL_BELLFLOWER_ITEM.get(), 1)
                    || !hasItems(player, LotmContent.STAR_CRYSTAL.get(), 1)) return "need_upgrade";
            consumeItems(player, LotmContent.LUNAR_MICA.get(), 2);
            consumeItems(player, LotmContent.SOUL_BELLFLOWER_ITEM.get(), 1);
            consumeItems(player, LotmContent.STAR_CRYSTAL.get(), 1);
            player.getPersistentData().putInt(SEQUENCE_KEY, 8);
            addTaint(player, 8);
            LotmState.adjustSanity(player, -3);
            return "advanced";
        }

        if (!hasItems(player, LotmContent.ECHO_QUARTZ.get(), 1)
                || !hasItems(player, LotmContent.LUNAR_MICA.get(), 1)) return "need_stabilize";
        consumeItems(player, LotmContent.ECHO_QUARTZ.get(), 1);
        consumeItems(player, LotmContent.LUNAR_MICA.get(), 1);
        player.getPersistentData().putInt(TAINT_KEY, Math.max(0, taint(player) - 20));
        LotmState.adjustSanity(player, 8);
        return "stabilized";
    }

    /** Activate the non-combat insight granted by the current boon sequence. */
    public static String activateInsight(ServerPlayer player) {
        if (!hasBoon(player)) return "no_boon";
        if (revoked(player)) return "revoked";
        if (taint(player) >= LotmBoonBalance.ACTIVE_TAINT_LIMIT) return "strained";
        if (LotmState.sanity(player) < 10) return "low_sanity";
        if (insightCooldown(player) > 0) return "cooldown";

        int sequence = sequence(player);
        long now = player.serverLevel().getGameTime();
        player.getPersistentData().putLong(LAST_INSIGHT_KEY, now);
        addTaint(player, LotmBoonBalance.insightTaintCost(sequence));
        LotmState.adjustSanity(player, -LotmBoonBalance.insightSanityCost(sequence));

        BlockPos found = findTrace(player, sequence);
        if (found == null) return "none";
        Block block = player.serverLevel().getBlockState(found).getBlock();
        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString();
        ListTag entries = player.getPersistentData().getList(DISCOVERIES_KEY, Tag.TAG_STRING);
        StringTag discovery = StringTag.valueOf(id);
        boolean fresh = !entries.contains(discovery);
        if (fresh) {
            while (entries.size() >= 64) entries.remove(0);
            entries.add(discovery);
            player.getPersistentData().put(DISCOVERIES_KEY, entries);
            if (LotmPath.of(player) != null) {
                LotmActing.recordJournalEvent(player, "other", "discovered", "boon:" + id);
            }
        }
        player.serverLevel().sendParticles(ParticleTypes.END_ROD,
                found.getX() + 0.5D, found.getY() + 0.7D, found.getZ() + 0.5D,
                8, 0.25D, 0.35D, 0.25D, 0.015D);
        return fresh ? "discovered" : "trace";
    }

    private static BlockPos findTrace(ServerPlayer player, int sequence) {
        BlockPos origin = player.blockPosition();
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -8; dx <= 8; dx++) {
                for (int dz = -8; dz <= 8; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    Block block = player.serverLevel().getBlockState(pos).getBlock();
                    boolean match = sequence == 9
                            ? block == LotmContent.RELIC_CACHE.get()
                                    || block == LotmContent.STAR_ORE.get()
                                    || block == LotmContent.CURSED_ORE.get()
                                    || block == LotmContent.GRAVE_ORE.get()
                                    || block == LotmContent.ECHO_QUARTZ_ORE.get()
                                    || block == LotmContent.SPIRIT_QUARTZ_ORE.get()
                            : block == LotmContent.MIDNIGHT_BLOOM.get()
                                    || block == LotmContent.FACE_ROSE.get()
                                    || block == LotmContent.SOUL_BELLFLOWER.get()
                                    || block == LotmContent.BLOOD_ROSE.get();
                    if (match) return pos.immutable();
                }
            }
        }
        return null;
    }

    private static void addTaint(Player player, int amount) {
        int updated = LotmBoonBalance.addTaint(taint(player), amount);
        player.getPersistentData().putInt(TAINT_KEY, updated);
        if (updated >= LotmBoonBalance.MAX_TAINT) {
            player.getPersistentData().putBoolean(REVOKED_KEY, true);
        }
    }

    private static boolean hasItems(Player player, Item item, int required) {
        if (player.getAbilities().instabuild) return true;
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) count += stack.getCount();
            if (count >= required) return true;
        }
        return false;
    }

    private static void consumeItems(Player player, Item item, int required) {
        if (player.getAbilities().instabuild) return;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && required > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(item)) continue;
            int taken = Math.min(required, stack.getCount());
            stack.shrink(taken);
            required -= taken;
        }
    }
}
