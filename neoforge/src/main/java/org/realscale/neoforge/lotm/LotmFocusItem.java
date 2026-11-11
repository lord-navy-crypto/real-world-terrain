package org.realscale.neoforge.lotm;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Four distinct Sequence 9-7 play styles, all server-authoritative and bounded.
 * Magician short-step is only enabled if both destination cells and ground are safe.
 */
public final class LotmFocusItem extends Item {
    public LotmFocusItem(Properties properties) { super(properties); }

    private static void apply(LivingEntity target, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect,
            int ticks, int amplifier) {
        target.addEffect(new MobEffectInstance(effect, ticks, amplifier, false, false));
    }

    private static boolean phaseStep(Level level, Player player) {
        Vec3 look = player.getLookAngle();
        BlockPos original = player.blockPosition();
        BlockPos destination = original.offset((int)Math.round(look.x * 6.0), 0,
                (int)Math.round(look.z * 6.0));
        BlockPos foot = destination.below();
        if (!level.hasChunkAt(destination) || !level.isEmptyBlock(destination)
                || !level.isEmptyBlock(destination.above())
                || !level.getBlockState(foot).isSolidRender(level, foot)) return false;
        player.teleportTo(destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5);
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!LotmConfig.ENABLED.get()) return InteractionResultHolder.fail(stack);
        if (level.isClientSide()) return InteractionResultHolder.success(stack);
        LotmPath path = LotmPath.of(player);
        if (path == null) {
            player.displayClientMessage(Component.translatable("message.realscale.lotm.no_path"), true);
            return InteractionResultHolder.fail(stack);
        }
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
        int sequence = LotmState.sequence(player);
        int cost = LotmBalance.focusCost(path.id()) + (9 - sequence) * 5;
        if (!LotmState.consume(player, cost)) {
            player.displayClientMessage(Component.translatable("message.realscale.lotm.low_spirit"), true);
            return InteractionResultHolder.fail(stack);
        }

        int discovered = 0;
        AABB range = new AABB(player.blockPosition()).inflate(sequence == 7 ? 20.0D : 14.0D);
        switch (path) {
            case SEER -> {
                // Seer senses, Clown coordination, Magician short fire-step / illusion.
                for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, range,
                        other -> other != player && other.getPersistentData().contains(LotmEvents.VARIANT))) {
                    apply(other, MobEffects.GLOWING, 140, 0);
                    discovered++;
                }
                if (sequence <= 8) apply(player, MobEffects.SLOW_FALLING, 180, 0);
                if (sequence <= 7) {
                    if (player.isShiftKeyDown() && phaseStep(level, player)) discovered++;
                    else apply(player, MobEffects.INVISIBILITY, 90, 0);
                }
            }
            case MYSTERY_PRYER -> {
                // Mystery observation is intentionally expensive only on player activation.
                BlockPos origin = player.blockPosition();
                for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-7, -5, -7),
                        origin.offset(7, 5, 7))) {
                    if (!level.hasChunkAt(pos)) continue;
                    var state = level.getBlockState(pos);
                    if (state.is(LotmContent.STAR_ORE.get()) ||
                            state.is(LotmContent.CURSED_ORE.get()) ||
                            state.is(LotmContent.GRAVE_ORE.get()) ||
                            state.is(LotmContent.MIDNIGHT_BLOOM.get()) ||
                            state.is(LotmContent.SPIRIT_QUARTZ_ORE.get()) ||
                            state.is(LotmContent.FACE_ROSE.get()) ||
                            state.is(LotmContent.SOUL_BELLFLOWER.get()) ||
                            state.is(LotmContent.BLOOD_ROSE.get()) ||
                            state.is(LotmContent.RELIC_CACHE.get())) discovered++;
                }
                if (sequence <= 8) apply(player, MobEffects.MOVEMENT_SPEED, 100, 0);
                if (sequence <= 7) {
                    apply(player, MobEffects.NIGHT_VISION, 260, 0);
                    for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, range,
                            other -> other != player && other.getPersistentData().contains(LotmEvents.VARIANT))) {
                        apply(other, MobEffects.GLOWING, 140, 0);
                        discovered++;
                    }
                }
            }
            case SLEEPLESS -> {
                for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, range,
                        other -> other instanceof Monster)) {
                    apply(other, MobEffects.GLOWING, 100, 0);
                    if (sequence <= 8) apply(other, MobEffects.MOVEMENT_SLOWDOWN, 120, 0);
                    if (sequence <= 7) apply(other, MobEffects.WEAKNESS, 100, 0);
                    discovered++;
                }
                if (sequence <= 8) apply(player, MobEffects.REGENERATION, 100, 0);
                if (sequence <= 7) apply(player, MobEffects.NIGHT_VISION, 280, 0);
            }
            case CORPSE_COLLECTOR -> {
                for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, range,
                        other -> other instanceof Zombie || other instanceof Skeleton || other instanceof Phantom)) {
                    apply(other, MobEffects.GLOWING, 150, 0);
                    if (sequence <= 8) apply(other, MobEffects.WEAKNESS, 120, 0);
                    discovered++;
                }
                if (sequence <= 8) apply(player, MobEffects.REGENERATION, 100, 0);
                if (sequence <= 7) {
                    apply(player, MobEffects.NIGHT_VISION, 250, 0);
                    for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, range,
                            other -> other != player && other.getPersistentData().contains(LotmEvents.VARIANT))) {
                        apply(other, MobEffects.GLOWING, 100, 0);
                        discovered++;
                    }
                }
            }
        }
        if (discovered > 0) LotmState.recordPractice(player, 2 + (9 - sequence));
        player.displayClientMessage(Component.translatable(
                "message.realscale.lotm.sense_spirit", discovered, LotmState.spirit(player)), true);
        player.getCooldowns().addCooldown(this, sequence == 7 ? 110 : sequence == 8 ? 135 : 160);
        return InteractionResultHolder.consume(stack);
    }
}
