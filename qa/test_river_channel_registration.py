#!/usr/bin/env python3
"""Worldgen river channel registration, sea-level and save safety contracts."""
import json
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]/"neoforge/src/main"
RES=ROOT/"resources"
SRC=ROOT/"java/org/realscale/neoforge"
ALL=("realism_max","georeal_ultimate","himalayan","grand_canyon",
     "usgs_big_sur","continental","realistic_mountains","ultimate")
class RiverRegression(unittest.TestCase):
    def test_sea_level_fixed_to_63_and_water_aquifers(self):
        for p in ALL:
            with self.subTest(preset=p):
                j=json.loads((RES/"data/realscale/worldgen/noise_settings"/(p+".json")).read_text())
                self.assertEqual(j["sea_level"],63)
                self.assertEqual(j["default_fluid"]["Name"],"minecraft:water")
                self.assertTrue(j["aquifers_enabled"])

    def test_safe_worldgen_feature_registration(self):
        f=json.loads((RES/"data/realscale/worldgen/configured_feature/river_channel.json").read_text())
        p=json.loads((RES/"data/realscale/worldgen/placed_feature/river_channel.json").read_text())
        b=json.loads((RES/"data/realscale/neoforge/biome_modifier/river_channel.json").read_text())
        self.assertEqual(f["type"],"realscale:river_channel")
        self.assertEqual(p["feature"],"realscale:river_channel")
        self.assertEqual(b["type"],"neoforge:add_features")
        self.assertEqual(set(b["biomes"]),{"minecraft:river","minecraft:frozen_river"})
        self.assertEqual(b["step"],"local_modifications")
        self.assertNotIn("minecraft:lake",json.dumps((f,p,b)))
        java=(SRC/"RiverChannelFeature.java").read_text()
        self.assertIn('id.getPath().equals("usgs_big_sur")',java)
        self.assertIn('id.getNamespace().equals("realscale")',java)
        self.assertIn('new ChunkPos(context.origin())',java)
        self.assertIn('noise.getSeaLevel() + 6',java)
        self.assertIn('top.below(3), Blocks.GRAVEL',java)
        self.assertIn('top.below(2), Blocks.WATER',java)
        self.assertIn('top.below(4)',java)
        self.assertIn('enclosedByBanks(level,top)',java)
        self.assertIn('Direction.Plane.HORIZONTAL',java)
        self.assertIn('neighbor.below(2)',java)
        self.assertIn('nextTop != top.getY()',java)
if __name__=="__main__":unittest.main()
