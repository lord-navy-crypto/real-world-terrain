"""Global v3 geomorphology: worldgen compatibility, physical density and new IDs."""
import json
import unittest
from pathlib import Path
R=Path(__file__).resolve().parents[1]
DATA=R/"neoforge/src/main/resources/data/realscale/worldgen"
JAVA=R/"neoforge/src/main/java/org/realscale/neoforge"
def load(path):return json.loads(path.read_text(encoding="utf-8"))
class GlobalRareGeomorphologyV3(unittest.TestCase):
    def test_new_source_is_separate_from_v1_v2_and_measured(self):
        old=load(DATA/"noise_settings/tva_regional_atlas_v2.json")
        new=load(DATA/"noise_settings/tva_global_landform_v3.json")
        for key in ("noise","sea_level","default_block","default_fluid",
                    "surface_rule","aquifers_enabled","ore_veins_enabled"):
            self.assertEqual(old[key],new[key],key)
        lhs=dict(old["noise_router"]);rhs=dict(new["noise_router"])
        for name in ("depth","initial_density_without_jaggedness","final_density"):
            lhs.pop(name);rhs.pop(name)
        self.assertEqual(lhs,rhs,"climate, cave and aquifer systems changed")
        self.assertEqual(new["noise_router"]["final_density"]["type"],
                         "realscale:grand_overworld_caverns")
        self.assertEqual(new["noise_router"]["final_density"]["small_caves"],
                         "realscale:realism_max/cave_core")
        self.assertEqual(new["noise_router"]["depth"],"realscale:tva_global_landform_v3/terrain_density")
        self.assertEqual(new["noise_router"]["final_density"]["base"],
                         "realscale:tva_global_landform_v3/terrain_density")
        self.assertTrue((DATA/"noise_settings/usgs_big_sur_v2.json").exists())

    def test_v3_density_codec_preserves_old_v2_original_math(self):
        new=load(DATA/"density_function/tva_global_landform_v3/height_signal.json")
        old=load(DATA/"density_function/tva_regional_atlas_v2/height_signal.json")
        self.assertEqual(new["type"],"minecraft:cache_2d")
        self.assertEqual(new["argument"]["type"],"realscale:regional_landform_global_v3")
        self.assertEqual(old["argument"]["type"],"realscale:regional_landform_diverse_v2")
        self.assertEqual({k:v for k,v in new["argument"].items() if k!="type"},
                         {k:v for k,v in old["argument"].items() if k!="type"})
        t=load(DATA/"density_function/tva_global_landform_v3/terrain_density.json")
        self.assertEqual(t["argument1"],"realscale:tva_global_landform_v3/height_signal")
        p=load(DATA/"density_function/tva_global_landform_v3/preliminary_density.json")
        self.assertEqual(p["argument1"],"realscale:tva_global_landform_v3/terrain_density")
        source=(JAVA/"RegionalGlobalLandformDensity.java").read_text()
        registry=(JAVA/"RealScaleMod.java").read_text()
        self.assertIn('"regional_landform_global_v3"',registry)
        self.assertIn("RegionalGlobalLandformDensity.MAP_CODEC",registry)
        self.assertIn("DensityFunction.HOLDER_HELPER_CODEC",source)
        self.assertIn("base.mapAll(visitor)",source)
        self.assertIn("selector.mapAll(visitor)",source)
        self.assertIn("Math.floorDiv(x, RegionalLandformMath.CELL_V3)",source)
        self.assertIn("RegionalLandformMath.gate(continental, current)",source)
        self.assertIn("ThreadLocal<SiteCache>",source)

    def test_world_can_be_selected_without_changing_previous_worlds(self):
        old=load(DATA/"world_preset/tva_regional_landform_expedition_v2_three_realms.json")
        new=load(DATA/"world_preset/tva_global_landform_expedition_v3_three_realms.json")
        for dim in ("minecraft:the_end","minecraft:the_nether"):
            self.assertEqual(new["dimensions"][dim],old["dimensions"][dim])
        for key in ("type","biome_source"):
            self.assertEqual(new["dimensions"]["minecraft:overworld"]["generator"][key],
                             old["dimensions"]["minecraft:overworld"]["generator"][key])
        self.assertEqual(new["dimensions"]["minecraft:overworld"]["generator"]["settings"],
                         "realscale:tva_global_landform_v3")
        self.assertEqual(old["dimensions"]["minecraft:overworld"]["generator"]["settings"],
                         "realscale:tva_regional_atlas_v2")
        menu=load(R/"neoforge/src/main/resources/data/minecraft/tags/worldgen/world_preset/normal.json")
        self.assertEqual(len(menu["values"]),4)
        self.assertEqual(len(set(menu["values"])),4)
        self.assertNotIn("realscale:tva_global_landform_expedition_v3_three_realms",menu["values"])
        self.assertNotIn("realscale:tva_regional_landform_expedition_v2_three_realms",menu["values"])
        self.assertIn("realscale:tva_measured_usgs_fitted_v1_three_realms",menu["values"])
        self.assertEqual(load(DATA/"world_preset/tva_measured_biodiversity_end3_spawn3_v3.json")[
                         "dimensions"]["minecraft:overworld"]["generator"]["settings"],
                         "realscale:usgs_big_sur_v2")

    def test_eight_new_geological_styles_not_duplicate_older_ids(self):
        java=(JAVA/"RegionalLandformMath.java").read_text()
        self.assertIn("KINDS = 8;",java)
        self.assertIn("KINDS_V2 = 16;",java)
        self.assertIn("KINDS_V3 = 24;",java)
        self.assertIn("CELL_V3 = 12288;",java)
        for name in ("GUIANA_TEPUI","TROPICAL_INSELBERGS","GLACIAL_DRUMLIN_FIELD",
                     "GLACIAL_ESKER_RIDGE","NAMIB_STAR_DUNES","EAST_AFRICAN_RIFT",
                     "UTAH_SLOT_CANYON","ICELAND_CALDERA"):
            self.assertIn(name,java)
        for lang in ("en_us","zh_cn"):
            text=load(R/("neoforge/src/main/resources/assets/realscale/lang/"+lang+".json"))
            self.assertTrue(text["generator.realscale.tva_global_landform_expedition_v3_three_realms"])

if __name__=="__main__":unittest.main()
