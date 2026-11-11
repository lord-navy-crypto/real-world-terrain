package org.realscale.neoforge.lotm;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Legacy focus item remains as an optional right-click alias for the primary skill key. */
public final class LotmFocusItem extends Item {
    public LotmFocusItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResultHolder.success(stack);
        return LotmSkills.activate(player, 1)
                ? InteractionResultHolder.consume(stack) : InteractionResultHolder.fail(stack);
    }
}
