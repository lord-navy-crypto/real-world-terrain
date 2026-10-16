#!/usr/bin/env python3
"""Assert world presets actually use highland basin/plateau relief + basin climate."""
import json
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]/"neoforge/src/main"
DATA=ROOT/"resources/data/realscale/worldgen"
JAVA=ROOT/"java/org/realscale/neoforge"

PRESETS={
    "tva_natural_smoothed":("realism_max","realism_max/continental_field"),
    "tva_alpine_smoothed":("himalayan","himalayan/continents"),
    "tva_canyon_smoothed":("grand_canyon","grand_canyon/continents"),
}
def read(path):
    return json.loads(path.read_text(encoding="utf-8"))


class MountainBasinPlateauTest(unittest.TestCase):
    def test_public_terrain_graphs_keep_original_height_and_add_relief(self):
        for preset,(family,continental) in PRESETS.items():
            with self.subTest(preset=preset):
                root=DATA/"density_function"/preset
                original=read(root/"original_height_signal.json")
                self.assertEqual(original,{
                    "type":"minecraft:interpolated",
                    "argument":f"realscale:{family}/height_signal"})
                shape=read(root/"height_signal.json")
                self.assertEqual(shape["type"],"realscale:mountain_relief")
                self.assertEqual(shape["base"],f"realscale:{preset}/original_height_signal")
                self.assertEqual(shape["continents"],f"realscale:{continental}")
                self.assertEqual(shape["selector"],{
                    "type":"minecraft:noise",
                    "noise":"minecraft:erosion",
                    "xz_scale":0.006,"y_scale":0
                })
                self.assertGreater(shape["basin_depth"],0)
                self.assertGreater(shape["plateau_rise"],0)
                terrain=read(root/"terrain_density.json")
                self.assertEqual(terrain["argument1"],f"realscale:{preset}/height_signal")
                noise=read(DATA/"noise_settings"/(preset+".json"))
                self.assertEqual(noise["noise_router"]["depth"],
                                 f"realscale:{preset}/terrain_density")
                self.assertEqual(noise["noise_router"]["final_density"]["type"],
                                 "realscale:grand_overworld_caverns")
                self.assertEqual(noise["noise_router"]["final_density"]["base"],
                                 f"realscale:{preset}/terrain_density")
                self.assertEqual(noise["sea_level"],63)

    def test_basins_reuse_same_seeded_layout_as_relief_and_climate(self):
        for preset in PRESETS:
            noise=read(DATA/"noise_settings"/(preset+".json"))
            temperature=noise["noise_router"]["temperature"]
            self.assertEqual(temperature["type"],"realscale:climate_budget")
            adjusted=temperature["adjusted"]
            self.assertEqual(adjusted["type"],"realscale:mountain_basin_temperature")
            self.assertEqual(adjusted["height_signal"],
                             f"realscale:{preset}/original_height_signal")
            base=adjusted["base"]
            self.assertEqual(base["type"],"realscale:radial_latitude_temperature")
            self.assertTrue(base["area_balanced"])
            self.assertTrue(base["balanced"])
            self.assertGreater(base["altitude_cooling"],0)
            landform=read(DATA/"density_function"/preset/"height_signal.json")
            self.assertEqual(adjusted["selector"],landform["selector"])
            self.assertEqual(adjusted["continents"],landform["continents"])
            self.assertGreater(adjusted["warmth"],0)
            self.assertLessEqual(adjusted["warmth"],0.25)

    def test_measured_usgs_preserved_and_old_worlds_unchanged(self):
        usgs=read(DATA/"density_function/usgs_big_sur_v2/terrain_density.json")
        self.assertEqual(usgs["type"],"realscale:dem_density_seamless")
        self.assertEqual(usgs["blend_meters"],2048)
        usgs_noise=read(DATA/"noise_settings/usgs_big_sur_v2.json")
        self.assertEqual(usgs_noise["noise_router"]["temperature"]["adjusted"]["type"],
                         "realscale:radial_latitude_temperature")
        self.assertEqual(usgs_noise["noise_router"]["final_density"]["type"],
                         "realscale:grand_overworld_caverns")
        for old in ("realism_max","himalayan","grand_canyon"):
            self.assertNotEqual(
                read(DATA/"density_function"/old/"height_signal.json").get("type"),
                "realscale:mountain_relief")

    def test_mod_codec_and_bounded_site_cache(self):
        main=(JAVA/"RealScaleMod.java").read_text()
        terrain=(JAVA/"MountainReliefDensity.java").read_text()
        temp=(JAVA/"MountainBasinTemperatureDensity.java").read_text()
        pure=(JAVA/"MountainReliefMath.java").read_text()
        self.assertIn('"mountain_relief"',main)
        self.assertIn('"mountain_basin_temperature"',main)
        self.assertIn("ThreadLocal.withInitial",terrain)
        self.assertIn("ThreadLocal.withInitial",temp)
        self.assertIn("Math.floorDiv",terrain)
        # The cache is keyed by horizontal region only; a Y-dependent anchor
        # would make site selection depend on whichever vertical sample runs first.
        self.assertIn("new DensityFunction.SinglePointContext(centerX, 0, centerZ)",terrain)
        self.assertNotIn("new DensityFunction.SinglePointContext(centerX, ctx.blockY(), centerZ)",terrain)
        self.assertIn("Math.floorDiv",temp)
        self.assertIn("int kind(long h, double seedResolvedSignal)",pure)
        self.assertIn("inlandUplandGate",pure)
        self.assertIn("return original;",terrain)
        self.assertIn("return original;",temp)


if __name__=="__main__":
    unittest.main()
