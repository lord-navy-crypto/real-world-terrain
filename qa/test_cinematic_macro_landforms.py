"""A genuine terrain-density upgrade for the curated Natural Exploration world."""
import copy
import json
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/"neoforge/src/main/resources/data/realscale/worldgen"
JAVA=ROOT/"neoforge/src/main/java/org/realscale/neoforge"
TEST_JAVA=ROOT/"neoforge/src/test/java/org/realscale/neoforge"

def load(file):return json.loads(file.read_text(encoding="utf-8"))
class CinematicMacroWorldgen(unittest.TestCase):
    def test_new_noise_exists_and_preserves_originals(self):
        historic=load(RES/"noise_settings/tva_natural_smoothed.json")
        cinematic=load(RES/"noise_settings/tva_cinematic_landforms_v1.json")
        settings=copy.deepcopy(cinematic)
        original=copy.deepcopy(historic)
        for key in ("depth","final_density","initial_density_without_jaggedness"):
            pass
        old_router=original["noise_router"]
        new_router=settings["noise_router"]
        self.assertEqual(new_router["depth"],
                         "realscale:tva_cinematic_landforms_v1/terrain_density")
        self.assertEqual(new_router["initial_density_without_jaggedness"],
                         "realscale:tva_cinematic_landforms_v1/preliminary_density")
        self.assertEqual(new_router["final_density"]["base"],
                         "realscale:tva_cinematic_landforms_v1/terrain_density")
        old_router["depth"]=new_router["depth"]
        old_router["initial_density_without_jaggedness"] = new_router["initial_density_without_jaggedness"]
        old_router["final_density"]["base"] = new_router["final_density"]["base"]
        self.assertEqual(original,settings,
            "Cinematic noise must preserve aquifers, caves, vanilla ore, climate and the surface rules")

    def test_height_function_is_real_2d_density_not_surface_decoration(self):
        base=RES/"density_function/tva_cinematic_landforms_v1"
        height=load(base/"height_signal.json")
        self.assertEqual(height["type"],"minecraft:cache_2d")
        macro=height["argument"]
        self.assertEqual(macro["type"],"realscale:cinematic_landforms_v1")
        self.assertEqual(macro["base"]["type"],"minecraft:cache_2d")
        self.assertEqual(macro["continents"],"realscale:realism_max/continental_field")
        self.assertEqual(macro["temperature"]["noise"],"minecraft:temperature")
        self.assertEqual(macro["humidity"]["noise"],"minecraft:vegetation")
        terrain=load(base/"terrain_density.json")
        preliminary=load(base/"preliminary_density.json")
        self.assertEqual(terrain["argument1"],
                         "realscale:tva_cinematic_landforms_v1/height_signal")
        self.assertEqual(preliminary["argument1"],
                         "realscale:tva_cinematic_landforms_v1/terrain_density")

    def test_only_one_player_preset_has_macro_noise(self):
        preset_dir=RES/"world_preset"
        names=["tva_sacred_biodiversity_end3_spawn3_v3",
               "tva_measured_global_landforms_three_realms",
               "tva_alpine_three_realms","tva_canyon_three_realms"]
        settings=[load(preset_dir/(name+".json"))["dimensions"]
                  ["minecraft:overworld"]["generator"]["settings"] for name in names]
        self.assertEqual(settings,["realscale:tva_cinematic_landforms_v1",
                "realscale:usgs_global_hybrid_marine",
                "realscale:tva_alpine_smoothed","realscale:tva_canyon_smoothed"])
        self.assertEqual(load(RES/"noise_settings/tva_natural_smoothed.json")
                         ["noise_router"]["depth"],
                         "realscale:tva_natural_smoothed/terrain_density")
        tag=ROOT/"neoforge/src/main/resources/data/minecraft/tags/worldgen/world_preset"
        self.assertEqual(len(load(tag/"normal.json")["values"]),4)
        self.assertEqual(load(tag/"normal.json")["values"],load(tag/"extended.json")["values"])

    def test_codec_registration_and_no_extra_biomes_or_worlds(self):
        mod=(JAVA/"RealScaleMod.java").read_text()
        math=(JAVA/"CinematicLandformMath.java").read_text()
        density=(JAVA/"CinematicLandformDensity.java").read_text()
        self.assertIn('"cinematic_landforms_v1"), CinematicLandformDensity.MAP_CODEC',mod)
        for phrase in ("Math.floorDiv(x,CinematicLandformMath.CELL)",
                       "DensityFunction.SinglePointContext",
                       "base.mapAll(visitor)","temperature.mapAll(visitor)",
                       "humidity.mapAll(visitor)","continents.mapAll(visitor)"):
            self.assertIn(phrase,density)
        for phrase in ("SANDSTONE_TOWERS","KARST_TOWERS",
                       "CAPROCK_MESA","INCISED_CANYON",
                       "towerDistance","envelope","gate"):
            self.assertIn(phrase,math)

    def test_standalone_actual_java_terrain_math(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("Full Java toolchain unavailable in Python-only environment")
        with tempfile.TemporaryDirectory(prefix="realscale-macro-") as d:
            sources=[JAVA/"CinematicLandformMath.java",
                     TEST_JAVA/"CinematicLandformMathSmoke.java"]
            subprocess.run(["javac","-d",d,*map(str,sources)],check=True,
                           stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,
                           timeout=45)
            out=subprocess.run(["java","-cp",d,
                                "org.realscale.neoforge.CinematicLandformMathSmoke"],
                               check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE,
                               text=True,timeout=45)
            self.assertIn("CINEMATIC_MACRO_RELIEF_PASS",out.stdout)

if __name__=="__main__":unittest.main()
