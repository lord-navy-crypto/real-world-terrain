package org.realscale.neoforge.lotm;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Opt-in biome-modifier feature; no RealScale density or climate changes. */
public final class LotmWorldgen {
    private LotmWorldgen() {}
    public static void register(RegisterEvent event) {
        event.register(Registries.FEATURE, helper ->
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "lotm_relic_site"),
                new LotmRelicSiteFeature()));
    }
}
