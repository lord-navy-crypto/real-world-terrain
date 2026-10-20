package org.realscale.neoforge;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * RealScale NeoForge 1.21.1 single-source real world generation integration.
 * Source of truth for eight production implementations: 10 cinematic macro
 * landforms, coastal fjord v4, six subterranean habitat distributions,
 * underground rock accents, and flowering hanging sky islands.
 *
 * Drop-in replacement for the existing RealScaleMod.java within this repo.
 * Requires this repository's existing Minecraft NeoForge dependencies,
 * worldgen datapack JSON, shared climate sources and other mod classes.
 * This file is NOT a standalone vanilla Java program or a self-contained mod JAR.
 *
 * Embedded code is retained faithfully from validated project production
 * classes. Original classes remain in the repo to preserve compatibility
 * with other consumers; this entrypoint's simple class names resolve the
 * nested implementations below, including all codec/feature registrations.
 */
@Mod("realscale")
public final class RealScaleMod {
    public RealScaleMod(IEventBus bus) {
        RealScaleVehicles.register(bus);
        MarineSpawnHabitats.register();
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
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "cinematic_landforms_v1"), CinematicLandformDensity.MAP_CODEC);
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "cinematic_landforms_v2"), CinematicLandformDensityV2.MAP_CODEC);
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "cinematic_landforms_v3"), CinematicLandformDensityV3.MAP_CODEC);
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "coastal_fjord_v4"), CoastalFjordDensityV4.MAP_CODEC);
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "cinematic_landforms_integrated_v1"), RealScaleCinematicIntegrated.MAP_CODEC);
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
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "rare_earth_biomes_v1"),
                    RareEarthBiomeSource.CODEC)
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
        event.register(Registries.BIOME_SOURCE, helper ->
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "northern_cave_biomes_v4"),
                NorthernCaveBiomeSourceV4.CODEC)
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
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "nether_ecology_accents"),
                    new RealmEcologyAccentFeature(RealmEcologyAccentFeature.Realm.NETHER));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "end_ecology_accents"),
                    new RealmEcologyAccentFeature(RealmEcologyAccentFeature.Realm.END));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "end_chorus_gardens"),
                    new EndAccentFeature(EndAccentFeature.Kind.CHORUS_GARDENS));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "end_crystal_barrens"),
                    new EndAccentFeature(EndAccentFeature.Kind.CRYSTAL_BARRENS));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "end_shattered_glades"),
                    new EndAccentFeature(EndAccentFeature.Kind.SHATTERED_GLADES));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "sky_blossom_island"),
                    new SkyIslandFeature());
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "rare_earth_surface"),
                    new RareEarthSurfaceFeature());
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "rare_earth_habitat"),
                    new RareEarthHabitatFeature());
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "scenic_rainforest_terrace"),
                    new ScenicLandscapeFeature(ScenicLandscapeFeature.Kind.RAINFOREST_TERRACE));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "scenic_misty_coast"),
                    new ScenicLandscapeFeature(ScenicLandscapeFeature.Kind.MISTY_COAST_CRAG));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "scenic_basalt_upland"),
                    new ScenicLandscapeFeature(ScenicLandscapeFeature.Kind.BASALT_UPLAND));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "scenic_underwater_arch"),
                    new ScenicLandscapeFeature(ScenicLandscapeFeature.Kind.UNDERWATER_ARCH));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "geo_wulingyuan_pillars"),
                    new GeographicLandmarkFeature(GeographicLandmarkFeature.Kind.WULINGYUAN_SANDSTONE));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "geo_south_china_karst"),
                    new GeographicLandmarkFeature(GeographicLandmarkFeature.Kind.SOUTH_CHINA_KARST));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "geo_aswan_granite"),
                    new GeographicLandmarkFeature(GeographicLandmarkFeature.Kind.ASWAN_GRANITE));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "geo_socotra_limestone"),
                    new GeographicLandmarkFeature(GeographicLandmarkFeature.Kind.SOCOTRA_LIMESTONE));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "geo_iceland_basalt"),
                    new GeographicLandmarkFeature(GeographicLandmarkFeature.Kind.ICELAND_LAVA_FIELD));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "geo_huanglong_travertine"),
                    new GeographicLandmarkFeature(GeographicLandmarkFeature.Kind.HUANGLONG_TRAVERTINE));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "geo_guilin_river_karst"),
                    new GeographicLandmarkFeature(GeographicLandmarkFeature.Kind.GUILIN_RIVER_KARST));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "geo_danxia_redbeds"),
                    new GeographicLandmarkFeature(GeographicLandmarkFeature.Kind.DANXIA_REDBEDS));
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "cave_geology_accents"),
                    new CaveGeologyAccentFeature());
            helper.register(ResourceLocation.fromNamespaceAndPath("realscale", "northern_cave_habitats_v4"),
                    new NorthernCaveHabitatFeatureV4());
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

    // ===== BEGIN REALSCALE ONE-FILE WORLDGEN V4 =====
    // ---- BEGIN EXACT PRODUCTION RealScaleCinematicIntegrated (nested; registered by RealScaleMod) ----
    /**
     * RealScale integrated macro terrain engine (10 archetypes in ONE production
     * Java source file). In an actual NeoForge project, register MAP_CODEC under
     * realscale:cinematic_landforms_integrated_v1 and reference that codec in
     * the versioned density-function datapack.
     *
     * Frozen legacy V1/V2/V3 maths are embedded as private nested implementations,
     * so the new engine has no dependency on the original three Java math files.
     * The legacy independent codecs and noise settings remain available for saved
     * worlds; they are NOT removed or changed by this consolidated engine.
     *
     * Density affects topography only. It does not mutate blocks/fluids, invent
     * fjords, or reconstruct exact coordinates of copyrighted game geography.
     */
    public static final class RealScaleCinematicIntegrated implements DensityFunction.SimpleFunction {
        private final DensityFunction base,continents,temperature,humidity;
        private final ThreadLocal<Site> local=ThreadLocal.withInitial(Site::new);
        private static final class Site {
            int cellX=Integer.MIN_VALUE,cellZ=Integer.MIN_VALUE,kind;
            boolean classified;
            long hash;
            int x,z;
            double radius;
            void prepare(int cx,int cz) {
                if(cx==cellX&&cz==cellZ)return;
                cellX=cx;cellZ=cz;
                hash=V3.hash(cx,cz);
                x=V3.siteX(cx,hash);
                z=V3.siteZ(cz,hash);
                radius=V3.radius(hash);
                classified=false;
            }
        }
        public RealScaleCinematicIntegrated(DensityFunction base,DensityFunction continents,
                                        DensityFunction temperature,DensityFunction humidity) {
            this.base=base;this.continents=continents;
            this.temperature=temperature;this.humidity=humidity;
        }
        public DensityFunction base(){return base;}
        public DensityFunction continents(){return continents;}
        public DensityFunction temperature(){return temperature;}
        public DensityFunction humidity(){return humidity;}
        public static final MapCodec<RealScaleCinematicIntegrated> MAP_CODEC =
                RecordCodecBuilder.mapCodec(i->i.group(
                        DensityFunction.HOLDER_HELPER_CODEC.fieldOf("base")
                                .forGetter(RealScaleCinematicIntegrated::base),
                        DensityFunction.HOLDER_HELPER_CODEC.fieldOf("continents")
                                .forGetter(RealScaleCinematicIntegrated::continents),
                        DensityFunction.HOLDER_HELPER_CODEC.fieldOf("temperature")
                                .forGetter(RealScaleCinematicIntegrated::temperature),
                        DensityFunction.HOLDER_HELPER_CODEC.fieldOf("humidity")
                                .forGetter(RealScaleCinematicIntegrated::humidity)
                ).apply(i,RealScaleCinematicIntegrated::new));
        private static final KeyDispatchDataCodec<RealScaleCinematicIntegrated> DISPATCH=
                KeyDispatchDataCodec.of(MAP_CODEC);

        @Override public double compute(DensityFunction.FunctionContext ctx) {
            double before=base.compute(ctx);
            if(before<=0.82)return before;
            int x=ctx.blockX(),z=ctx.blockZ();
            Site site=local.get();
            site.prepare(Math.floorDiv(x,V3.CELL),
                         Math.floorDiv(z,V3.CELL));
            double dx=(double)x-site.x,dz=(double)z-site.z;
            if(dx*dx+dz*dz>=site.radius*site.radius)return before;
            double continental=continents.compute(ctx);
            if(V3.gate(continental,before)==0)return before;
            if(!site.classified) {
                DensityFunction.SinglePointContext anchor=
                        new DensityFunction.SinglePointContext(site.x,0,site.z);
                site.kind=V3.kind(site.hash,
                        temperature.compute(anchor),humidity.compute(anchor));
                site.classified=true;
            }
            if(site.kind==V3.NONE)return before;
            return V3.apply(site.kind,before,dx,dz,
                    site.radius,site.hash,continental);
        }
        
        // === BEGIN INTEGRATED PURE JAVA MATH ===
        // Embedded original algorithms; the Java verification test extracts this
        // region and checks output parity against the three previous versions.
        static final class V1 {
            static final int CELL = 4096;
            static final int SANDSTONE_TOWERS = 0;
            static final int KARST_TOWERS = 1;
            static final int CAPROCK_MESA = 2;
            static final int INCISED_CANYON = 3;
            static final int VOLCANIC_CALDERA = 4;
            static final int GLACIAL_U_TROUGH = 5;
            static final int NONE = 6;
        
            private V1() {}
            static long mix(long h) {
                h=(h^(h>>>30))*0xbf58476d1ce4e5b9L;
                h=(h^(h>>>27))*0x94d049bb133111ebL;
                return h^(h>>>31);
            }
            static long hash(int x,int z) {
                return mix(((long)x)*0x9e3779b97f4a7c15L
                       ^ ((long)z)*0xc2b2ae3d27d4eb4fL
                       ^ 0x5d91a6e7b230fc49L);
            }
            static int siteX(int cx,long hash) {
                return (int)((long)cx*CELL + CELL/2
                        +Math.floorMod(hash>>>13,401L)-200);
            }
            static int siteZ(int cz,long hash) {
                return (int)((long)cz*CELL + CELL/2
                        +Math.floorMod(hash>>>34,401L)-200);
            }
            static double radius(long hash) {
                return 1075.0+Math.floorMod(hash>>>22,276L);
            }
            static double smooth(double a,double b,double x) {
                double t=Math.max(0,Math.min(1,(x-a)/(b-a)));
                return t*t*(3-2*t);
            }
            static double envelope(double r) {
                return 1.0-smooth(0.73,1.0,r);
            }
            static double gate(double continental,double baseline) {
                // No sea-floor alteration or sudden mountain raising on coasts.
                return smooth(0.04,0.31,continental)*smooth(0.82,1.85,baseline);
            }
            static int kind(long h,double temp,double humidity) {
                // World-seeded climate sampled once at site center, not chunk order.
                if(temp>=0.04 && humidity>=0.16)
                    return (h&1L)==0 ? SANDSTONE_TOWERS : KARST_TOWERS;
                if(temp>=0.09 && humidity<=-0.09)return CAPROCK_MESA;
                if(humidity<=0.09 && temp>=-0.35)return INCISED_CANYON;
                if(temp<=-0.30)
                    return humidity>=-0.04 ? GLACIAL_U_TROUGH : VOLCANIC_CALDERA;
                return NONE;
            }
            static double towerDistance(double u,double v,long h,double spacing) {
                // Nearest jittered lattice site: no uniform Minecraft checkerboard
                // repetition. Jitter is fixed by world-absolute lattice coordinates.
                int cx=(int)Math.floor(u/spacing),cz=(int)Math.floor(v/spacing);
                // Compare squared distances, then take ONE square root. Density
                // sampling calls this for every column: nine hypot/sqrt calls here
                // were unnecessary and dominate generation in tower districts.
                double bestSquared=Double.POSITIVE_INFINITY;
                for(int i=-1;i<=1;i++)for(int j=-1;j<=1;j++) {
                    int xx=cx+i,zz=cz+j;
                    long k=mix(h ^ (long)xx*0x9e3779b97f4a7c15L
                                    ^ (long)zz*0xc2b2ae3d27d4eb4fL);
                    double px=(xx+0.5)*spacing
                            +(Math.floorMod(k>>>17,1001L)/1000.0-0.5)*spacing*0.47;
                    double pz=(zz+0.5)*spacing
                            +(Math.floorMod(k>>>38,1001L)/1000.0-0.5)*spacing*0.47;
                    double du=u-px,dv=v-pz;
                    bestSquared=Math.min(bestSquared,du*du+dv*dv);
                }
                return Math.sqrt(bestSquared);
            }
            static double relief(int kind,double dx,double dz,double radius,long h) {
                final double r=Math.hypot(dx,dz)/radius;
                if(r>=1.0 || kind==NONE)return 0;
                final double edge=envelope(r);
                // Direction is district-stable; avoids long world-axis repetition.
                final double angle=(Math.floorMod(h>>>45,6284L)/1000.0);
                final double u=dx*Math.cos(angle)+dz*Math.sin(angle);
                final double v=-dx*Math.sin(angle)+dz*Math.cos(angle);
                return switch(kind) {
                    case SANDSTONE_TOWERS -> {
                        double d=towerDistance(u,v,h,205.0);
                        // Tight, steep-sided separate pillars on a raised wooded
                        // platform with winding gaps. Density 2.50 ≈ 320 blocks.
                        double pillar=1.0-smooth(48.0,91.0,d);
                        yield edge*(0.14+2.65*pillar);
                    }
                    case KARST_TOWERS -> {
                        double d=towerDistance(u,v,h,300.0);
                        // Rounder limestone cones with saddle-like valley floors.
                        double cone=1.0-smooth(24.0,165.0,d);
                        yield edge*(-0.12+2.10*cone);
                    }
                    case CAPROCK_MESA -> {
                        // 900-block plateau with a deliberately sharp but continuous
                        // escarpment; no discontinuous floating platform.
                        double table=1.0-smooth(0.43,0.51,r);
                        yield edge*(0.17+3.05*table);
                    }
                    case INCISED_CANYON -> {
                        double axis=v-125*Math.sin(u/240.0)-42*Math.sin(u/73.0);
                        double trench=1.0-smooth(54.0,155.0,Math.abs(axis));
                        double shoulders=smooth(105.0,320.0,Math.abs(axis));
                        yield edge*(0.55*shoulders-1.35*trench);
                    }
                    // Broad volcanic caldera: eroded ring wall enclosing a recessed
                    // floor. No lava, new water or destructive block placement.
                    case VOLCANIC_CALDERA -> {
                        double ring=smooth(0.29,0.49,r)
                                   *(1.0-smooth(0.68,0.84,r));
                        double floor=1.0-smooth(0.12,0.48,r);
                        yield edge*(2.85*ring-0.95*floor);
                    }
                    // Wide glacier-carved U valley with sinuous trough, broad floor
                    // and flanking shoulders, rather than a sharp V-shaped ravine.
                    case GLACIAL_U_TROUGH -> {
                        double axis=Math.abs(v-72*Math.sin(u/360.0));
                        double floor=1.0-smooth(105.0,270.0,axis);
                        double shoulders=smooth(135.0,430.0,axis)
                                *(1.0-smooth(740.0,1120.0,axis));
                        yield edge*(1.80*shoulders-0.78*floor);
                    }
                    default -> 0.0;
                };
            }
            static double apply(int kind,double before,double dx,double dz,
                                double radius,long hash,double continental) {
                double weight=gate(continental,before);
                return weight==0?before:before+weight*relief(kind,dx,dz,radius,hash);
            }
        }
        
        static final class V2 {
            static final int CELL=V1.CELL;
            static final int STAR_DUNE_FIELD=6;
            static final int TEPUI_TABLELANDS=7;
            static final int NONE=8;
        
            private V2() {}
            // Reuse the exact world-absolute v1 spatial cell layout for continuity.
            // The source functions themselves retain their v1 implementations.
            static long hash(int cx,int cz){return V1.hash(cx,cz);}
            static int siteX(int cx,long h){return V1.siteX(cx,h);}
            static int siteZ(int cz,long h){return V1.siteZ(cz,h);}
            static double radius(long h){return V1.radius(h);}
            static double gate(double continent,double height) {
                return V1.gate(continent,height);
            }
            static int kind(long hash,double temperature,double humidity) {
                // Keep existing cold/wet archetypes, but allow climate-matched
                // diversity within broad wet tropics and hot arid environments.
                if(temperature>=0.04 && humidity>=0.16) {
                    int variant=(int)Math.floorMod(hash>>>13,3L);
                    return variant==0?V1.SANDSTONE_TOWERS
                           :variant==1?V1.KARST_TOWERS
                           :TEPUI_TABLELANDS;
                }
                if(temperature>=0.09 && humidity<=-0.09)
                    return ((hash>>>21)&1L)==0L
                            ?V1.CAPROCK_MESA:STAR_DUNE_FIELD;
                if(humidity<=0.09 && temperature>=-0.35)
                    return V1.INCISED_CANYON;
                if(temperature<=-0.30)
                    return humidity>=-0.04?V1.GLACIAL_U_TROUGH
                            :V1.VOLCANIC_CALDERA;
                return NONE;
            }
        
            static double relief(int kind,double dx,double dz,double radius,long hash) {
                double r=Math.hypot(dx,dz)/radius;
                if(r>=1.0 || kind==NONE)return 0.0;
                double envelope=V1.envelope(r);
                if(kind<STAR_DUNE_FIELD) {
                    // V2 scenic erosion: replace perfectly circular rims and orderly
                    // dot grids with smooth site-specific folding, without changing
                    // a single byte of the V1 production shape function.
                    // The original radius gate is applied last so warped shapes NEVER
                    // leak across 4-km site boundaries.
                    double seed=Math.floorMod(hash>>>36,6284L)/1000.0;
                    double wx=dx*1.035+58.0*Math.sin(dz/290.0+seed)
                            +21.0*Math.sin((dx+dz)/124.0-seed);
                    double wz=dz*0.955+52.0*Math.cos(dx/325.0-seed)
                            +24.0*Math.sin((dx-dz)/138.0+seed);
                    double weather=0.88+0.16*
                            (0.5+0.5*Math.sin(wx/270.0+wz/410.0+seed));
                    return envelope*weather*
                            V1.relief(kind,wx,wz,radius,hash);
                }
                double angle=Math.floorMod(hash>>>45,6284L)/1000.0;
                double u=dx*Math.cos(angle)+dz*Math.sin(angle);
                double v=-dx*Math.sin(angle)+dz*Math.cos(angle);
                return switch(kind) {
                    case STAR_DUNE_FIELD -> {
                        // An asymmetric wind-aligned dune complex with branching
                        // slipface arms, not a single symmetrical conical hill.
                        // Positive-only sand relief; natural sea-floor and base
                        // continental thresholds are safeguarded by density gate.
                        double windward=0.5+0.5*Math.sin(u/195.0+0.33*Math.sin(v/315.0));
                        double crossing=0.5+0.5*Math.sin(v/240.0-0.25*Math.sin(u/300.0));
                        double ridge=Math.pow(windward,5.0);
                        double arms=Math.pow(crossing,7.0);
                        double star=Math.pow(Math.max(0.0,
                                Math.cos(5.0*Math.atan2(v,u))),8.0);
                        double core=Math.exp(-Math.pow(Math.hypot(u,v)/830.0,2.0));
                        yield envelope*(0.07+1.30*ridge+0.66*arms
                                +0.48*core*star);
                    }
                    case TEPUI_TABLELANDS -> {
                        // Two caprock massifs with independent flat tops and steep
                        // sides; the saddle between them opens a traverse corridor.
                        double first=Math.pow(
                                Math.pow(Math.abs((u+450.0)/480.0),4)
                                +Math.pow(Math.abs((v+95.0)/440.0),4),0.25);
                        double second=Math.pow(
                                Math.pow(Math.abs((u-450.0)/455.0),4)
                                +Math.pow(Math.abs((v-130.0)/395.0),4),0.25);
                        double plateau1=1.0-V1.smooth(0.68,0.84,first);
                        double plateau2=1.0-V1.smooth(0.68,0.84,second);
                        double capped=Math.max(plateau1,plateau2);
                        yield envelope*(0.10+3.15*capped);
                    }
                    default -> 0.0;
                };
            }
            static double apply(int kind,double baseline,double dx,double dz,
                                double radius,long hash,double continental) {
                double gate=V1.gate(continental,baseline);
                return gate==0?baseline:baseline+gate*relief(kind,dx,dz,radius,hash);
            }
        }
        
        static final class V3 {
            static final int CELL=V1.CELL;
            static final int STAR_DUNE_FIELD=6;
            static final int TEPUI_TABLELANDS=7;
            static final int CIRQUE_BASIN=8;
            static final int MORAINE_RIDGES=9;
            static final int NONE=10;
        
            private V3() {}
            // Reuse the exact world-absolute v1 spatial cell layout for continuity.
            // The source functions themselves retain their v1 implementations.
            static long hash(int cx,int cz){return V1.hash(cx,cz);}
            static int siteX(int cx,long h){return V1.siteX(cx,h);}
            static int siteZ(int cz,long h){return V1.siteZ(cz,h);}
            static double radius(long h){return V1.radius(h);}
            static double gate(double continent,double height) {
                return V1.gate(continent,height);
            }
            static int kind(long hash,double temperature,double humidity) {
                // Keep existing cold/wet archetypes, but allow climate-matched
                // diversity within broad wet tropics and hot arid environments.
                if(temperature>=0.04 && humidity>=0.16) {
                    int variant=(int)Math.floorMod(hash>>>13,3L);
                    return variant==0?V1.SANDSTONE_TOWERS
                           :variant==1?V1.KARST_TOWERS
                           :TEPUI_TABLELANDS;
                }
                if(temperature>=0.09 && humidity<=-0.09)
                    return ((hash>>>21)&1L)==0L
                            ?V1.CAPROCK_MESA:STAR_DUNE_FIELD;
                if(humidity<=0.09 && temperature>=-0.35)
                    return V1.INCISED_CANYON;
                if(temperature<=-0.30) {
                    if(humidity < -0.04)return V1.VOLCANIC_CALDERA;
                    // Cold, moisture-supported uplands cycle through distinct
                    // erosional U valleys, amphitheatre cirques and depositional
                    // moraines. Selection is per immutable world-coordinate site.
                    return switch((int)Math.floorMod(hash>>>27,3L)) {
                        case 0 -> V1.GLACIAL_U_TROUGH;
                        case 1 -> CIRQUE_BASIN;
                        default -> MORAINE_RIDGES;
                    };
                }
                return NONE;
            }
        
            static double relief(int kind,double dx,double dz,double radius,long hash) {
                double r=Math.hypot(dx,dz)/radius;
                if(r>=1.0 || kind==NONE)return 0.0;
                double envelope=V1.envelope(r);
                if(kind<STAR_DUNE_FIELD) {
                    // V2 scenic erosion: replace perfectly circular rims and orderly
                    // dot grids with smooth site-specific folding, without changing
                    // a single byte of the V1 production shape function.
                    // The original radius gate is applied last so warped shapes NEVER
                    // leak across 4-km site boundaries.
                    double seed=Math.floorMod(hash>>>36,6284L)/1000.0;
                    double wx=dx*1.035+58.0*Math.sin(dz/290.0+seed)
                            +21.0*Math.sin((dx+dz)/124.0-seed);
                    double wz=dz*0.955+52.0*Math.cos(dx/325.0-seed)
                            +24.0*Math.sin((dx-dz)/138.0+seed);
                    double weather=0.88+0.16*
                            (0.5+0.5*Math.sin(wx/270.0+wz/410.0+seed));
                    return envelope*weather*
                            V1.relief(kind,wx,wz,radius,hash);
                }
                double angle=Math.floorMod(hash>>>45,6284L)/1000.0;
                double u=dx*Math.cos(angle)+dz*Math.sin(angle);
                double v=-dx*Math.sin(angle)+dz*Math.cos(angle);
                return switch(kind) {
                    case STAR_DUNE_FIELD -> {
                        // An asymmetric wind-aligned dune complex with branching
                        // slipface arms, not a single symmetrical conical hill.
                        // Positive-only sand relief; natural sea-floor and base
                        // continental thresholds are safeguarded by density gate.
                        double windward=0.5+0.5*Math.sin(u/195.0+0.33*Math.sin(v/315.0));
                        double crossing=0.5+0.5*Math.sin(v/240.0-0.25*Math.sin(u/300.0));
                        double ridge=Math.pow(windward,5.0);
                        double arms=Math.pow(crossing,7.0);
                        double star=Math.pow(Math.max(0.0,
                                Math.cos(5.0*Math.atan2(v,u))),8.0);
                        double core=Math.exp(-Math.pow(Math.hypot(u,v)/830.0,2.0));
                        yield envelope*(0.07+1.30*ridge+0.66*arms
                                +0.48*core*star);
                    }
                    case TEPUI_TABLELANDS -> {
                        // Two caprock massifs with independent flat tops and steep
                        // sides; the saddle between them opens a traverse corridor.
                        double first=Math.pow(
                                Math.pow(Math.abs((u+450.0)/480.0),4)
                                +Math.pow(Math.abs((v+95.0)/440.0),4),0.25);
                        double second=Math.pow(
                                Math.pow(Math.abs((u-450.0)/455.0),4)
                                +Math.pow(Math.abs((v-130.0)/395.0),4),0.25);
                        double plateau1=1.0-V1.smooth(0.68,0.84,first);
                        double plateau2=1.0-V1.smooth(0.68,0.84,second);
                        double capped=Math.max(plateau1,plateau2);
                        yield envelope*(0.10+3.15*capped);
                    }
                    case CIRQUE_BASIN -> {
                        // Erosion-style amphitheatre in a cold mountain shoulder:
                        // a concave basin, semicircular higher headwall and a
                        // low, open outlet extending along +v. This is not a lake.
                        double ellipse=Math.hypot(u/440.0,(v+185.0)/380.0);
                        double basin=1.0-V1.smooth(0.18,0.91,ellipse);
                        double wall=V1.smooth(0.54,0.76,ellipse)
                                *(1.0-V1.smooth(1.00,1.27,ellipse));
                        double opening=1.0-V1.smooth(-40,270,v);
                        double outlet=(1.0-V1.smooth(100,250,
                                Math.abs(u-31*Math.sin(v/210.0))))
                                *V1.smooth(-190,90,v)
                                *(1.0-V1.smooth(570,870,v));
                        yield envelope*(2.05*wall*opening-0.91*basin
                                -0.36*outlet);
                    }
                    case MORAINE_RIDGES -> {
                        // Glacial till: two irregular side-ridges + arced terminal
                        // ridge with a depressed inter-ridge corridor. No water,
                        // glacial ice or unsupported floating structures are placed.
                        double lateralOffset=Math.abs(u+26*Math.sin(v/170.0))-230.0;
                        double leftRight=Math.exp(-Math.pow(lateralOffset/78.0,2))
                                *(1.0-V1.smooth(620,960,Math.abs(v)));
                        double elliptic=Math.hypot(u/495.0,(v-80.0)/520.0);
                        double terminal=Math.exp(-Math.pow((elliptic-0.82)/0.12,2))
                                *V1.smooth(-290,110,v);
                        double hollow=(1.0-V1.smooth(85,220,Math.abs(u)))
                                *(1.0-V1.smooth(160,490,Math.abs(v)));
                        yield envelope*(1.05*leftRight+0.87*terminal-0.24*hollow);
                    }
                    default -> 0.0;
                };
            }
            static double apply(int kind,double baseline,double dx,double dz,
                                double radius,long hash,double continental) {
                double gate=V1.gate(continental,baseline);
                return gate==0?baseline:baseline+gate*relief(kind,dx,dz,radius,hash);
            }
        }
        
        // === END INTEGRATED PURE JAVA MATH ===
    @Override public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new RealScaleCinematicIntegrated(base.mapAll(visitor),
                    continents.mapAll(visitor),temperature.mapAll(visitor),
                    humidity.mapAll(visitor)));
        }
        @Override public double minValue(){return base.minValue()-2.0;}
        @Override public double maxValue(){return base.maxValue()+4.0;}
        @Override public KeyDispatchDataCodec<? extends DensityFunction> codec(){return DISPATCH;}
    }
    // ---- END RealScaleCinematicIntegrated ----

    // ---- BEGIN EXACT PRODUCTION CoastalFjordDensityV4 (nested; registered by RealScaleMod) ----
    /**
     * Experimental opt-in v4 coastal fjord height-density engine for NeoForge 1.21.1.
     * Consumes the archived v3 height signal; never alters v1/v2/v3 codecs.
     * Selects cold, wet sites with genuine ocean and inland samples in a fixed
     * coastal direction, then erodes a continuous submerged U-shaped corridor.
     * Does not write blocks, water, biomes, chunks, or saved world metadata.
     */
    public static final class CoastalFjordDensityV4 implements DensityFunction.SimpleFunction {
        private final DensityFunction base,continents,temperature,humidity;
        private final ThreadLocal<Site> sites=ThreadLocal.withInitial(Site::new);
        private static final class Site {
            int cx=Integer.MIN_VALUE,cz=Integer.MIN_VALUE,x,z;
            long hash;
            boolean classified,enabled;
            double angle,phase;
            void prepare(int nextX,int nextZ) {
                if(cx==nextX&&cz==nextZ)return;
                cx=nextX;cz=nextZ;
                hash=FjordMath.hash(cx,cz);
                x=FjordMath.siteX(cx,hash);
                z=FjordMath.siteZ(cz,hash);
                phase=FjordMath.phase(hash);
                classified=false;
                enabled=false;
            }
        }
        public CoastalFjordDensityV4(DensityFunction base,DensityFunction continents,
                                      DensityFunction temperature,DensityFunction humidity) {
            this.base=base;this.continents=continents;
            this.temperature=temperature;this.humidity=humidity;
        }
        public DensityFunction base(){return base;}
        public DensityFunction continents(){return continents;}
        public DensityFunction temperature(){return temperature;}
        public DensityFunction humidity(){return humidity;}

        public static final MapCodec<CoastalFjordDensityV4> MAP_CODEC=
                RecordCodecBuilder.mapCodec(i->i.group(
                        DensityFunction.HOLDER_HELPER_CODEC.fieldOf("base")
                                .forGetter(CoastalFjordDensityV4::base),
                        DensityFunction.HOLDER_HELPER_CODEC.fieldOf("continents")
                                .forGetter(CoastalFjordDensityV4::continents),
                        DensityFunction.HOLDER_HELPER_CODEC.fieldOf("temperature")
                                .forGetter(CoastalFjordDensityV4::temperature),
                        DensityFunction.HOLDER_HELPER_CODEC.fieldOf("humidity")
                                .forGetter(CoastalFjordDensityV4::humidity)
                ).apply(i,CoastalFjordDensityV4::new));
        private static final KeyDispatchDataCodec<CoastalFjordDensityV4> DISPATCH=
                KeyDispatchDataCodec.of(MAP_CODEC);

        private double at(DensityFunction fn,double x,double z) {
            return fn.compute(new DensityFunction.SinglePointContext(
                    (int)Math.round(x),0,(int)Math.round(z)));
        }
        private void classify(Site site) {
            site.classified=true;
            // A fjord must reach cold marine water, not make a landlocked lake.
            if(at(temperature,site.x,site.z)>-0.22
                    ||at(humidity,site.x,site.z)<-0.18)return;
            double best=0;
            int first=(int)((site.hash>>>53)&7L);
            for(int step=0;step<8;step++) {
                double angle=((first+step)&7)*(Math.PI/4.0);
                double c=Math.cos(angle),s=Math.sin(angle);
                double seaX=site.x-1100*c,seaZ=site.z-1100*s;
                double outerX=site.x-1340*c,outerZ=site.z-1340*s;
                double landX=site.x+900*c,landZ=site.z+900*s;
                double seaCont=at(continents,seaX,seaZ);
                double outerCont=at(continents,outerX,outerZ);
                double landCont=at(continents,landX,landZ);
                if(seaCont>=-0.07||outerCont>=-0.08||landCont<=0.16)
                    continue;
                double seaHeight=at(base,seaX,seaZ);
                double outerHeight=at(base,outerX,outerZ);
                double landHeight=at(base,landX,landZ);
                if(seaHeight>=-0.06||outerHeight>=-0.04
                        ||landHeight<=0.42)continue;
                double score=(landCont-seaCont)+0.12*(landHeight-seaHeight);
                if(score>best) {
                    best=score;site.angle=angle;site.enabled=true;
                }
            }
        }
        @Override public double compute(DensityFunction.FunctionContext ctx) {
            double before=base.compute(ctx);
            int x=ctx.blockX(),z=ctx.blockZ();
            Site site=sites.get();
            site.prepare(Math.floorDiv(x,FjordMath.CELL),
                         Math.floorDiv(z,FjordMath.CELL));
            double dx=(double)x-site.x,dz=(double)z-site.z;
            if(dx*dx+dz*dz>=FjordMath.RADIUS*FjordMath.RADIUS)return before;
            if(!site.classified)classify(site);
            if(!site.enabled)return before;
            return FjordMath.carve(before,dx,dz,site.angle,site.phase);
        }
        @Override public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new CoastalFjordDensityV4(base.mapAll(visitor),
                    continents.mapAll(visitor),temperature.mapAll(visitor),
                    humidity.mapAll(visitor)));
        }
        @Override public double minValue(){return Math.min(base.minValue(),-1.0);}
        @Override public double maxValue(){return base.maxValue();}
        @Override public KeyDispatchDataCodec<? extends DensityFunction> codec(){return DISPATCH;}

        // === BEGIN INDEPENDENT FJORD MATH ===
        /** The following nested class compiles without any Minecraft library. */
        static final class FjordMath {
            static final int CELL=3072;
            static final double RADIUS=1350.0;
            private FjordMath(){}
            static long mix(long h) {
                h=(h^(h>>>30))*0xbf58476d1ce4e5b9L;
                h=(h^(h>>>27))*0x94d049bb133111ebL;
                return h^(h>>>31);
            }
            static long hash(int cx,int cz) {
                return mix((long)cx*0x9e3779b97f4a7c15L
                        ^(long)cz*0xc2b2ae3d27d4eb4fL
                        ^0x7b46f22e21e5af19L);
            }
            static int siteX(int cx,long h) {
                return (int)((long)cx*CELL+CELL/2+Math.floorMod(h>>>13,193L)-96);
            }
            static int siteZ(int cz,long h) {
                return (int)((long)cz*CELL+CELL/2+Math.floorMod(h>>>34,193L)-96);
            }
            static double phase(long h){return Math.floorMod(h>>>40,6284L)/1000.0;}
            static double smooth(double a,double b,double x) {
                double t=Math.max(0.0,Math.min(1.0,(x-a)/(b-a)));
                return t*t*(3.0-2.0*t);
            }
            static double carve(double before,double dx,double dz,
                                double angle,double phase) {
                double r=Math.hypot(dx,dz);
                if(r>=RADIUS)return before;
                double c=Math.cos(angle),s=Math.sin(angle);
                double u=dx*c+dz*s;
                double v=-dx*s+dz*c;
                // Winding glacier-cut valley with broad estuary and U-shape.
                double meander=66.0*Math.sin(u/315.0+phase)
                             +21.0*Math.sin(u/113.0-phase*0.7);
                double halfWidth=95.0+46.0*(1.0-smooth(-500.0,850.0,u));
                double cross=1.0-smooth(halfWidth-25,halfWidth+120,
                                        Math.abs(v-meander));
                double length=smooth(-1310,-1010,u)*(1.0-smooth(830,1250,u));
                // Fade exactly to zero INSIDE the cell for seamless chunks.
                double boundary=1.0-smooth(0.80,1.0,r/RADIUS);
                double weight=cross*length*boundary;
                if(weight<=0)return before;
                // Signal zero is sea level Y=63; one unit is about 128 blocks.
                double floor=-0.24-0.36*smooth(-520,480,u)
                             +0.11*smooth(550,1000,u);
                // Terrain can only erode. Existing ocean never rises into a dam.
                return before+weight*(Math.min(before,floor)-before);
            }
        }
        // === END INDEPENDENT FJORD MATH ===
    }
    // ---- END CoastalFjordDensityV4 ----

    // ---- BEGIN EXACT PRODUCTION NorthernCaveBiomeSourceV4 (nested; registered by RealScaleMod) ----
    /** V4-only 3D cave biome overlay; unchanged above Y=36 and over waters. */
    public static final class NorthernCaveBiomeSourceV4 extends BiomeSource {
        public static final MapCodec<NorthernCaveBiomeSourceV4> CODEC=
            RecordCodecBuilder.mapCodec(i->i.group(
                BiomeSource.CODEC.fieldOf("source").forGetter(NorthernCaveBiomeSourceV4::source),
                RegistryOps.retrieveGetter(Registries.BIOME))
            .apply(i,NorthernCaveBiomeSourceV4::new));
        private static final List<String> NAMES=List.of(
            "underground_springs","deep_fungal_caverns","glacial_subsurface",
            "damp_limestone_caverns","dry_karst_caves","volcanic_caverns");
        private final BiomeSource source;
        private final List<Holder.Reference<Biome>> regions;
        private NorthernCaveBiomeSourceV4(BiomeSource source,HolderGetter<Biome> biomes) {
            this.source=source;
            regions=NAMES.stream().map(id->biomes.getOrThrow(ResourceKey.create(
                Registries.BIOME,ResourceLocation.fromNamespaceAndPath("realscale",id)))).toList();
        }
        private BiomeSource source(){return source;}
        @Override protected MapCodec<? extends BiomeSource> codec(){return CODEC;}
        @Override protected Stream<Holder<Biome>> collectPossibleBiomes() {
            return Stream.concat(source.possibleBiomes().stream(),regions.stream()).distinct();
        }
        private static String name(Holder<Biome> b) {
            return b.unwrapKey().map(k->k.location().toString()).orElse("");
        }
        @Override public Holder<Biome> getNoiseBiome(int qx,int qy,int qz,Climate.Sampler sampler) {
            Holder<Biome> original=source.getNoiseBiome(qx,qy,qz,sampler);
            if(qy>9||qy< -12)return original;
            String chosen=NorthernCaveBiomeMathV4.select(name(original),
                name(source.getNoiseBiome(qx,16,qz,sampler)),qy,
                NorthernCaveBiomeMathV4.regionHash(qx,qz));
            if(chosen==null)return original;
            int idx=NAMES.indexOf(chosen.substring("realscale:".length()));
            return idx<0?original:regions.get(idx);
        }
    }
    // ---- END NorthernCaveBiomeSourceV4 ----

    // ---- BEGIN EXACT PRODUCTION NorthernCaveBiomeMathV4 (nested; registered by RealScaleMod) ----
    /** Deterministic pure-Java cave habitat classification for opt-in northern v4. */
    public static final class NorthernCaveBiomeMathV4 {
        private NorthernCaveBiomeMathV4(){}
        private static long mix(long h) {
            h=(h^(h>>>30))*0xbf58476d1ce4e5b9L;
            h=(h^(h>>>27))*0x94d049bb133111ebL;
            return h^(h>>>31);
        }
        public static long regionHash(int qx,int qz) {
            long x=Math.floorDiv(qx,80),z=Math.floorDiv(qz,80);
            return mix(x*0x9e3779b97f4a7c15L^z*0xc2b2ae3d27d4eb4fL^0x54ca4e7f18bdbac3L);
        }
        /** Null delegates original biome exactly, including Deep Dark and sea. */
        public static String select(String original,String surface,int quartY,long h) {
            if(quartY>9||quartY< -12||original==null||surface==null)return null;
            if(surface.contains("ocean")||surface.endsWith(":river")
                    ||surface.endsWith(":frozen_river")
                    ||surface.endsWith(":beach")||surface.endsWith(":stony_shore")
                    ||surface.endsWith(":swamp")||surface.endsWith(":mangrove_swamp"))
                return null;
            if(original.equals("minecraft:deep_dark"))return null;
            int v=(int)Math.floorMod(h>>>17,8L);
            if(original.equals("minecraft:lush_caves")) {
                if(v<=1)return "realscale:underground_springs";
                if(v<=5)return "realscale:deep_fungal_caverns";
            }
            if(original.equals("minecraft:dripstone_caves")) {
                if(quartY>=-4 && v<4 && (surface.contains("snow")
                    ||surface.contains("frozen")||surface.contains("ice")
                    ||surface.contains("grove")))
                    return "realscale:glacial_subsurface";
                if(v<3)return "realscale:damp_limestone_caverns";
                if(v<6)return "realscale:dry_karst_caves";
            }
            if(quartY<=4 && v==0 && (surface.equals("minecraft:badlands")
                    ||surface.equals("minecraft:eroded_badlands")
                    ||surface.equals("minecraft:stony_peaks")))
                return "realscale:volcanic_caverns";
            return null;
        }
    }
    // ---- END NorthernCaveBiomeMathV4 ----

    // ---- BEGIN EXACT PRODUCTION NorthernCaveHabitatFeatureV4 (nested; registered by RealScaleMod) ----
    /** V4-only cave floor palette, never carving cavities or replacing liquids/ores. */
    public static final class NorthernCaveHabitatFeatureV4 extends Feature<NoneFeatureConfiguration> {
        public NorthernCaveHabitatFeatureV4(){super(NoneFeatureConfiguration.CODEC);}
        private static boolean rock(BlockState s) {
            return s.is(Blocks.STONE)||s.is(Blocks.DEEPSLATE)||s.is(Blocks.TUFF)
                ||s.is(Blocks.ANDESITE)||s.is(Blocks.DIORITE)
                ||s.is(Blocks.GRANITE)||s.is(Blocks.CALCITE);
        }
        @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
            if(!TVAWorldGuard.hasNorthernFjordV4(ctx))return false;
            WorldGenLevel w=ctx.level();
            if(!"minecraft:overworld".equals(w.getLevel().dimension().location().toString()))
                return false;
            int x0=Math.floorDiv(ctx.origin().getX(),16)*16;
            int z0=Math.floorDiv(ctx.origin().getZ(),16)*16;
            int changed=0;
            for(int n=0;n<16&&changed<4;n++) {
                int x=x0+ctx.random().nextInt(16),z=z0+ctx.random().nextInt(16);
                int y=ctx.origin().getY()+ctx.random().nextInt(49)-24;
                if(y<=w.getMinBuildHeight()+5||y>=36)continue;
                BlockPos air=new BlockPos(x,y,z),floor=air.below();
                if(!w.getBlockState(air).isAir()||!w.getBlockState(air.above()).isAir()
                        ||!w.getFluidState(air).isEmpty()
                        ||!w.getFluidState(floor).isEmpty())continue;
                BlockState prior=w.getBlockState(floor);
                if(!rock(prior))continue;
                String biome=w.getBiome(air).unwrapKey()
                     .map(k->k.location().toString()).orElse("");
                BlockState next=switch(biome) {
                    case "realscale:underground_springs" -> Blocks.CLAY.defaultBlockState();
                    case "realscale:deep_fungal_caverns" -> Blocks.MOSS_BLOCK.defaultBlockState();
                    case "realscale:glacial_subsurface" -> Blocks.PACKED_ICE.defaultBlockState();
                    case "realscale:damp_limestone_caverns" -> Blocks.CALCITE.defaultBlockState();
                    case "realscale:dry_karst_caves" -> Blocks.DRIPSTONE_BLOCK.defaultBlockState();
                    case "realscale:volcanic_caverns" -> Blocks.SMOOTH_BASALT.defaultBlockState();
                    default -> null;
                };
                if(next!=null&&!prior.equals(next) && w.setBlock(floor,next,2))changed++;
            }
            return changed>0;
        }
    }
    // ---- END NorthernCaveHabitatFeatureV4 ----

    // ---- BEGIN EXACT PRODUCTION SkyIslandFeature (nested; registered by RealScaleMod) ----
    /**
     * Actual floating stone/soil landmass in the special, opt-in sky-ocean preset.
     * Worldgen only, at most five islands per 512x512 block grid and only over
     * biome-source-confirmed far-ocean conditions. Does not touch sea or seabed.
     */
    public static final class SkyIslandFeature extends Feature<NoneFeatureConfiguration> {
        private static final ResourceKey<Biome> SKY = ResourceKey.create(Registries.BIOME,
                ResourceLocation.fromNamespaceAndPath("realscale", "sky_blossom_isles"));

        public SkyIslandFeature() { super(NoneFeatureConfiguration.CODEC); }

        @Override
        public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
            // The common path adds only a constant false branch; no clock reads.
            if (!WorldgenProfiler.ENABLED) return placeUnprofiled(context);
            long started = WorldgenProfiler.begin();
            try {
                return placeUnprofiled(context);
            } finally {
                WorldgenProfiler.finish(WorldgenProfiler.Section.SKY_ISLAND, started);
            }
        }

        private boolean placeUnprofiled(FeaturePlaceContext<NoneFeatureConfiguration> context) {
            if (!(context.chunkGenerator().getBiomeSource() instanceof SkyOceanBiomeSource))
                return false; // Existing worlds and vanilla presets cannot create islands.
            WorldGenLevel world = context.level();
            BlockPos origin = context.origin();
            boolean enhanced=TVAWorldGuard.hasNorthernFjordV4(context);
            int chunkX=Math.floorDiv(origin.getX(),16);
            int chunkZ=Math.floorDiv(origin.getZ(),16);
            boolean any=false;
            // Feature origins are placement-specific; use the actual chunk
            // center as the lattice reference. Neighbor cells are considered
            // because the footprint, not just an island center, may overlap.
            // Each chunk writes only its own columns.
            int referenceX=chunkX*16+8;
            int referenceZ=chunkZ*16+8;
            for (int gx=-1;gx<=1;gx++) for(int gz=-1;gz<=1;gz++) {
                SkyIslandLayout.Site base=SkyIslandLayout.at(
                    referenceX+gx*SkyIslandLayout.GRID,
                    referenceZ+gz*SkyIslandLayout.GRID);
                for (int slot=0;slot<SkyIslandLayout.SLOTS;slot++) {
                    SkyIslandLayout.Site island=SkyIslandLayout.satellite(base,slot);
                    int r=island.radius();
                    if (island.x()+r<chunkX*16 || island.x()-r>=chunkX*16+16
                            || island.z()+r<chunkZ*16 || island.z()-r>=chunkZ*16+16)
                        continue;
                    any |= placeIsland(world,island,chunkX,chunkZ,enhanced);
                }
            }
            return any;
        }

        private static boolean placeIsland(WorldGenLevel world, SkyIslandLayout.Site site,
                                           int chunkX,int chunkZ,boolean enhanced) {
            if (site.topY() + 8 >= world.getMaxBuildHeight()
                    || site.topY() - (enhanced?26:17) <= world.getMinBuildHeight())
                return false;
            // Biome selection enforces the sampled ocean isolation tier, so an
            // inactive island slot cannot write a real landmass.
            // Must be selected by the actual high-altitude climate sampler.
            // Query the biome INSIDE the decorating chunk. The island center
            // usually belongs to another chunk; asking WorldGenRegion for a
            // remote center biome can trigger unsafe neighbor chunk access.
            int localX = Math.max(chunkX * 16, Math.min(site.x(), chunkX * 16 + 15));
            int localZ = Math.max(chunkZ * 16, Math.min(site.z(), chunkZ * 16 + 15));
            if (!world.getBiome(new BlockPos(localX, site.topY(), localZ)).is(SKY))
                return false;
            final int r = site.radius();
            // Validate each column independently. One obstructed edge column must
            // not cancel the entire chunk (or leave the island center hollow).
            boolean placed=false;
            // Iterate only the 16x16 columns owned by this chunk. The previous
            // (-r..r)^2 scan discarded out-of-chunk positions AFTER calculating
            // their distance. This produces identical candidate coordinates,
            // iteration order, block choices and random calls.
            int minDx=Math.max(-r,chunkX*16-site.x());
            int maxDx=Math.min(r,chunkX*16+15-site.x());
            int minDz=Math.max(-r,chunkZ*16-site.z());
            int maxDz=Math.min(r,chunkZ*16+15-site.z());
            for (int dx=minDx; dx<=maxDx; dx++) for (int dz=minDz; dz<=maxDz; dz++) {
                double ratio = (double)(dx * dx + dz * dz) / (r * r);
                if (ratio > 1.0) continue;
                int top = enhanced ? SkyIslandShapeV4.top(site.topY(),dx,dz,ratio)
                        : surface(site,dx,dz,ratio);
                int thickness = enhanced ? SkyIslandShapeV4.thickness(ratio,dx,dz)
                        : thickness(ratio);
                int x = site.x() + dx, z = site.z() + dz;
                boolean clear=true;
                for (int y=top-thickness;y<=top;y++) {
                    BlockPos check=new BlockPos(x,y,z);
                    BlockState existing=world.getBlockState(check);
                    // Complete partially generated islands without overwriting
                    // anything other than our own known stone/soil palette.
                    if (!existing.isAir() && !isIslandMaterial(existing)) {
                        clear=false;
                        break;
                    }
                    if (!world.getFluidState(check).isEmpty()) {
                        clear=false;
                        break;
                    }
                }
                BlockPos above=new BlockPos(x,top+1,z);
                BlockState overhead=world.getBlockState(above);
                // Existing island beacons and flowers are legitimate; all other
                // solid obstacles prevent replacing their support columns.
                if (!overhead.isAir() && !isIslandDecoration(overhead)) clear=false;
                if (!world.getFluidState(above).isEmpty()) clear=false;
                if (!clear) continue;
                for (int y = top - thickness; y <= top; ++y) {
                    int depth = top - y;
                    BlockState state = depth == 0
                            ? (ratio < .65 && ((dx * 17 + dz * 11) & 7) == 0
                                    ? Blocks.MOSS_BLOCK.defaultBlockState()
                                    : Blocks.GRASS_BLOCK.defaultBlockState())
                            : depth <= 2 ? Blocks.DIRT.defaultBlockState()
                            : depth >= 6 && ((dx * 7 + dz * 13) & 3) == 0
                                    ? Blocks.END_STONE.defaultBlockState()
                                    : Blocks.STONE.defaultBlockState();
                    world.setBlock(new BlockPos(x, y, z), state, 2);
                    placed=true;
                }
                // V4-only sheltered understory, within the owning chunk.
                if(enhanced && ratio<.55 && ((x*13 ^ z*37)&31)==7) {
                    BlockPos shrub=new BlockPos(x,top+1,z);
                    BlockState plant=((x^z)&1)==0
                        ? Blocks.AZALEA.defaultBlockState()
                        : Blocks.FLOWERING_AZALEA.defaultBlockState();
                    if(world.getBlockState(shrub).isAir()&&plant.canSurvive(world,shrub))
                        world.setBlock(shrub,plant,2);
                }
                // Fantastical flowering meadow, solely using vanilla blocks.
                if (ratio < .70 && ((x * 31 ^ z * 17) & (enhanced?7:15)) == 3) {
                    BlockPos flower = new BlockPos(x, top + 1, z);
                    BlockState plant = ((x ^ z) & 1) == 0
                            ? Blocks.ALLIUM.defaultBlockState()
                            : Blocks.BLUE_ORCHID.defaultBlockState();
                    if (plant.canSurvive(world, flower))
                        world.setBlock(flower, plant, 2);
                }
            }
            // A glowing violet beacon makes the sky island recognizably fictional.
            BlockPos beacon = new BlockPos(site.x(), (enhanced
                    ? SkyIslandShapeV4.top(site.topY(),0,0,0)
                    : surface(site,0,0,0))+1, site.z());
            if (placed && Math.floorDiv(beacon.getX(),16)==chunkX
                    && Math.floorDiv(beacon.getZ(),16)==chunkZ
                    && world.getBlockState(beacon).isAir()
                    && isIslandMaterial(world.getBlockState(beacon.below()))) {
                world.setBlock(beacon, Blocks.AMETHYST_BLOCK.defaultBlockState(), 2);
                world.setBlock(beacon.above(), Blocks.SEA_LANTERN.defaultBlockState(), 2);
            }
            return placed;
        }

        private static boolean isIslandMaterial(BlockState state) {
            return state.is(Blocks.STONE) || state.is(Blocks.END_STONE)
                    || state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK)
                    || state.is(Blocks.MOSS_BLOCK);
        }
        private static boolean isIslandDecoration(BlockState state) {
            return state.is(Blocks.AMETHYST_BLOCK) || state.is(Blocks.SEA_LANTERN)
                    || state.is(Blocks.ALLIUM) || state.is(Blocks.BLUE_ORCHID);
        }

        private static int surface(SkyIslandLayout.Site site, int dx, int dz, double ratio) {
            return site.topY() - (int)Math.floor(3.0 * ratio);
        }
        private static int thickness(double ratio) {
            return 2 + (int)Math.floor(6.0 * (1.0 - ratio));
        }
    }
    // ---- END SkyIslandFeature ----

    // ---- BEGIN EXACT PRODUCTION SkyIslandShapeV4 (nested; registered by RealScaleMod) ----
    /** Pure Java v4 shapes, same legacy island centers but irregular cliffs and skirts. */
    public static final class SkyIslandShapeV4 {
        private SkyIslandShapeV4(){}
        public static int top(int centerY,int dx,int dz,double ratio) {
            double r=Math.max(0,Math.min(1,ratio));
            double folds=2.5*Math.sin(dx/5.8+dz/11.0)
                *Math.cos(dz/6.2-dx/13.0)*(1.0-r);
            return centerY-(int)Math.floor(4.0*r)+(int)Math.round(folds);
        }
        public static int thickness(double ratio,int dx,int dz) {
            double r=Math.max(0,Math.min(1,ratio));
            return 3+(int)Math.round(10.0*Math.pow(1.0-r,1.2))
                +(Math.floorMod(dx*13+dz*19,7)==0?2:0);
        }
    }
    // ---- END SkyIslandShapeV4 ----

    // ---- BEGIN EXACT PRODUCTION SkyIslandLayout (nested; registered by RealScaleMod) ----
    /**
     * Deterministic, world-seed-independent geometry for optional fantasy sky
     * archipelagos. The wrapped Minecraft climate source decides which candidate
     * cells are actually far enough from land to create islands.
     */
    public static final class SkyIslandLayout {
        private SkyIslandLayout() {}
        public static final int GRID = 320;
        public static final int MIN_ISOLATION = 256;
        public static final int MAX_ISOLATION = 640;
        public static final int SLOTS = 5;
        // Five island footprints lie at the base center or +/-80 X/Z. Every
        // satellite radius is 7..11; a 94-block axis bound is conservative.
        // This rejects most sky-biome samples before allocating any satellites.
        public static final int MAX_SITE_REACH = 94;
        // Base tops are 183..203; satellite tops add -3..+3, and
        // insideBiome extends -13..+8 from each top. Every sky island biome
        // sample outside this inclusive height range is impossible.
        public static final int MIN_BIOME_Y = 167;
        public static final int MAX_BIOME_Y = 214;
        private static final int DIRECTIONS = 16;
        // More numerous small near-shore islets and denser far-ocean clusters.
        // Ocean probes progressively increase the required offshore isolation.
        private static final int[] PROBE_RADII = {64,128,192,256,352,448,544,640};
        private static final int[] PROBE_X = new int[PROBE_RADII.length*DIRECTIONS];
        private static final int[] PROBE_Z = new int[PROBE_X.length];
        private static final int[][] SATELLITES = {{0,0},{80,0},{-80,0},{0,80},{0,-80}};
        static {
            for (int n=0;n<PROBE_RADII.length;n++) for (int i=0;i<DIRECTIONS;i++) {
                double angle=i*2.0*Math.PI/DIRECTIONS;
                PROBE_X[n*DIRECTIONS+i]=(int)Math.round(PROBE_RADII[n]*Math.cos(angle));
                PROBE_Z[n*DIRECTIONS+i]=(int)Math.round(PROBE_RADII[n]*Math.sin(angle));
            }
        }
        public record Site(int x,int z,int topY,int radius) {}
        private static long mix(long x) {
            x^=x>>>30; x*=0xbf58476d1ce4e5b9L;
            x^=x>>>27; x*=0x94d049bb133111ebL;
            return x^(x>>>31);
        }
        public static Site at(int blockX,int blockZ) {
            long gx=Math.floorDiv((long)blockX+GRID/2,GRID);
            long gz=Math.floorDiv((long)blockZ+GRID/2,GRID);
            long hash=mix((gx*0x9e3779b97f4a7c15L)
                   ^ (gz*0xc2b2ae3d27d4eb4fL) ^ 0x3bc7f54a83e1d209L);
            // Keep each satellite in the same lattice cell as its parent.
            // Previously +/-128 jitter plus +/-96 offsets escaped the cell,
            // so home chunks selected a DIFFERENT base and many islands vanished.
            int x=(int)(gx*GRID-48+Math.floorMod(hash,96L));
            int z=(int)(gz*GRID-48+Math.floorMod(hash>>>15,96L));
            return new Site(x,z,183+(int)Math.floorMod(hash>>>30,21L),
                    10+(int)Math.floorMod(hash>>>47,5L));
        }
        /** The main island plus four distant, independently shaped satellites. */
        /** Conservative per-axis reject; never hides a valid sky island. */
        public static boolean mayIntersectIslands(Site base,int x,int z) {
            return Math.abs((long)x-base.x())<=MAX_SITE_REACH
                    && Math.abs((long)z-base.z())<=MAX_SITE_REACH;
        }
        public static Site satellite(Site base,int slot) {
            if(slot<0||slot>=SLOTS) throw new IllegalArgumentException("invalid sky slot");
            if(slot==0)return base;
            long hash=mix((((long)base.x())<<32) ^ (base.z()&0xffffffffL)
                    ^ (0x9e3779b97f4a7c15L*slot));
            return new Site(base.x()+SATELLITES[slot][0],
                    base.z()+SATELLITES[slot][1],
                    base.topY()+(int)Math.floorMod(hash,7L)-3,
                    7+(int)Math.floorMod(hash>>>27,5L));
        }
        /** 0 absent, 1 sparse, 2 a three-island group, 3 a five-island cluster.
         * Returns only a *sampled lower bound* on coast isolation, NOT an exact
         * shortest distance to every land block. The first failed ring stops
         * all further checks, preventing needless climate lookups near coasts.
         */
        public static int oceanTier(Site base,
                BiPredicate<Integer,Integer> deep,
                BiPredicate<Integer,Integer> ocean) {
            if(!deep.test(base.x(),base.z()))return 0;
            for(int ring=0;ring<PROBE_RADII.length;ring++) {
                int first=ring*DIRECTIONS;
                for(int i=0;i<DIRECTIONS;i++)
                    if(!ocean.test(base.x()+PROBE_X[first+i],
                                   base.z()+PROBE_Z[first+i]))
                        return ring<4?0:ring<6?1:2;
            }
            return 3;
        }
        public static int countForTier(int tier) {
            return switch(tier) {
                case 1 -> 1;
                case 2 -> 3;
                case 3 -> 5;
                default -> 0;
            };
        }
        public static boolean remoteDeepOcean(Site base,
                BiPredicate<Integer,Integer> deep,
                BiPredicate<Integer,Integer> ocean) {
            return oceanTier(base,deep,ocean)>0;
        }
        public static boolean insideBiome(Site site,int x,int y,int z) {
            long dx=(long)x-site.x(), dz=(long)z-site.z();
            return dx*dx+dz*dz <= (long)site.radius()*site.radius()
                    && y>=site.topY()-13 && y<=site.topY()+8;
        }
    }
    // ---- END SkyIslandLayout ----
    // ===== END REALSCALE ONE-FILE WORLDGEN V4 =====
}
