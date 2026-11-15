package org.realscale.neoforge.lotm;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Curios-compatible Spirit Focus; its effect is applied by the server tick. */
public final class LotmFocusItem extends Item {
    public LotmFocusItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level,
            net.minecraft.world.entity.player.Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.sidedSuccess(stack, true);
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResultHolder.pass(stack);
        String result = LotmBoonState.activateInsight(serverPlayer);
        serverPlayer.displayClientMessage(
                Component.translatable("message.realscale.lotm.boon.insight." + result), true);
        return "no_boon".equals(result) || "revoked".equals(result)
                ? InteractionResultHolder.fail(stack)
                : InteractionResultHolder.sidedSuccess(stack, false);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
            List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.realscale.lotm.spirit_focus.effect"));
        tooltip.add(Component.translatable("tooltip.realscale.lotm.spirit_focus.boon_insight"));
        tooltip.add(Component.translatable("tooltip.realscale.lotm.curio_exclusive_slot"));
    }
}
