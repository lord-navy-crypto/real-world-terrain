package org.realscale.neoforge.lotm;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/** A prepared potion or unprepared characteristic; consumption is server-authoritative. */
public final class LotmPotionItem extends Item {
    private static final int DRINK_TICKS = 32;

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

    public LotmPath pathway() { return pathway; }
    public int sequence() { return sequence; }
    public boolean isCharacteristic() { return characteristic; }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return DRINK_TICKS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!LotmConfig.ENABLED.get()) {
            player.displayClientMessage(Component.translatable("message.realscale.lotm.disabled"), true);
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!(entity instanceof Player player) || level.isClientSide()) return stack;
        if (!LotmConfig.ENABLED.get()) return stack;

        LotmPath current = LotmPath.of(player);
        String result = LotmProgression.validate(current == null ? null : current.id(),
                LotmState.sequence(player), pathway.id(), sequence, LotmState.practice(player),
                characteristic, LotmConfig.POTION_PRACTICE.get(), LotmConfig.CHARACTERISTIC_PRACTICE.get());
        if (!result.equals("ready")) {
            player.displayClientMessage(Component.translatable("message.realscale.lotm.advance." + result), true);
            return stack;
        }

        LotmState.advance(player, pathway, sequence, characteristic);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        player.displayClientMessage(Component.translatable("message.realscale.lotm.advanced_rank",
                Component.translatable("lotm.realscale.rank." + pathway.id() + "." + sequence),
                sequence), false);
        if (characteristic) {
            player.displayClientMessage(Component.translatable("message.realscale.lotm.direct_unease"), false);
        }
        return stack;
    }
}
