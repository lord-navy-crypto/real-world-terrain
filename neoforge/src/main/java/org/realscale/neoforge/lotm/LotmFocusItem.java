package org.realscale.neoforge.lotm;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Curios-compatible Spirit Focus; its effect is applied by the server tick. */
public final class LotmFocusItem extends Item {
    public LotmFocusItem(Properties properties) { super(properties); }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
            List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.realscale.lotm.spirit_focus.effect"));
        tooltip.add(Component.translatable("tooltip.realscale.lotm.curio_exclusive_slot"));
    }
}
