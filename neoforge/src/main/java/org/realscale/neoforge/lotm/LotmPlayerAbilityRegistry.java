package org.realscale.neoforge.lotm;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.silvertide.player_abilities.api.Ability;
import net.silvertide.player_abilities.api.AbilityRegistry;
import java.util.List;

/** Registers the eight sequence-gated LOTM skills with Player Abilities. */
public final class LotmPlayerAbilityRegistry {
    public static final LotmPathwayAbility SEER_PRIMARY = new LotmPathwayAbility(LotmPath.SEER, 1);
    public static final LotmPathwayAbility SEER_SECONDARY = new LotmPathwayAbility(LotmPath.SEER, 2);
    public static final LotmPathwayAbility PRYER_PRIMARY = new LotmPathwayAbility(LotmPath.MYSTERY_PRYER, 1);
    public static final LotmPathwayAbility PRYER_SECONDARY = new LotmPathwayAbility(LotmPath.MYSTERY_PRYER, 2);
    public static final LotmPathwayAbility SLEEPLESS_PRIMARY = new LotmPathwayAbility(LotmPath.SLEEPLESS, 1);
    public static final LotmPathwayAbility SLEEPLESS_SECONDARY = new LotmPathwayAbility(LotmPath.SLEEPLESS, 2);
    public static final LotmPathwayAbility CORPSE_PRIMARY = new LotmPathwayAbility(LotmPath.CORPSE_COLLECTOR, 1);
    public static final LotmPathwayAbility CORPSE_SECONDARY = new LotmPathwayAbility(LotmPath.CORPSE_COLLECTOR, 2);

    private static final List<LotmPathwayAbility> ALL = List.of(
            SEER_PRIMARY, SEER_SECONDARY, PRYER_PRIMARY, PRYER_SECONDARY,
            SLEEPLESS_PRIMARY, SLEEPLESS_SECONDARY, CORPSE_PRIMARY, CORPSE_SECONDARY);

    private LotmPlayerAbilityRegistry() {}

    public static void register(RegisterEvent event) {
        event.register(AbilityRegistry.ABILITY_REGISTRY_KEY, helper -> {
            helper.register(id("lotm_seer_primary"), SEER_PRIMARY);
            helper.register(id("lotm_seer_secondary"), SEER_SECONDARY);
            helper.register(id("lotm_mystery_pryer_primary"), PRYER_PRIMARY);
            helper.register(id("lotm_mystery_pryer_secondary"), PRYER_SECONDARY);
            helper.register(id("lotm_sleepless_primary"), SLEEPLESS_PRIMARY);
            helper.register(id("lotm_sleepless_secondary"), SLEEPLESS_SECONDARY);
            helper.register(id("lotm_corpse_collector_primary"), CORPSE_PRIMARY);
            helper.register(id("lotm_corpse_collector_secondary"), CORPSE_SECONDARY);
        });
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("realscale", path);
    }

    public static LotmPathwayAbility primary(LotmPath path) {
        return switch (path) {
            case SEER -> SEER_PRIMARY;
            case MYSTERY_PRYER -> PRYER_PRIMARY;
            case SLEEPLESS -> SLEEPLESS_PRIMARY;
            case CORPSE_COLLECTOR -> CORPSE_PRIMARY;
        };
    }

    public static LotmPathwayAbility secondary(LotmPath path) {
        return switch (path) {
            case SEER -> SEER_SECONDARY;
            case MYSTERY_PRYER -> PRYER_SECONDARY;
            case SLEEPLESS -> SLEEPLESS_SECONDARY;
            case CORPSE_COLLECTOR -> CORPSE_SECONDARY;
        };
    }

    public static List<LotmPathwayAbility> all() {
        return ALL;
    }

    public static boolean isLotmAbility(Ability ability) {
        return ALL.contains(ability);
    }
}
