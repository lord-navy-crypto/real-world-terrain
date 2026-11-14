package org.realscale.neoforge.lotm;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.silvertide.player_abilities.api.AbilityUseType;
import net.silvertide.player_abilities.api.ActiveAbility;

/**
 * One of the eight active abilities shared with Player Abilities.
 * Level 1 is Sequence 9; level 2 is Sequence 8. The server-side LOTM state
 * remains authoritative for pathway, sequence, spirituality and digestion.
 */
public final class LotmPathwayAbility extends ActiveAbility {
    private final LotmPath pathway;
    private final int slot;

    public LotmPathwayAbility(LotmPath pathway, int slot) {
        if (slot < 1 || slot > 2) throw new IllegalArgumentException("LOTM skill slot must be 1 or 2");
        this.pathway = pathway;
        this.slot = slot;
    }

    public LotmPath pathway() { return pathway; }
    public int slot() { return slot; }

    private int sequenceForLevel(int level) {
        return switch (level) {
            case 1 -> 9;
            case 2 -> 8;
            default -> -1;
        };
    }

    @Override
    public AbilityUseType getUseType() {
        return AbilityUseType.INSTANT;
    }

    @Override
    public int getMaxLevel() {
        return 2;
    }

    @Override
    public ResourceLocation getCategory() {
        return ResourceLocation.fromNamespaceAndPath("realscale", "lotm_" + pathway.id());
    }

    @Override
    public int getCooldownTicks(int level) {
        int sequence = sequenceForLevel(level);
        return sequence < 0 ? 0 : LotmBalance.skillCooldownTicks(slot, sequence);
    }

    @Override
    public boolean canUse(ServerPlayer player, int level) {
        return getUseFailureMessage(player, level) == null;
    }

    @Override
    public Component getUseFailureMessage(ServerPlayer player, int level) {
        if (!LotmConfig.ENABLED.get() || !LotmConfig.SKILLS_ENABLED.get()) {
            return Component.translatable("message.realscale.lotm.disabled");
        }
        if (LotmPath.of(player) != pathway) {
            return Component.translatable("message.realscale.lotm.ability.wrong_path");
        }
        int sequence = sequenceForLevel(level);
        if (sequence < 0 || LotmState.sequence(player) != sequence) {
            return Component.translatable("message.realscale.lotm.ability.wrong_sequence");
        }
        int baseCost = LotmBalance.skillCost(pathway.id(), slot, sequence,
                LotmCurios.hasDivinationPendulum(player), LotmState.instability(player));
        int cost = LotmBalance.skillCostWithMastery(baseCost,
                LotmState.skillMastery(player, pathway, slot), LotmConfig.SKILL_COST_PERCENT.get());
        if (LotmState.spirit(player) < cost) {
            return Component.translatable("message.realscale.lotm.low_spirit");
        }
        return null;
    }

    @Override
    public void onUse(ServerPlayer player, int level) {
        int sequence = sequenceForLevel(level);
        if (sequence > 0) {
            LotmSkills.executeFromAbility(player, pathway, slot, sequence);
        }
    }
}
