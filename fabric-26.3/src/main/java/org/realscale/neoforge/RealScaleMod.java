package org.realscale.neoforge;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.realscale.neoforge.compat.dhchunky.DhChunkyGuardCommands;

/** Fabric 1.21.1 entrypoint. Registry codecs and features mirror the current
 * 1.21.1 NeoForge implementation; loader-specific hooks remain Fabric-native.
 */
public final class RealScaleMod implements ModInitializer {
    @Override
    public void onInitialize() {
        
        // 26.3 separates statically registered feature codecs from datapack feature instances.
        Registry.register(BuiltInRegistries.FEATURE_TYPE, Identifier.fromNamespaceAndPath("realscale", "river_channel"), RiverChannelFeature.MAP_CODEC);
        Registry.register(BuiltInRegistries.FEATURE_TYPE, Identifier.fromNamespaceAndPath("realscale", "vanilla_habitat"), VanillaHabitatFeature.MAP_CODEC);
        Registry.register(BuiltInRegistries.FEATURE_TYPE, Identifier.fromNamespaceAndPath("realscale", "ground_microstructure"), GroundMicrostructureFeature.MAP_CODEC);
        Registry.register(BuiltInRegistries.FEATURE_TYPE, Identifier.fromNamespaceAndPath("realscale", "sky_island"), SkyIslandFeature.MAP_CODEC);
        Registry.register(BuiltInRegistries.FEATURE_TYPE, Identifier.fromNamespaceAndPath("realscale", "habitat_patch"), HabitatPatchFeature.MAP_CODEC);
        Registry.register(BuiltInRegistries.FEATURE_TYPE, Identifier.fromNamespaceAndPath("realscale", "grand_ecology"), GrandEcologyFeature.MAP_CODEC);
        Registry.register(BuiltInRegistries.FEATURE_TYPE, Identifier.fromNamespaceAndPath("realscale", "cave_wonder_network"), CaveWonderNetworkFeature.MAP_CODEC);
        Registry.register(BuiltInRegistries.FEATURE_TYPE, Identifier.fromNamespaceAndPath("realscale", "geo_wonder_region"), GeoWonderRegionFeature.MAP_CODEC);
        Registry.register(BuiltInRegistries.FEATURE_TYPE, Identifier.fromNamespaceAndPath("realscale", "earth_wonder"), EarthWonderFeature.MAP_CODEC);
        Registry.register(BuiltInRegistries.FEATURE_TYPE, Identifier.fromNamespaceAndPath("realscale", "earth_systems_accent"), EarthSystemsAccentFeature.MAP_CODEC);
        Registry.register(BuiltInRegistries.FEATURE_TYPE, Identifier.fromNamespaceAndPath("realscale", "cave_marine_habitat"), CaveMarineHabitatFeature.MAP_CODEC);
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, Identifier.fromNamespaceAndPath("realscale", "dem_density"), RealDemDensity.MAP_CODEC);
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, Identifier.fromNamespaceAndPath("realscale", "dem_density_seamless"), RealDemSeamlessDensity.MAP_CODEC);
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, Identifier.fromNamespaceAndPath("realscale", "measured_horizontal_memo"), MeasuredHorizontalMemoDensity.MAP_CODEC);
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, Identifier.fromNamespaceAndPath("realscale", "grand_end_density"), GrandEndDensity.MAP_CODEC);
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, Identifier.fromNamespaceAndPath("realscale", "grand_nether_caverns"), GrandNetherCaverns.MAP_CODEC);
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, Identifier.fromNamespaceAndPath("realscale", "grand_overworld_caverns"), GrandOverworldCaverns.MAP_CODEC);
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, Identifier.fromNamespaceAndPath("realscale", "range_min_exact"), RangeMinDensity.MAP_CODEC);
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, Identifier.fromNamespaceAndPath("realscale", "mountain_relief"), MountainReliefDensity.MAP_CODEC);
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, Identifier.fromNamespaceAndPath("realscale", "terrain_archetype"), TerrainArchetypeDensity.MAP_CODEC);
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, Identifier.fromNamespaceAndPath("realscale", "ridge_erosion"), RidgeErosionDensity.MAP_CODEC);
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, Identifier.fromNamespaceAndPath("realscale", "strata"), StrataDensity.MAP_CODEC);
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, Identifier.fromNamespaceAndPath("realscale", "valley_temperature"), ValleyTemperatureDensity.MAP_CODEC);
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, Identifier.fromNamespaceAndPath("realscale", "latitude_temperature"), LatitudeTemperatureDensity.MAP_CODEC);
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, Identifier.fromNamespaceAndPath("realscale", "basin_temperature"), BasinTemperatureDensity.MAP_CODEC);
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, Identifier.fromNamespaceAndPath("realscale", "mountain_basin_temperature"), MountainBasinTemperatureDensity.MAP_CODEC);
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, Identifier.fromNamespaceAndPath("realscale", "radial_latitude_temperature"), RadialLatitudeTemperatureDensity.MAP_CODEC);
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, Identifier.fromNamespaceAndPath("realscale", "climate_budget"), ClimateBudgetDensity.MAP_CODEC);
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, Identifier.fromNamespaceAndPath("realscale", "fantasy_end_islands"), FantasyEndIslandsDensity.MAP_CODEC);
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, Identifier.fromNamespaceAndPath("realscale", "fantasy_nether_caverns"), FantasyNetherCavernsDensity.MAP_CODEC);
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, Identifier.fromNamespaceAndPath("realscale", "fantasy_end_archipelago"), FantasyEndArchipelagoDensity.MAP_CODEC);
        Registry.register(BuiltInRegistries.BIOME_SOURCE, Identifier.fromNamespaceAndPath("realscale", "geo_wonders_opt_in"), GeoWondersBiomeSource.CODEC);
        Registry.register(BuiltInRegistries.BIOME_SOURCE, Identifier.fromNamespaceAndPath("realscale", "natural_ecotones"), NaturalEcotoneBiomeSource.CODEC);
        Registry.register(BuiltInRegistries.BIOME_SOURCE, Identifier.fromNamespaceAndPath("realscale", "sky_ocean"), SkyOceanBiomeSource.CODEC);
        Registry.register(BuiltInRegistries.BIOME_SOURCE, Identifier.fromNamespaceAndPath("realscale", "cave_depth_habitats"), CaveDepthBiomeSource.CODEC);
        RealScaleStructurePacks.register();
        FabricBiomeModifiers.register();
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, access, environment) -> DhChunkyGuardCommands.registerCommands(dispatcher));
        FabricServerSmoke.register();
        System.out.println("[RealScale] Fabric 1.21.1 registries initialized: 94 codecs/features.");
    }
}
