package org.realscale.neoforge.lotm;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** A reusable low-sequence ability trigger; no spellcaster or combat subsystem required. */
public final class LotmFocusItem extends Item {
    public LotmFocusItem(Properties properties) { super(properties); }

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
        int discovered = 0;
        AABB range = new AABB(player.blockPosition()).inflate(16.0D);
        switch (path) {
            case SEER -> {
                for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, range,
                        other -> other != player && other.getPersistentData().contains(LotmEvents.VARIANT))) {
                    other.addEffect(new MobEffectInstance(MobEffects.GLOWING, 120, 0, false, false));
                    discovered++;
                }
            }
            case MYSTERY_PRYER -> {
                BlockPos origin = player.blockPosition();
                // On-demand, capped radius; never scan large chunks on each game tick.
                for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-7, -5, -7), origin.offset(7, 5, 7))) {
                    var state = level.getBlockState(pos);
                    if (state.is(LotmContent.STAR_ORE.get()) || state.is(LotmContent.CURSED_ORE.get())
                            || state.is(LotmContent.GRAVE_ORE.get()) || state.is(LotmContent.MIDNIGHT_BLOOM.get())) {
                        discovered++;
                    }
                }
            }
            case SLEEPLESS -> {
                for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, range,
                        other -> other != player && other instanceof net.minecraft.world.entity.monster.Monster)) {
                    other.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0, false, false));
                    discovered++;
                }
            }
            case CORPSE_COLLECTOR -> {
                for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, range,
                        other -> other != player && (other instanceof Zombie || other instanceof Skeleton || other instanceof Phantom))) {
                    other.addEffect(new MobEffectInstance(MobEffects.GLOWING, 120, 0, false, false));
                    discovered++;
                }
            }
        }
        player.displayClientMessage(Component.translatable("message.realscale.lotm.sense", discovered), true);
        player.getCooldowns().addCooldown(this, 160);
        return InteractionResultHolder.consume(stack);
    }
}
