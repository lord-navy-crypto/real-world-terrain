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
    }
    private void registerDensity(RegisterEvent event) {
        event.register(Registries.DENSITY_FUNCTION_TYPE, helper -> {
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "dem_density"), RealDemDensity.MAP_CODEC);
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "grand_end_density"), GrandEndDensity.MAP_CODEC);
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "grand_nether_caverns"), GrandNetherCaverns.MAP_CODEC);
        });
        event.register(Registries.BIOME_SOURCE, helper ->
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "natural_ecotones"),
                    NaturalEcotoneBiomeSource.CODEC)
        );
        event.register(Registries.FEATURE, helper -> {
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "fallen_oak_log"),
                    new GroundMicrostructureFeature(GroundMicrostructureFeature.Kind.OAK_LOG));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "fallen_spruce_log"),
                    new GroundMicrostructureFeature(GroundMicrostructureFeature.Kind.SPRUCE_LOG));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "woodland_hummock"),
                    new GroundMicrostructureFeature(GroundMicrostructureFeature.Kind.FOREST_HUMMOCK));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "upland_scree"),
                    new GroundMicrostructureFeature(GroundMicrostructureFeature.Kind.UPLAND_SCREE));
        });
    }
}
