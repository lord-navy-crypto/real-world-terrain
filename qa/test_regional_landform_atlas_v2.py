"""Preserve original region-v1 and measured DEM; only v2 gets new geography."""
import json
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/"neoforge/src/main/resources/data/realscale/worldgen"
JAVA=ROOT/"neoforge/src/main/java/org/realscale/neoforge"
def read(path):
    return json.loads(path.read_text(encoding="utf-8"))

class GlobalHighlandAtlasV2(unittest.TestCase):
    def test_v1_noise_and_exact_measured_settings_are_not_overwritten(self):
        old=read(RES/"noise_settings/tva_regional_atlas.json")
        new=read(RES/"noise_settings/tva_regional_atlas_v2.json")
        self.assertEqual(old["noise"],new["noise"])
        for key in ("sea_level","default_block","default_fluid",
                    "surface_rule","aquifers_enabled","ore_veins_enabled"):
            self.assertEqual(old[key],new[key],key)
        fields=("depth","initial_density_without_jaggedness","final_density")
        lhs=dict(old["noise_router"]);rhs=dict(new["noise_router"])
        for name in fields:
            lhs.pop(name);rhs.pop(name)
        self.assertEqual(lhs,rhs,"climate, temperature, caves or fluids changed")
        self.assertEqual(new["noise_router"]["final_density"]["type"],
                         "realscale:grand_overworld_caverns")
        self.assertEqual(new["noise_router"]["final_density"]["small_caves"],
                         "realscale:realism_max/cave_core")
        self.assertEqual(new["noise_router"]["final_density"]["base"],
                         "realscale:tva_regional_atlas_v2/terrain_density")
        self.assertEqual(new["noise_router"]["depth"],
                         "realscale:tva_regional_atlas_v2/terrain_density")
        self.assertTrue((RES/"noise_settings/usgs_big_sur_v2.json").is_file())
        self.assertEqual(read(RES/"world_preset/tva_measured_biodiversity_end3_spawn3_v3.json")[
                         "dimensions"]["minecraft:overworld"]["generator"]["settings"],
                         "realscale:usgs_big_sur_v2")

    def test_only_v2_calls_new_density_codec_and_old_v1_is_untouched(self):
        old=read(RES/"density_function/tva_regional_atlas/height_signal.json")
        new=read(RES/"density_function/tva_regional_atlas_v2/height_signal.json")
        self.assertEqual(old["type"],"minecraft:cache_2d")
        self.assertEqual(new["type"],"minecraft:cache_2d")
        self.assertEqual(old["argument"]["type"],"realscale:regional_landform")
        self.assertEqual(new["argument"]["type"],"realscale:regional_landform_diverse_v2")
        v1=dict(old["argument"]);v2=dict(new["argument"])
        v1.pop("type");v2.pop("type")
        self.assertEqual(v1,v2)
        density=(JAVA/"RegionalLandformDensity.java").read_text()
        math=(JAVA/"RegionalLandformMath.java").read_text()
        registry=(JAVA/"RealScaleMod.java").read_text()
        self.assertIn('RegionalLandformDensity.V2_MAP_CODEC',registry)
        self.assertIn('"regional_landform_diverse_v2"',registry)
        self.assertIn("return diverseV2 ? V2_DISPATCH : DISPATCH;",density)
        self.assertIn("base.mapAll(visitor),continents.mapAll(visitor),selector.mapAll(visitor),diverseV2",density)
        self.assertIn("CELL_V2 = 8192",math)
        self.assertIn("KINDS_V2 = 16",math)
        for name in ("TIBETAN_GLACIAL_TROUGH","TIBETAN_ENDORHEIC_BASIN",
                     "ANDEAN_SALT_PAN","ANDEAN_BOFEDEL_SHELF",
                     "ECUADOR_VOLCANIC_CHAIN","YUNGUI_FENGLIN",
                     "WULONG_TIAN_KENG","SICHUAN_ESCARPMENT"):
            self.assertIn(name,math)

    def test_new_user_world_selection_preserves_cross_realm_sky_and_old_preset(self):
        old=read(RES/"world_preset/tva_regional_landform_expedition_three_realms.json")
        new=read(RES/"world_preset/tva_regional_landform_expedition_v2_three_realms.json")
        for dimension in ("minecraft:the_nether","minecraft:the_end"):
            self.assertEqual(new["dimensions"][dimension],old["dimensions"][dimension])
        before=old["dimensions"]["minecraft:overworld"]
        after=new["dimensions"]["minecraft:overworld"]
        self.assertEqual(after["type"],before["type"])
        self.assertEqual(after["generator"]["biome_source"],before["generator"]["biome_source"])
        self.assertEqual(after["generator"]["settings"],"realscale:tva_regional_atlas_v2")
        self.assertEqual(before["generator"]["settings"],"realscale:tva_regional_atlas")
        self.assertEqual(after["generator"]["biome_source"]["type"],"realscale:sky_ocean")
        self.assertEqual(after["generator"]["biome_source"]["source"]["type"],"realscale:cross_realm_spawn3")
        menu=read(ROOT/"neoforge/src/main/resources/data/minecraft/tags/worldgen/world_preset/normal.json")
        self.assertEqual(menu["values"][-1],"realscale:tva_regional_landform_expedition_v2_three_realms")
        self.assertEqual(len(menu["values"]),11)
        self.assertEqual(len(set(menu["values"])),11)

if __name__=="__main__":unittest.main()
