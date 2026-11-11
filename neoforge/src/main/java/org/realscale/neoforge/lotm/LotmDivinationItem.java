package org.realscale.neoforge.lotm;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Coarse local dowsing rather than guaranteed prophecy.
 * Server scans only loaded blocks in a small bounded area when activated.
 */
public final class LotmDivinationItem extends Item {
    public LotmDivinationItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!LotmConfig.ENABLED.get()) return InteractionResultHolder.fail(stack);
        if (level.isClientSide()) return InteractionResultHolder.success(stack);
        if (LotmPath.of(player) != LotmPath.SEER) {
            player.displayClientMessage(Component.translatable("message.realscale.lotm.seer_only"), true);
            return InteractionResultHolder.fail(stack);
        }
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
        if (!LotmState.consume(player, LotmBalance.DIVINATION_COST)) {
            player.displayClientMessage(Component.translatable("message.realscale.lotm.low_spirit"), true);
            return InteractionResultHolder.fail(stack);
        }

        BlockPos origin = player.blockPosition();
        BlockPos nearest = null;
        double distance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-10, -6, -10), origin.offset(10, 6, 10))) {
            if (!level.hasChunkAt(pos)) continue;
            var block = level.getBlockState(pos);
            if (!(block.is(LotmContent.STAR_ORE.get()) ||
                    block.is(LotmContent.CURSED_ORE.get()) ||
                    block.is(LotmContent.GRAVE_ORE.get()) ||
                    block.is(LotmContent.MIDNIGHT_BLOOM.get()))) continue;
            double current = origin.distSqr(pos);
            if (current < distance) {
                nearest = pos.immutable();
                distance = current;
            }
        }
        if (nearest == null) {
            player.displayClientMessage(Component.translatable("message.realscale.lotm.unclear_omen"), true);
        } else {
            String direction = LotmBalance.bearing(nearest.getX() - origin.getX(),
                    nearest.getZ() - origin.getZ(), nearest.getY() - origin.getY());
            player.displayClientMessage(Component.translatable("message.realscale.lotm.omen",
                    Component.translatable("message.realscale.lotm.bearing." + direction)), true);
            LotmActing.recordResearch(player, 3);
        }
        player.getCooldowns().addCooldown(this, 200);
        return InteractionResultHolder.consume(stack);
    }
}
