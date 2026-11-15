package org.realscale.neoforge.lotm;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.TooltipFlag;
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
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
            List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.realscale.lotm.potion.path",
                Component.translatable("lotm.realscale.path." + pathway.id())));
        tooltip.add(Component.translatable("tooltip.realscale.lotm.potion.rank", sequence));
        tooltip.add(Component.translatable("tooltip.realscale.lotm.potion.backlash"));
        if (characteristic) {
            tooltip.add(Component.translatable("tooltip.realscale.lotm.potion.characteristic"));
            tooltip.add(Component.translatable("tooltip.realscale.lotm.potion.direct_risk"));
            if (sequence == 8) {
                tooltip.add(Component.translatable("tooltip.realscale.lotm.potion.characteristic_requirement",
                        LotmConfig.CHARACTERISTIC_PRACTICE.get()));
            } else {
                tooltip.add(Component.translatable("tooltip.realscale.lotm.potion.first_characteristic"));
            }
        } else if (sequence == 9) {
            tooltip.add(Component.translatable("tooltip.realscale.lotm.potion.first_potion"));
        } else {
            tooltip.add(Component.translatable("tooltip.realscale.lotm.potion.prepared"));
            tooltip.add(Component.translatable("tooltip.realscale.lotm.potion.prepared_requirement",
                    LotmConfig.POTION_PRACTICE.get()));
        }
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return DRINK_TICKS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // Invalid rank transitions are rejected immediately. Pathway mismatch and
        // incomplete digestion are warned before the player chooses to complete use.
        // The server revalidates the transition in finishUsingItem().
        if (!level.isClientSide()) {
            String validation = LotmState.validateAdvance(player, pathway, sequence, characteristic);
            if (!"ready".equals(validation)) {
                boolean backlashEligible = "path".equals(validation) || "digestion".equals(validation);
                if (!backlashEligible) {
                    player.displayClientMessage(Component.translatable(
                            "message.realscale.lotm.advance." + validation), true);
                    return InteractionResultHolder.fail(stack);
                }
                player.displayClientMessage(Component.translatable(
                        "message.realscale.lotm.potion.backlash_warning"), true);
            }
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!(entity instanceof Player player) || level.isClientSide()) return stack;

        // Recheck inside the authoritative state transition: the server config
        // may have changed while the player was completing the drink animation.
        String result = LotmState.advance(player, pathway, sequence, characteristic);
        if (!"ready".equals(result)) {
            if ("path".equals(result) || "digestion".equals(result)) {
                if (player.getAbilities().instabuild) {
                    player.displayClientMessage(Component.translatable(
                            "message.realscale.lotm.advance." + result), true);
                    return stack;
                }
                boolean wrongPathway = "path".equals(result);
                int oldSanity = LotmState.sanity(player);
                int sanityLoss = LotmBalance.potionBacklashSanityLoss(
                        wrongPathway, LotmState.instability(player));
                LotmState.adjustSanity(player, -sanityLoss);
                float requestedDamage = LotmBalance.potionBacklashDamage(
                        wrongPathway, LotmState.instability(player));
                float safeDamage = Math.min(requestedDamage, Math.max(0.0F, player.getHealth() - 1.0F));
                float beforeHealth = player.getHealth();
                if (safeDamage > 0.0F) player.hurt(player.damageSources().magic(), safeDamage);
                float actualDamage = Math.max(0.0F, beforeHealth - player.getHealth());
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 60, 0, true, false));
                if (!player.getAbilities().instabuild) stack.shrink(1);
                LotmActing.recordJournalEvent(player, "potion", "incomplete",
                        pathway.id() + ":" + sequence + (characteristic ? ":characteristic" : ":prepared"));
                player.displayClientMessage(Component.translatable(
                        "message.realscale.lotm.potion.backlash",
                        Math.max(0, oldSanity - LotmState.sanity(player)), actualDamage), false);
                return stack;
            }
            player.displayClientMessage(Component.translatable("message.realscale.lotm.advance." + result), true);
            return stack;
        }
        if (!player.getAbilities().instabuild) stack.shrink(1);
        LotmActing.recordJournalEvent(player, "potion", "verified",
                pathway.id() + ":" + sequence + (characteristic ? ":characteristic" : ":prepared"));
        LotmActing.recordJournalEvent(player, "pathway", "verified", pathway.id() + ":" + sequence);
        player.displayClientMessage(Component.translatable("message.realscale.lotm.advanced_rank",
                Component.translatable("lotm.realscale.rank." + pathway.id() + "." + sequence),
                sequence), false);
        // An actual in-world advancement cue, played only after the server accepted the potion.
        if (level instanceof ServerLevel server) {
            if (LotmConfig.APPEARANCE_EFFECTS.get() && LotmPreferences.particlesEnabled(player)) {
                server.sendParticles(characteristic ? ParticleTypes.SOUL : ParticleTypes.ENCHANT,
                        player.getX(), player.getY() + 1.0D, player.getZ(),
                        22, 0.45D, 0.7D, 0.45D, 0.03D);
            }
            if (LotmPreferences.soundEnabled(player)) {
                server.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP,
                        SoundSource.PLAYERS, 0.65F, characteristic ? 0.85F : 1.15F);
            }
        }
        if (characteristic) {
            player.displayClientMessage(Component.translatable("message.realscale.lotm.direct_unease"), false);
        }
        return stack;
    }
}
