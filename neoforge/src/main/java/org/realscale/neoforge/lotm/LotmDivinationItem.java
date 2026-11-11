package org.realscale.neoforge.lotm;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Legacy pendulum remains as a Seer-only right-click alias for the secondary skill key. */
public final class LotmDivinationItem extends Item {
    public LotmDivinationItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResultHolder.success(stack);
        return LotmSkills.activateSeerDivination(player)
                ? InteractionResultHolder.consume(stack) : InteractionResultHolder.fail(stack);
    }
}
