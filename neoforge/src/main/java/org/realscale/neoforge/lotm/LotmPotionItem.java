package org.realscale.neoforge.lotm;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** In-game fantasy progression, not a real-world consumable recipe. */
public final class LotmPotionItem extends Item {
    private final LotmPath pathway;
    public LotmPotionItem(LotmPath pathway, Properties properties) {
        super(properties);
        this.pathway = pathway;
    }
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!LotmConfig.ENABLED.get()) return InteractionResultHolder.fail(stack);
        if (level.isClientSide()) return InteractionResultHolder.success(stack);
        if (LotmPath.of(player) != null) {
            player.displayClientMessage(Component.translatable("message.realscale.lotm.already_path"), true);
            return InteractionResultHolder.fail(stack);
        }
        LotmState.initialize(player, pathway);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        player.displayClientMessage(Component.translatable("message.realscale.lotm.advanced", pathway.id()), false);
        return InteractionResultHolder.consume(stack);
    }
}
