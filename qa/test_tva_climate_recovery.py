#!/usr/bin/env python3
"""Prevent TVA climate/cave regression: check actually used registry graphs."""
import json
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
WORLDGEN=ROOT/"neoforge/src/main/resources/data/realscale/worldgen"
SRC=ROOT/"neoforge/src/main/java/org/realscale/neoforge"

PRESETS={
    "tva_natural_smoothed":"tva_sacred_timeline_three_realms",
    "tva_alpine_smoothed":"tva_alpine_three_realms",
    "tva_canyon_smoothed":"tva_canyon_three_realms",
    "usgs_big_sur_v2":"tva_measured_three_realms",
}

def read(path):
    return json.loads(path.read_text(encoding="utf-8"))

class RealClimateIntegrationTest(unittest.TestCase):
    def test_public_presets_activate_all_climate_layers_not_just_algorithms(self):
        for name,world in PRESETS.items():
            with self.subTest(name=name):
                ns=read(WORLDGEN/"noise_settings"/(name+".json"))
                router=ns["noise_router"]
                self.assertEqual(router["temperature"],f"realscale:{name}/biome_temperature")
                profile=read(WORLDGEN/"density_function"/name/"biome_temperature.json")
                self.assertEqual(profile["type"],"realscale:climate_budget")
                self.assertEqual(profile["max_cooling"],.60)
                self.assertEqual(profile["max_warming"],.45)
                self.assertEqual(profile["original"]["type"],"minecraft:shifted_noise")
                self.assertEqual(profile["original"]["noise"],"minecraft:temperature")
                basin=profile["adjusted"]
                self.assertEqual(basin["type"],"realscale:basin_temperature")
                self.assertGreater(basin["warmth"],0)
                valley=basin["base"]
                self.assertEqual(valley["type"],"realscale:valley_temperature")
                self.assertGreater(valley["warmth"],0)
                radial=valley["base"]
                self.assertEqual(radial["type"],"realscale:radial_latitude_temperature")
                self.assertTrue(radial["balanced"])
                self.assertTrue(radial["area_balanced"])
                self.assertEqual(radial["pole_distance"],8192)
                self.assertGreater(radial["altitude_cooling"],0)
                cooling=radial["base"]
                self.assertEqual(cooling["type"],"minecraft:add")
                self.assertEqual(cooling["argument2"]["type"],"minecraft:mul")
                self.assertLess(cooling["argument2"]["argument1"],0)
                elevation=cooling["argument2"]["argument2"]
                self.assertEqual(elevation["type"],"minecraft:y_clamped_gradient")
                self.assertEqual(elevation["from_y"],96)
                self.assertEqual(elevation["to_y"],768)
                self.assertEqual(cooling["argument1"],profile["original"])
                self.assertEqual(valley["continents"],router["continents"])
                self.assertEqual(basin["continents"],router["continents"])
                height=f"realscale:{name}/"
                self.assertEqual(valley["height_signal"],
                                 height+("terrain_density" if name=="usgs_big_sur_v2"
                                         else "height_signal"))
                self.assertEqual(valley["height_signal"],basin["height_signal"])
                file=valley["height_signal"].split(":",1)[1]
                self.assertTrue((WORLDGEN/"density_function"/(file+".json")).is_file())
                self.assertEqual(ns["sea_level"],63)
                self.assertEqual(router["final_density"]["type"],
                                 "realscale:grand_overworld_caverns",
                                 "Climate update must NEVER disconnect recently restored caves")
                worldPreset=read(WORLDGEN/"world_preset"/(world+".json"))
                ow=worldPreset["dimensions"]["minecraft:overworld"]["generator"]
                self.assertEqual(ow["settings"],f"realscale:{name}")
                self.assertEqual(worldPreset["dimensions"]["minecraft:the_nether"]
                                 ["generator"]["settings"],"realscale:grand_nether")
                self.assertEqual(worldPreset["dimensions"]["minecraft:the_end"]
                                 ["generator"]["settings"],"realscale:grand_end")

    def test_old_non_tva_climates_are_still_opt_out(self):
        for name in ("realism_max","himalayan","grand_canyon",
                     "continental","ultimate","usgs_big_sur"):
            with self.subTest(name=name):
                temp=read(WORLDGEN/"noise_settings"/(name+".json"))["noise_router"]["temperature"]
                self.assertEqual(temp["type"],"minecraft:shifted_noise",
                                 "Legacy climate changed without a world migration")

    def test_registration_yacl_and_exporter_cannot_lose_area_mode(self):
        registry=(SRC/"RealScaleMod.java").read_text()
        yacl=(SRC/"RealScaleTerrainLabClient.java").read_text()
        code=(SRC/"RadialLatitudeTemperatureDensity.java").read_text()
        exported=(ROOT/"scripts/terrain_lab.py").read_text()
        self.assertIn('"radial_latitude_temperature"',registry)
        self.assertIn('"basin_temperature"',registry)
        self.assertIn('"valley_temperature"',registry)
        self.assertIn('"climate_budget"',registry)
        self.assertIn('"radial_equal_area"',yacl)
        self.assertIn('"radial_equal_area"',exported)
        self.assertIn('optionalFieldOf("area_balanced",false)',code)
        self.assertIn('"area_balanced":True',exported)
        self.assertIn("GeoMath.areaBalancedRadialLatitudeDeg",code)

if __name__=="__main__":
    unittest.main()
