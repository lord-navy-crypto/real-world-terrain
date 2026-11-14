package org.realscale.neoforge.lotm;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.silvertide.player_abilities.api.Ability;
import net.silvertide.player_abilities.api.AbilityAPI;
import net.silvertide.player_abilities.api.event.AbilityGrantedEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Bridges LOTM progression to Player Abilities.
 * Only the realscale:lotm_pathway grant source is authoritative; Ability Tomes
 * and other external grant sources cannot bypass pathway/sequence requirements.
 */
public final class LotmPlayerAbilitySync {
    public static final ResourceLocation PATHWAY_SOURCE =
            ResourceLocation.fromNamespaceAndPath("realscale", "lotm_pathway");
    private static final String TOMES_REJECTED_KEY = "realscale_lotm_ability_tome_rejected";

    private LotmPlayerAbilitySync() {}

    public static void sync(ServerPlayer player) {
        sanitizeUnauthorizedGrants(player);
        Map<Ability, Integer> expected = new LinkedHashMap<>();
        LotmPath path = LotmPath.of(player);
        int sequence = LotmState.sequence(player);
        if (LotmConfig.ENABLED.get() && path != null && (sequence == 9 || sequence == 8)) {
            int level = sequence == 9 ? 1 : 2;
            expected.put(LotmPlayerAbilityRegistry.primary(path), level);
            expected.put(LotmPlayerAbilityRegistry.secondary(path), level);
        }

        Map<Ability, Integer> current =
                AbilityAPI.getGrantsBySource(player).getOrDefault(PATHWAY_SOURCE, Map.of());
        for (Ability ability : new ArrayList<>(current.keySet())) {
            if (!expected.containsKey(ability)) {
                AbilityAPI.revoke(player, PATHWAY_SOURCE, ability);
            }
        }
        for (Map.Entry<Ability, Integer> entry : expected.entrySet()) {
            if (!Objects.equals(current.get(entry.getKey()), entry.getValue())) {
                AbilityAPI.grant(player, PATHWAY_SOURCE, entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Reconcile the authoritative pathway grants every second. Besides removing
     * unauthorized grants, this restores the correct abilities after a live config
     * toggle or an integration that changed grant maps without firing an event.
     */
    public static boolean shouldAuditUnauthorizedGrants(int tickCount) {
        return Math.floorMod(tickCount, 20) == 0;
    }

    public static void periodicSync(ServerPlayer player) {
        if (shouldAuditUnauthorizedGrants(player.tickCount)) {
            sync(player);
        }
    }

    /** Full audit used on login and progression changes, plus the periodic fallback. */
    public static void sanitizeUnauthorizedGrants(ServerPlayer player) {
        Map<ResourceLocation, Map<Ability, Integer>> grants = AbilityAPI.getGrantsBySource(player);
        for (Map.Entry<ResourceLocation, Map<Ability, Integer>> sourceEntry :
                new ArrayList<>(grants.entrySet())) {
            if (PATHWAY_SOURCE.equals(sourceEntry.getKey())) continue;
            for (Ability ability : new ArrayList<>(sourceEntry.getValue().keySet())) {
                if (LotmPlayerAbilityRegistry.isLotmAbility(ability)
                        && AbilityAPI.revoke(player, sourceEntry.getKey(), ability)) {
                    player.getPersistentData().putBoolean(TOMES_REJECTED_KEY, true);
                }
            }
        }
    }

    public static void onAbilityGranted(AbilityGrantedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || PATHWAY_SOURCE.equals(event.getSource())
                || !LotmPlayerAbilityRegistry.isLotmAbility(event.getAbility())) {
            return;
        }
        if (AbilityAPI.revoke(player, event.getSource(), event.getAbility())) {
            player.getPersistentData().putBoolean(TOMES_REJECTED_KEY, true);
        }
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    public static void showPendingTomeMessage(ServerPlayer player) {
        if (player.getPersistentData().getBoolean(TOMES_REJECTED_KEY)) {
            player.getPersistentData().remove(TOMES_REJECTED_KEY);
            player.displayClientMessage(Component.translatable(
                    "message.realscale.lotm.ability.tome_rejected"), true);
        }
    }
}
