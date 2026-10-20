"""Pure structural QA: eight height-density landforms vs unchanged real DEM worlds."""
import json
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/"neoforge/src/main/resources/data/realscale/worldgen"
JAVA=ROOT/"neoforge/src/main/java/org/realscale/neoforge"
NAME="tva_regional_atlas"
PRESET="tva_regional_landform_expedition_three_realms"
def load(p): return json.loads(p.read_text(encoding="utf-8"))

class RegionalLandformAtlasQA(unittest.TestCase):
    def test_only_experimental_settings_use_morphology_and_originals_are_referenced(self):
        old=load(DATA/"noise_settings/tva_natural_smoothed.json")
        new=load(DATA/("noise_settings/"+NAME+".json"))
        self.assertEqual(new["noise"],old["noise"])
        self.assertEqual(new["sea_level"],old["sea_level"])
        self.assertEqual(new["surface_rule"],old["surface_rule"])
        self.assertEqual(new["default_fluid"],old["default_fluid"])
        self.assertEqual(new["aquifers_enabled"],old["aquifers_enabled"])
        a=dict(old["noise_router"]);b=dict(new["noise_router"])
        for field in ("depth","initial_density_without_jaggedness","final_density"):
            a.pop(field);b.pop(field)
        self.assertEqual(a,b,"Climate, temperature, rivers, caves and humidity changed")
        self.assertEqual(new["noise_router"]["final_density"]["type"],"realscale:grand_overworld_caverns")
        self.assertEqual(new["noise_router"]["final_density"]["small_caves"],
                         "realscale:realism_max/cave_core")
        self.assertEqual(new["noise_router"]["depth"],"realscale:"+NAME+"/terrain_density")
        self.assertEqual(new["noise_router"]["initial_density_without_jaggedness"],
                         "realscale:"+NAME+"/preliminary_density")
        self.assertEqual(new["noise_router"]["final_density"]["base"],
                         "realscale:"+NAME+"/terrain_density")
        h=load(DATA/("density_function/"+NAME+"/height_signal.json"))
        self.assertEqual(h["type"],"minecraft:cache_2d")
        self.assertEqual(h["argument"]["type"],"realscale:regional_landform")
        self.assertEqual(h["argument"]["continents"],"realscale:realism_max/continental_field")
        self.assertEqual(h["argument"]["base"],
                         load(DATA/"density_function/tva_natural_smoothed/height_signal.json")["argument"])
        terrain=load(DATA/("density_function/"+NAME+"/terrain_density.json"))
        self.assertEqual(terrain["argument1"],"realscale:"+NAME+"/height_signal")
        self.assertEqual(terrain["argument2"],
                         load(DATA/"density_function/tva_natural_smoothed/terrain_density.json")["argument2"])
        preliminary=load(DATA/("density_function/"+NAME+"/preliminary_density.json"))
        self.assertEqual(preliminary["argument1"],"realscale:"+NAME+"/terrain_density")
        self.assertEqual(preliminary["argument2"],.390625)

    def test_its_preset_retains_end_invasion_and_sky_but_never_replaces_usgs(self):
        original=load(DATA/"world_preset/tva_sacred_biodiversity_end3_spawn3_v3.json")
        regional=load(DATA/("world_preset/"+PRESET+".json"))
        origDims=original["dimensions"];nowDims=regional["dimensions"]
        for dim in ("minecraft:the_nether","minecraft:the_end"):
            self.assertEqual(nowDims[dim],origDims[dim])
        self.assertEqual(nowDims["minecraft:overworld"]["type"],origDims["minecraft:overworld"]["type"])
        oldGenerator=origDims["minecraft:overworld"]["generator"]
        newGenerator=nowDims["minecraft:overworld"]["generator"]
        base_source=json.loads(json.dumps(oldGenerator["biome_source"]))
        # The player-facing Natural Exploration preset now also preserves
        # historical geological wonders; regional archive did not opt in.
        if base_source["source"]["source"]["type"]=="realscale:geo_wonders_opt_in":
            base_source["source"]["source"]=base_source["source"]["source"]["source"]
        self.assertEqual(newGenerator["biome_source"],base_source)
        self.assertEqual(newGenerator["type"],oldGenerator["type"])
        self.assertEqual(newGenerator["settings"],"realscale:"+NAME)
        self.assertEqual(oldGenerator["settings"],"realscale:tva_cinematic_landforms_v2")
        self.assertEqual(load(DATA/"world_preset/tva_measured_biodiversity_end3_spawn3_v3.json")[
                         "dimensions"]["minecraft:overworld"]["generator"]["settings"],
                         "realscale:usgs_big_sur_v2")
        menu=load(ROOT/"neoforge/src/main/resources/data/minecraft/tags/worldgen/world_preset/normal.json")
        # Legacy regional experiments remain inspectable, but are not
        # duplicate choices in the four-mode player menu.
        self.assertNotIn("realscale:"+PRESET,menu["values"])
        self.assertNotIn("realscale:tva_regional_landform_expedition_v2_three_realms",menu["values"])
        self.assertIn("realscale:tva_measured_global_landforms_three_realms",menu["values"])
        self.assertEqual(len(menu["values"]),4)
        self.assertEqual(len(set(menu["values"])),4)

    def test_density_codec_and_eight_distinct_geometry_kinds(self):
        code=(JAVA/"RealScaleMod.java").read_text(encoding="utf-8")
        density=(JAVA/"RegionalLandformDensity.java").read_text(encoding="utf-8")
        math=(JAVA/"RegionalLandformMath.java").read_text(encoding="utf-8")
        self.assertIn('"regional_landform"), RegionalLandformDensity.MAP_CODEC',code)
        self.assertIn("DensityFunction.HOLDER_HELPER_CODEC",density)
        self.assertIn("base.mapAll(visitor)",density)
        self.assertIn("continents.mapAll(visitor)",density)
        self.assertIn("selector.mapAll(visitor)",density)
        self.assertIn("RegionalLandformMath.gate(continent,before)",density)
        self.assertIn("CELL = 4096",math)
        for shape in ("TIBETAN_PLATEAU","TIBETAN_WIDE_VALLEY","ANDEAN_ALTIPLANO",
                      "EQUATORIAL_VOLCANO","SICHUAN_BASIN","CHONGQING_RIDGES",
                      "YUNGUI_KARST","YUNGUI_BAZI"):
            self.assertIn(shape,math)
        self.assertIn("static double effect(",math)
        self.assertIn("static double gate(",math)
        self.assertIn("static int kind(",math)
        self.assertIn("Math.floorDiv(ctx.blockX(),RegionalLandformMath.CELL)",density)

if __name__=="__main__":unittest.main()
