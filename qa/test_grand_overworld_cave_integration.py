#!/usr/bin/env python3
"""Guard the actual final-density wiring for RealScale giant Overworld caves."""
import json
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/"neoforge/src/main/java/org/realscale/neoforge"
DATA=ROOT/"neoforge/src/main/resources/data/realscale/worldgen"

CAVE_WORLDS=(
    "tva_natural_smoothed","tva_alpine_smoothed",
    "tva_canyon_smoothed","usgs_big_sur_v2"
)

def read(path):
    return json.loads(path.read_text(encoding="utf-8"))


class OverworldCaveContracts(unittest.TestCase):
    def test_four_public_realms_actually_carve_caves_in_final_density(self):
        for preset in CAVE_WORLDS:
            with self.subTest(preset=preset):
                noise=read(DATA/"noise_settings"/f"{preset}.json")
                node=noise["noise_router"]["final_density"]
                self.assertIsInstance(node,dict)
                self.assertEqual(node["type"],"realscale:grand_overworld_caverns")
                self.assertEqual(node["small_caves"],"realscale:realism_max/cave_core")
                self.assertEqual(node["warp"]["type"],"minecraft:noise")
                self.assertEqual(noise["sea_level"],63)
                if preset!="usgs_big_sur_v2":
                    self.assertEqual(node["base"],f"realscale:{preset}/terrain_density")
                    self.assertEqual(noise["noise_router"]["depth"],
                                     f"realscale:{preset}/terrain_density")
                else:
                    self.assertEqual(node["base"]["type"],"minecraft:range_choice")
                    self.assertEqual(node["base"]["input"],
                                     "realscale:usgs_big_sur_v2/terrain_density")
                    self.assertEqual(node["base"]["when_in_range"]["type"],"minecraft:min")

    def test_old_legacy_worldgen_remains_unmodified(self):
        for name in ("realism_max","himalayan","grand_canyon",
                     "georeal_ultimate","ultimate","usgs_big_sur"):
            final=read(DATA/"noise_settings"/f"{name}.json")["noise_router"]["final_density"]
            self.assertNotEqual(final.get("type") if isinstance(final,dict) else final,
                                "realscale:grand_overworld_caverns")

    def test_mod_registers_codec_and_roof_safety(self):
        main=(JAVA/"RealScaleMod.java").read_text(encoding="utf-8")
        codec=(JAVA/"GrandOverworldCaverns.java").read_text(encoding="utf-8")
        layout=(JAVA/"GrandOverworldCaveNetwork.java").read_text(encoding="utf-8")
        self.assertIn('"grand_overworld_caverns"',main)
        self.assertIn("GrandOverworldCaverns.MAP_CODEC",main)
        self.assertIn("original<=0.24",codec)
        self.assertIn("MIN_Y = -40",layout)
        self.assertIn("MAX_Y = 1920",layout)
        self.assertIn("CELL = 512",layout)
        self.assertIn("LAYERS = 4",layout)
        self.assertIn("new Passage(47+Math.floorMod",layout)
        self.assertIn("axis==0?3:0",layout)
        self.assertIn("ThreadLocal.withInitial",layout)
        self.assertIn("Math.floorDiv",layout)
        self.assertIn("return original;",codec)

    def test_canonical_world_presets_keep_all_three_realms(self):
        for name in ("tva_sacred_timeline_three_realms","tva_alpine_three_realms",
                     "tva_canyon_three_realms","tva_measured_three_realms"):
            data=read(DATA/"world_preset"/(name+".json"))
            self.assertEqual(set(data["dimensions"]),{
                "minecraft:overworld","minecraft:the_nether","minecraft:the_end"
            })
            self.assertEqual(data["dimensions"]["minecraft:the_nether"]["generator"]["settings"],
                             "realscale:grand_nether")
            self.assertEqual(data["dimensions"]["minecraft:the_end"]["generator"]["settings"],
                             "realscale:grand_end")


if __name__=="__main__":
    unittest.main()
