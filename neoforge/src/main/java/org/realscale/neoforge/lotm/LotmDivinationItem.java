package org.realscale.neoforge.lotm;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Curios-compatible Seer specialization; active only while equipped. */
public final class LotmDivinationItem extends Item {
    public LotmDivinationItem(Properties properties) { super(properties); }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
            List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.realscale.lotm.divination_pendulum.effect"));
        tooltip.add(Component.translatable("tooltip.realscale.lotm.curio_exclusive_slot"));
    }
}
