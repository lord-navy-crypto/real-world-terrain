package org.realscale.neoforge;

import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;

@Mod("realscale")
public final class RealScaleMod {
    public RealScaleMod(IEventBus bus) {
        bus.addListener(this::registerDensity);
        bus.addListener(this::registerRiverFeature);
    }
    private void registerRiverFeature(RegisterEvent event) {
        event.register(Registries.FEATURE, helper ->
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "river_channel"),
                new RiverChannelFeature())
        );
    }

    private void registerDensity(RegisterEvent event) {
        event.register(Registries.DENSITY_FUNCTION_TYPE, helper ->
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "dem_density"), RealDemDensity.MAP_CODEC);
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "ridge_erosion"), RidgeErosionDensity.MAP_CODEC);
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "strata"), StrataDensity.MAP_CODEC)
        );
    }
}
