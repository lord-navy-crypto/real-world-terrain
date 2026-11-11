package org.realscale.neoforge.lotm;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Both a brewed potion and an unprepared characteristic can carry the next sequence. */
public final class LotmPotionItem extends Item {
    private final LotmPath pathway;
    private final int sequence;
    private final boolean characteristic;

    public LotmPotionItem(LotmPath pathway, Properties properties) {
        this(pathway, 9, false, properties);
    }

    public LotmPotionItem(LotmPath pathway, int sequence, boolean characteristic, Properties properties) {
        super(properties);
        this.pathway = pathway;
        this.sequence = sequence;
        this.characteristic = characteristic;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!LotmConfig.ENABLED.get()) return InteractionResultHolder.fail(stack);
        if (level.isClientSide()) return InteractionResultHolder.success(stack);
        LotmPath current = LotmPath.of(player);
        String result = LotmProgression.validate(current == null ? null : current.id(),
                LotmState.sequence(player), pathway.id(), sequence, LotmState.practice(player),
                characteristic, LotmConfig.POTION_PRACTICE.get(), LotmConfig.CHARACTERISTIC_PRACTICE.get());
        if (!result.equals("ready")) {
            player.displayClientMessage(Component.translatable("message.realscale.lotm.advance." + result), true);
            return InteractionResultHolder.fail(stack);
        }
        LotmState.advance(player, pathway, sequence, characteristic);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        player.displayClientMessage(Component.translatable("message.realscale.lotm.advanced_rank",
                Component.translatable("lotm.realscale.rank." + pathway.id() + "." + sequence),
                sequence), false);
        if (characteristic) player.displayClientMessage(
                Component.translatable("message.realscale.lotm.direct_unease"), false);
        return InteractionResultHolder.consume(stack);
    }
}
