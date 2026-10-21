"""Integrated USGS ocean renewal: no new world mode, preserve old marine data."""
import copy,json,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
WG=ROOT/"neoforge/src/main/resources/data/realscale/worldgen"
J=ROOT/"neoforge/src/main/java/org/realscale/neoforge"
def read(path):return json.loads(path.read_text(encoding="utf-8"))
class IntegratedMarineRenewal(unittest.TestCase):
    def test_ocean_density_only_layers_on_existing_hybrid(self):
        ocean=read(WG/"density_function/usgs_global_hybrid_marine/terrain_density.json")
        self.assertEqual(ocean["type"],"realscale:marine_seafloor")
        self.assertEqual(ocean["base"],"realscale:usgs_global_hybrid/terrain_density")
        self.assertEqual(ocean["continents"],"realscale:realism_max/continental_field")
        self.assertEqual(ocean["procedural_height"],{
            "type":"realscale:measured_horizontal_memo",
            "input":"realscale:realism_max/height_signal"
        })
        self.assertEqual(ocean["measured_blend_meters"],2048)
        self.assertTrue((WG/"density_function/usgs_global_hybrid/terrain_density.json").exists())
        self.assertTrue((WG/"density_function/usgs_big_sur_v2/terrain_density.json").exists())
        source=(J/"MarineSeafloorDensity.java").read_text()
        math=(J/"OceanFloorMorphologyMath.java").read_text()
        registry=(J/"RealScaleMod.java").read_text()
        self.assertIn('"marine_seafloor"',registry)
        self.assertIn("MarineSeafloorDensity.MAP_CODEC",registry)
        self.assertIn("RealDemSeamlessMath.cachedWeightOutside",source)
        self.assertIn("measuredBlendMeters",source)
        self.assertIn("return measuredOrNatural;",source)
        self.assertIn("base.mapAll(visitor)",source)
        self.assertIn("continents.mapAll(visitor)",source)
        self.assertIn("selector.mapAll(visitor)",source)
        self.assertIn("intRange(256,4096)",source)
        self.assertIn("TYPES=8;",math)
        self.assertIn("CELL=8192;",math)
        for kind in ("ABYSSAL_PLAIN","MID_OCEAN_RIDGE","HADAL_TRENCH",
                     "SEAMOUNT_CHAIN","GUYOT","SUBMARINE_CANYON",
                     "ABYSSAL_HILLS","TRANSFORM_FRACTURE_ZONE"):
            self.assertIn(kind,math)

    def test_climate_sea_level_original_biomes_ocean_palette_stay_identical(self):
        old=read(WG/"noise_settings/usgs_global_hybrid.json")
        ocean=read(WG/"noise_settings/usgs_global_hybrid_marine.json")
        before=copy.deepcopy(old);after=copy.deepcopy(ocean)
        oldR=before.pop("noise_router");newR=after.pop("noise_router")
        self.assertEqual(before,after,"Fluid, sea level, biome surface, sky bounds mutated")
        for entry in ("depth","initial_density_without_jaggedness","final_density"):
            oldR.pop(entry);newR.pop(entry)
        self.assertEqual(oldR,newR,"Changing climate or ocean aquifer is forbidden")
        newF=ocean["noise_router"]["final_density"]
        self.assertEqual(newF["type"],"realscale:grand_overworld_caverns")
        self.assertEqual(newF["base"]["type"],"realscale:range_min_exact")
        self.assertEqual(newF["small_caves"],"realscale:realism_max/cave_core")
        self.assertEqual(newF["base"]["base"],"realscale:usgs_global_hybrid_marine/terrain_density")
        self.assertEqual(newF["base"]["cave"],"realscale:usgs_global_hybrid_marine/cave_density")
        oldC=read(WG/"density_function/usgs_global_hybrid/cave_density.json")
        actualC=read(WG/"density_function/usgs_global_hybrid_marine/cave_density.json")
        expected=json.loads(json.dumps(oldC).replace("usgs_global_hybrid/terrain_density",
                         "usgs_global_hybrid_marine/terrain_density"))
        self.assertEqual(actualC,expected)
        self.assertEqual(read(WG/"density_function/usgs_global_hybrid_marine/preliminary_density.json")[
                         "argument1"],"realscale:usgs_global_hybrid_marine/terrain_density")

    def test_only_unified_measured_experiment_is_changed(self):
        fused=read(WG/"world_preset/tva_measured_global_landforms_three_realms.json")
        canonical=read(WG/"world_preset/tva_measured_biodiversity_end3_spawn3_v3.json")
        self.assertEqual(fused["dimensions"]["minecraft:overworld"]["generator"]["settings"],
                         "realscale:usgs_global_hybrid_marine")
        self.assertEqual(canonical["dimensions"]["minecraft:overworld"]["generator"]["settings"],
                         "realscale:usgs_big_sur_v2")
        for dim in ("minecraft:the_nether","minecraft:the_end"):
            self.assertEqual(fused["dimensions"][dim],canonical["dimensions"][dim])
        actual=fused["dimensions"]["minecraft:overworld"]["generator"]["biome_source"]
        baseline=canonical["dimensions"]["minecraft:overworld"]["generator"]["biome_source"]
        # Shared ecological and geological-wonder layers are now part of the
        # four player presets, while this mode retains its marine/USGS density.
        prior=json.loads(json.dumps(baseline))
        prior["source"]["source"]={"type":"realscale:geo_wonders_opt_in",
                                   "source":prior["source"]["source"]}
        # New player-facing Measured worlds use ordinary spawn; legacy fixture
        # still keeps its original origin End-invasion codec.
        prior["source"]["source"]["source"]["type"]="realscale:cross_realm_free_spawn_v1"
        self.assertEqual(actual,prior)
        self.assertEqual(fused["dimensions"]["minecraft:overworld"]["generator"]["biome_source"]["type"],
                         "realscale:sky_ocean")
        values=read(ROOT/"neoforge/src/main/resources/data/minecraft/tags/worldgen/world_preset/normal.json")["values"]
        self.assertEqual(len(values),4,"Only four playable world modes")
        self.assertEqual(len(set(values)),4)
        self.assertIn("realscale:tva_measured_global_landforms_three_realms",values)

    def test_existing_ocean_features_do_not_receive_global_biome_modifiers(self):
        b=ROOT/"neoforge/src/main/resources/data/realscale/neoforge/biome_modifier"
        self.assertFalse((b/"ocean_seagrass_patches.json").exists())
        self.assertFalse((b/"ocean_sediment_stones.json").exists())
        self.assertTrue((WG/"configured_feature/coral_reef_floor.json").exists())
        self.assertTrue((WG/"placed_feature/coral_reef_floor.json").exists())

if __name__=="__main__":unittest.main()
