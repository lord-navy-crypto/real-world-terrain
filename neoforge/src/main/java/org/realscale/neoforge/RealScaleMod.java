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
        event.register(Registries.DENSITY_FUNCTION_TYPE, helper -> {
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "dem_density"), RealDemDensity.MAP_CODEC);
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "ridge_erosion"), RidgeErosionDensity.MAP_CODEC);
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "strata"), StrataDensity.MAP_CODEC);
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "valley_temperature"), ValleyTemperatureDensity.MAP_CODEC);
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "latitude_temperature"), LatitudeTemperatureDensity.MAP_CODEC);
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "basin_temperature"), BasinTemperatureDensity.MAP_CODEC);
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "radial_latitude_temperature"), RadialLatitudeTemperatureDensity.MAP_CODEC);
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "climate_budget"), ClimateBudgetDensity.MAP_CODEC);
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "fantasy_end_islands"), FantasyEndIslandsDensity.MAP_CODEC);
        });
    }
}
