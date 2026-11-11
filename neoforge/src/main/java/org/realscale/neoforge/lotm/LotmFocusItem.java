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

/**
 * Sequence 9/8 pathway focus abilities. All effects are server-authoritative and bounded.
 */
public final class LotmFocusItem extends Item {
    public LotmFocusItem(Properties properties) { super(properties); }

    private static void apply(LivingEntity target, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect,
            int ticks, int amplifier) {
        target.addEffect(new MobEffectInstance(effect, ticks, amplifier, false, false));
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
        AABB range = new AABB(player.blockPosition()).inflate(14.0D);
        switch (path) {
            case SEER -> {
                // Seer senses and Clown coordination.
                for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, range,
                        other -> other != player && other.getPersistentData().contains(LotmEvents.VARIANT))) {
                    apply(other, MobEffects.GLOWING, 140, 0);
                    discovered++;
                }
                if (sequence <= 8) apply(player, MobEffects.SLOW_FALLING, 180, 0);
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
            }
            case SLEEPLESS -> {
                for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, range,
                        other -> other instanceof Monster)) {
                    apply(other, MobEffects.GLOWING, 100, 0);
                    if (sequence <= 8) apply(other, MobEffects.MOVEMENT_SLOWDOWN, 120, 0);
                    discovered++;
                }
                if (sequence <= 8) apply(player, MobEffects.REGENERATION, 100, 0);
            }
            case CORPSE_COLLECTOR -> {
                for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, range,
                        other -> other instanceof Zombie || other instanceof Skeleton || other instanceof Phantom)) {
                    apply(other, MobEffects.GLOWING, 150, 0);
                    if (sequence <= 8) apply(other, MobEffects.WEAKNESS, 120, 0);
                    discovered++;
                }
                if (sequence <= 8) apply(player, MobEffects.REGENERATION, 100, 0);
            }
        }
        if (discovered > 0) {
            LotmActing.recordSkillUse(player, 2 + (9 - sequence));
            LotmActing.recordResearch(player, Math.min(4, discovered));
        }
        player.displayClientMessage(Component.translatable(
                "message.realscale.lotm.sense_spirit", discovered, LotmState.spirit(player)), true);
        player.getCooldowns().addCooldown(this, sequence == 8 ? 135 : 160);
        return InteractionResultHolder.consume(stack);
    }
}
