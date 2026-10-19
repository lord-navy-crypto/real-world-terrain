package org.realscale.neoforge;

import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;

@Mod("realscale")
public final class RealScaleMod {
    public RealScaleMod(IEventBus bus) {
        RealScaleVehicles.register(bus);
        bus.addListener(RealScaleStructurePacks::register);
        bus.addListener(this::registerDensity);
    }
    private void registerDensity(RegisterEvent event) {
        event.register(Registries.DENSITY_FUNCTION_TYPE, helper -> {
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "dem_density"), RealDemDensity.MAP_CODEC);
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "dem_density_seamless"), RealDemSeamlessDensity.MAP_CODEC);
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "measured_horizontal_memo"), MeasuredHorizontalMemoDensity.MAP_CODEC);
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "grand_end_density"), GrandEndDensity.MAP_CODEC);
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "grand_nether_caverns"), GrandNetherCaverns.MAP_CODEC);
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "grand_overworld_caverns"), GrandOverworldCaverns.MAP_CODEC);
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "range_min_exact"), RangeMinDensity.MAP_CODEC);
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "mountain_relief"), MountainReliefDensity.MAP_CODEC);
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "regional_landform"), RegionalLandformDensity.MAP_CODEC);
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "regional_landform_diverse_v2"), RegionalLandformDensity.V2_MAP_CODEC);
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "regional_landform_global_v3"), RegionalGlobalLandformDensity.MAP_CODEC);
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "measured_rare_hybrid"), MeasuredHybridRareDensity.MAP_CODEC);
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "marine_seafloor"), MarineSeafloorDensity.MAP_CODEC);
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "ridge_erosion"), RidgeErosionDensity.MAP_CODEC);
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "strata"), StrataDensity.MAP_CODEC);
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "valley_temperature"), ValleyTemperatureDensity.MAP_CODEC);
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "latitude_temperature"), LatitudeTemperatureDensity.MAP_CODEC);
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "basin_temperature"), BasinTemperatureDensity.MAP_CODEC);
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "mountain_basin_temperature"), MountainBasinTemperatureDensity.MAP_CODEC);
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "radial_latitude_temperature"), RadialLatitudeTemperatureDensity.MAP_CODEC);
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "climate_budget"), ClimateBudgetDensity.MAP_CODEC);
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "fantasy_end_islands"), FantasyEndIslandsDensity.MAP_CODEC);
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "fantasy_nether_caverns"), FantasyNetherCavernsDensity.MAP_CODEC);
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "fantasy_end_archipelago"), FantasyEndArchipelagoDensity.MAP_CODEC);
        });
        event.register(Registries.BIOME_SOURCE, helper ->
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "geo_wonders_opt_in"),
                    GeoWondersBiomeSource.CODEC)
        );
        event.register(Registries.BIOME_SOURCE, helper ->
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "natural_ecotones"),
                    NaturalEcotoneBiomeSource.CODEC)
        );
        // New codec ID only: original NaturalEcotoneBiomeSource.CODEC is immutable.
        event.register(Registries.BIOME_SOURCE, helper ->
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "natural_ecotones_diverse_v2"),
                    NaturalEcotoneBiomeSource.DIVERSE_CODEC)
        );
        event.register(Registries.BIOME_SOURCE, helper ->
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "natural_ecotones_diverse_v3"),
                    NaturalEcotoneBiomeSource.DIVERSE_V3_CODEC)
        );
        event.register(Registries.BIOME_SOURCE, helper ->
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "cross_realm_showcase"),
                    CrossRealmShowcaseBiomeSource.CODEC)
        );
        event.register(Registries.BIOME_SOURCE, helper ->
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "cross_realm_invasion"),
                    CrossRealmBiomeSource.CODEC)
        );
        event.register(Registries.BIOME_SOURCE, helper ->
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "cross_realm_spawn3"),
                    CrossRealmBiomeSource.SPAWN3_CODEC)
        );
        event.register(Registries.BIOME_SOURCE, helper ->
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "end_accents"),
                    EndAccentBiomeSource.CODEC)
        );
        event.register(Registries.BIOME_SOURCE, helper ->
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "sky_ocean"),
                    SkyOceanBiomeSource.CODEC)
        );
        event.register(Registries.FEATURE, helper -> {
            // Terrain Lab's bank-protected, source-only river channels. The
            // feature itself rejects vanilla/foreign noise settings.
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "river_channel"),
                    new RiverChannelFeature());
            // Optional structures from vanilla blocks; datapack controls where.
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "fallen_timber"),
                    new VanillaHabitatFeature(VanillaHabitatFeature.Habitat.FALLEN_TIMBER));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "riparian_reeds"),
                    new VanillaHabitatFeature(VanillaHabitatFeature.Habitat.RIPARIAN_REEDS));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "fallen_oak_log"),
                    new GroundMicrostructureFeature(GroundMicrostructureFeature.Kind.OAK_LOG));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "fallen_spruce_log"),
                    new GroundMicrostructureFeature(GroundMicrostructureFeature.Kind.SPRUCE_LOG));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "woodland_hummock"),
                    new GroundMicrostructureFeature(GroundMicrostructureFeature.Kind.FOREST_HUMMOCK));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "upland_scree"),
                    new GroundMicrostructureFeature(GroundMicrostructureFeature.Kind.UPLAND_SCREE));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "coastal_driftwood"),
                    new GroundMicrostructureFeature(GroundMicrostructureFeature.Kind.COASTAL_DRIFTWOOD));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "invasion_patch_nether_overworld"),
                    new CrossRealmTerrainPatchFeature(CrossRealmTerrainPatchFeature.Kind.NETHER_IN_OVERWORLD));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "invasion_patch_end_overworld"),
                    new CrossRealmTerrainPatchFeature(CrossRealmTerrainPatchFeature.Kind.END_IN_OVERWORLD));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "invasion_patch_overworld_nether"),
                    new CrossRealmTerrainPatchFeature(CrossRealmTerrainPatchFeature.Kind.OVERWORLD_IN_NETHER));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "invasion_patch_end_nether"),
                    new CrossRealmTerrainPatchFeature(CrossRealmTerrainPatchFeature.Kind.END_IN_NETHER));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "invasion_patch_overworld_end"),
                    new CrossRealmTerrainPatchFeature(CrossRealmTerrainPatchFeature.Kind.OVERWORLD_IN_END));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "invasion_patch_nether_end"),
                    new CrossRealmTerrainPatchFeature(CrossRealmTerrainPatchFeature.Kind.NETHER_IN_END));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "invasion_nether"),
                    new DimensionalInvasionFeature(DimensionalInvasionFeature.Invader.NETHER));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "invasion_end"),
                    new DimensionalInvasionFeature(DimensionalInvasionFeature.Invader.END));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "invasion_overworld"),
                    new DimensionalInvasionFeature(DimensionalInvasionFeature.Invader.OVERWORLD));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "end_chorus_gardens"),
                    new EndAccentFeature(EndAccentFeature.Kind.CHORUS_GARDENS));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "end_crystal_barrens"),
                    new EndAccentFeature(EndAccentFeature.Kind.CRYSTAL_BARRENS));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "end_shattered_glades"),
                    new EndAccentFeature(EndAccentFeature.Kind.SHATTERED_GLADES));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "sky_blossom_island"),
                    new SkyIslandFeature());
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "cave_moss_nooks"),
                    new HabitatPatchFeature(HabitatPatchFeature.Kind.CAVE_MOSS));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "cave_stalagmites"),
                    new HabitatPatchFeature(HabitatPatchFeature.Kind.CAVE_DRIPSTONE));
            // Registered experimental marine decorations are not added to normal
            // ocean biomes: native seabed and ocean structures stay untouched.
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "ocean_seagrass_patches"),
                    new HabitatPatchFeature(HabitatPatchFeature.Kind.OCEAN_SEAGRASS));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "ocean_sediment_stones"),
                    new HabitatPatchFeature(HabitatPatchFeature.Kind.OCEAN_STONE));
            // ONE shared decoration feature across vanilla ocean variants:
            // guard inside the Feature enables it only in the unmerged
            // USGS+24 Landforms+Marine world; avoids feature-order cycles.
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "marine_ecology_layers"),
                    new MarineEcologyFeature());
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "temperate_rainforest"),
                    new GrandEcologyFeature(GrandEcologyFeature.Kind.TEMPERATE_RAINFOREST));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "savanna_canopy"),
                    new GrandEcologyFeature(GrandEcologyFeature.Kind.SAVANNA_CANOPY));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "chaparral_scrub"),
                    new GrandEcologyFeature(GrandEcologyFeature.Kind.CHAPARRAL_SCRUB));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "grassland_mosaic"),
                    new GrandEcologyFeature(GrandEcologyFeature.Kind.GRASSLAND_MOSAIC));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "alpine_tundra"),
                    new GrandEcologyFeature(GrandEcologyFeature.Kind.ALPINE_TUNDRA));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "floodplain_bank"),
                    new GrandEcologyFeature(GrandEcologyFeature.Kind.FLOODPLAIN_BANK));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "tropical_rainforest"),
                    new GrandEcologyFeature(GrandEcologyFeature.Kind.TROPICAL_RAINFOREST));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "temperate_seasonal_forest"),
                    new GrandEcologyFeature(GrandEcologyFeature.Kind.TEMPERATE_SEASONAL_FOREST));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "boreal_taiga_floor"),
                    new GrandEcologyFeature(GrandEcologyFeature.Kind.BOREAL_TAIGA));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "geo_cave_lava_tube"),
                    new CaveWonderNetworkFeature(CaveWonderNetworkFeature.Kind.LAVA_TUBE));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "geo_cave_karst_passage"),
                    new CaveWonderNetworkFeature(CaveWonderNetworkFeature.Kind.KARST_PASSAGE));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "geo_region_playa_basin"),
                    new GeoWonderRegionFeature(GeoWonderRegionFeature.Kind.PLAYA_BASIN));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "geo_region_alluvial_fan"),
                    new GeoWonderRegionFeature(GeoWonderRegionFeature.Kind.ALLUVIAL_FAN));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "geo_region_cold_desert_pavement"),
                    new GeoWonderRegionFeature(GeoWonderRegionFeature.Kind.COLD_DESERT_PAVEMENT));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "geo_region_hoodoo_amphitheater"),
                    new GeoWonderRegionFeature(GeoWonderRegionFeature.Kind.HOODOO_AMPHITHEATER));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "geo_region_travertine_steps"),
                    new GeoWonderRegionFeature(GeoWonderRegionFeature.Kind.TRAVERTINE_STEPS));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "geo_region_mangrove_estuary"),
                    new GeoWonderRegionFeature(GeoWonderRegionFeature.Kind.MANGROVE_ESTUARY));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "geo_region_cooled_lava_field"),
                    new GeoWonderRegionFeature(GeoWonderRegionFeature.Kind.COOLED_LAVA_FIELD));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "geo_region_glacial_moraine_ring"),
                    new GeoWonderRegionFeature(GeoWonderRegionFeature.Kind.GLACIAL_MORAINE_RING));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "geo_region_delta_distributaries"),
                    new GeoWonderRegionFeature(GeoWonderRegionFeature.Kind.DELTA_DISTRIBUTARIES));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "geo_region_karst_doline_rim"),
                    new GeoWonderRegionFeature(GeoWonderRegionFeature.Kind.KARST_DOLINE_RIM));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "geo_region_permafrost_polygons"),
                    new GeoWonderRegionFeature(GeoWonderRegionFeature.Kind.PERMAFROST_POLYGONS));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "earth_hoodoo_spires"),
                    new EarthWonderFeature(EarthWonderFeature.Kind.HOODOO_SPIRES));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "earth_travertine_terraces"),
                    new EarthWonderFeature(EarthWonderFeature.Kind.TRAVERTINE_TERRACES));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "earth_mangrove_tidal_roots"),
                    new EarthWonderFeature(EarthWonderFeature.Kind.MANGROVE_TIDAL_ROOTS));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "earth_cooled_lava_field"),
                    new EarthWonderFeature(EarthWonderFeature.Kind.COOLED_LAVA_FIELD));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "earth_lava_tube_lining"),
                    new EarthWonderFeature(EarthWonderFeature.Kind.LAVA_TUBE_LINING));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "earth_glacial_moraine"),
                    new EarthWonderFeature(EarthWonderFeature.Kind.GLACIAL_MORAINE));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "earth_delta_sediment"),
                    new EarthWonderFeature(EarthWonderFeature.Kind.DELTA_SEDIMENT));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "earth_karst_sinkhole_rim"),
                    new EarthWonderFeature(EarthWonderFeature.Kind.KARST_SINKHOLE_RIM));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "earth_cold_desert"),
                    new EarthSystemsAccentFeature(EarthSystemsAccentFeature.Kind.COLD_DESERT));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "earth_rocky_plateau"),
                    new EarthSystemsAccentFeature(EarthSystemsAccentFeature.Kind.ROCKY_PLATEAU));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "earth_salt_playa"),
                    new EarthSystemsAccentFeature(EarthSystemsAccentFeature.Kind.SALT_PLAYA));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "earth_cloud_forest"),
                    new EarthSystemsAccentFeature(EarthSystemsAccentFeature.Kind.CLOUD_FOREST));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "earth_alluvial_fan"),
                    new EarthSystemsAccentFeature(EarthSystemsAccentFeature.Kind.ALLUVIAL_FAN));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "earth_volcanic_highland"),
                    new EarthSystemsAccentFeature(EarthSystemsAccentFeature.Kind.VOLCANIC_HIGHLAND));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "earth_badlands"),
                    new EarthSystemsAccentFeature(EarthSystemsAccentFeature.Kind.BADLANDS));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "earth_yardang"),
                    new EarthSystemsAccentFeature(EarthSystemsAccentFeature.Kind.YARDANG));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "earth_karst"),
                    new EarthSystemsAccentFeature(EarthSystemsAccentFeature.Kind.KARST));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "earth_glacial"),
                    new EarthSystemsAccentFeature(EarthSystemsAccentFeature.Kind.GLACIAL));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "earth_wetland"),
                    new EarthSystemsAccentFeature(EarthSystemsAccentFeature.Kind.WETLAND));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "earth_ash_field"),
                    new EarthSystemsAccentFeature(EarthSystemsAccentFeature.Kind.ASH_FIELD));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "desert_outcrops"),
                    new GrandEcologyFeature(GrandEcologyFeature.Kind.DESERT_OUTCROPS));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "arctic_tundra"),
                    new GrandEcologyFeature(GrandEcologyFeature.Kind.ARCTIC_TUNDRA));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "freshwater_bed"),
                    new GrandEcologyFeature(GrandEcologyFeature.Kind.FRESHWATER_BED));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "coastal_seagrass"),
                    new GrandEcologyFeature(GrandEcologyFeature.Kind.COASTAL_SEAGRASS));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "coral_reef_floor"),
                    new GrandEcologyFeature(GrandEcologyFeature.Kind.CORAL_REEF_FLOOR));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "estuary_shallows"),
                    new GrandEcologyFeature(GrandEcologyFeature.Kind.ESTUARY_SHALLOWS));
        });
    }
}
