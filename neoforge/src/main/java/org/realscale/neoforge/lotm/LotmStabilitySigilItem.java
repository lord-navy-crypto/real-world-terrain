package org.realscale.neoforge.lotm;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** A crafted, single-use aid that reverses one point of direct-characteristic instability. */
public final class LotmStabilitySigilItem extends Item {
    public LotmStabilitySigilItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
            List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.realscale.lotm.stability_sigil.effect"));
        tooltip.add(Component.translatable("tooltip.realscale.lotm.stability_sigil.limit"));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!LotmConfig.ENABLED.get()) {
            player.displayClientMessage(Component.translatable("message.realscale.lotm.disabled"), true);
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide()) return InteractionResultHolder.sidedSuccess(stack, true);
        int reduced = LotmState.reduceInstability(player, 1);
        if (reduced <= 0) {
            player.displayClientMessage(Component.translatable(
                    "message.realscale.lotm.stability_sigil.no_instability"), true);
            return InteractionResultHolder.fail(stack);
        }
        if (!player.getAbilities().instabuild) stack.shrink(1);
        player.displayClientMessage(Component.translatable(
                "message.realscale.lotm.stability_sigil.restored",
                LotmState.instability(player)), true);
        return InteractionResultHolder.sidedSuccess(stack, false);
    }
}
