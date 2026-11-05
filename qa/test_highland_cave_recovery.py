#!/usr/bin/env python3
"""Safety/registration gates for mountain-height cave decoration recovery."""
import json
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/"neoforge/src/main/resources/data/realscale"
JAVA=ROOT/"neoforge/src/main/java/org/realscale/neoforge"


class MountainCavesTest(unittest.TestCase):
    def test_legacy_cave_placements_remain_in_place(self):
        for name in ("cave_moss_nooks","cave_stalagmites"):
            old=json.loads((BASE/f"worldgen/placed_feature/{name}.json").read_text())
            self.assertEqual(old["feature"],f"realscale:{name}")
            old_height=next(p["height"] for p in old["placement"]
                        if p["type"]=="minecraft:height_range")
            self.assertEqual(old_height["max_inclusive"]["absolute"],32)

    def test_new_highland_layers_use_existing_vanilla_block_features(self):
        for kind,parent in (
            ("cave_moss_highland","cave_moss_nooks"),
            ("cave_stalagmites_highland","cave_stalagmites")
        ):
            for layer,bounds in (("middle",(64,704)),("upper",(704,1776))):
                with self.subTest(kind=kind,layer=layer):
                    name=f"{kind}_{layer}"
                    placement=json.loads((BASE/f"worldgen/placed_feature/{name}.json").read_text())
                    modifier=json.loads((BASE/f"neoforge/biome_modifier/{name}.json").read_text())
                    self.assertEqual(placement["feature"],f"realscale:{parent}")
                    self.assertEqual(placement["placement"][-1]["type"],"minecraft:biome")
                    height=next(p["height"] for p in placement["placement"]
                                if p["type"]=="minecraft:height_range")
                    self.assertEqual(
                        (height["min_inclusive"]["absolute"],height["max_inclusive"]["absolute"]),
                        bounds)
                    self.assertEqual(modifier["type"],"neoforge:add_features")
                    self.assertEqual(modifier["features"],f"realscale:{name}")
                    self.assertEqual(modifier["step"],"underground_decoration")
                    self.assertIn("minecraft:jagged_peaks",modifier["biomes"])
                    self.assertIn("minecraft:plains",modifier["biomes"])
                    self.assertIn("minecraft:dripstone_caves",modifier["biomes"])
                    self.assertFalse(any("ocean" in x or "river" in x
                                         for x in modifier["biomes"]))

    def test_world_guard_and_underground_roof_protection(self):
        java=(JAVA/"HabitatPatchFeature.java").read_text()
        self.assertIn("TVAWorldGuard.hasRealScaleGenerator(context)",java)
        self.assertIn("Heightmap.Types.WORLD_SURFACE_WG",java)
        self.assertIn("surface - MIN_ROOF_DEPTH",java)
        self.assertIn("GrandOverworldCaveNetwork.MAX_Y",java)
        self.assertIn("GrandOverworldCaveNetwork.MIN_Y",java)
        self.assertIn("HIGHLAND_SCAN_RADIUS_Y",java)
        self.assertIn("Blocks.CALCITE",java)
        self.assertIn("plant.canSurvive(w, target)",java)
        self.assertIn("dryCaveFloor",java)
        self.assertNotIn("Math.min(45, w.getMaxBuildHeight()",java)


if __name__=="__main__":
    unittest.main()
