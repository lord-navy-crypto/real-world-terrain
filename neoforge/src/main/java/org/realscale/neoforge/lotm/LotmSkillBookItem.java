package org.realscale.neoforge.lotm;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** A crafted pathway manual that improves one already-unlocked skill's mastery. */
public final class LotmSkillBookItem extends Item {
    private final LotmPath pathway;
    private final int slot;

    public LotmSkillBookItem(LotmPath pathway, int slot, Properties properties) {
        super(properties);
        if (pathway == null || slot < 1 || slot > 2) {
            throw new IllegalArgumentException("Invalid LOTM skill manual target");
        }
        this.pathway = pathway;
        this.slot = slot;
    }

    public LotmPath pathway() { return pathway; }
    public int slot() { return slot; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.sidedSuccess(stack, true);
        String result = LotmState.learnSkillManual(player, pathway, slot);
        if (!"learned".equals(result)) {
            player.displayClientMessage(Component.translatable(
                    "message.realscale.lotm.skill_manual." + result), true);
            return InteractionResultHolder.fail(stack);
        }
        if (!player.getAbilities().instabuild) stack.shrink(1);
        int mastery = LotmState.skillMastery(player, pathway, slot);
        player.displayClientMessage(Component.translatable(
                "message.realscale.lotm.skill_manual.learned",
                Component.translatable("lotm.realscale.path." + pathway.id()), slot, mastery), true);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
            List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.realscale.lotm.skill_manual.path",
                Component.translatable("lotm.realscale.path." + pathway.id())));
        tooltip.add(Component.translatable("tooltip.realscale.lotm.skill_manual.slot", slot));
        tooltip.add(Component.translatable("tooltip.realscale.lotm.skill_manual.effect"));
        tooltip.add(Component.translatable("tooltip.realscale.lotm.skill_manual.requirement"));
    }
}
