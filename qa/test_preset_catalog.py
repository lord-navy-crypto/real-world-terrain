#!/usr/bin/env python3
"""Five main modes in UI, eight retained generator IDs."""
import json
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]/"neoforge/src/main/resources"
TAG=ROOT/"data/minecraft/tags/worldgen/world_preset"
DATA=ROOT/"data/realscale/worldgen"
LANG=ROOT/"assets/minecraft/lang"
PRIMARY=("realism_max","georeal_ultimate","himalayan","grand_canyon","usgs_big_sur")
HIDDEN=("continental","realistic_mountains","ultimate")
ALL=PRIMARY+HIDDEN

def load(path):
    return json.loads(path.read_text(encoding="utf-8"))

class PresetCatalogChecks(unittest.TestCase):
    def test_only_five_main_choices(self):
        j=load(TAG/"normal.json")
        self.assertIs(j["replace"],False)
        self.assertEqual(j["values"],["realscale:"+s for s in PRIMARY])
        self.assertEqual(len(j["values"]),len(set(j["values"])))

    def test_extended_preserves_all_eight(self):
        j=load(TAG/"extended.json")
        self.assertIs(j["replace"],False)
        self.assertEqual(j["values"],["realscale:"+s for s in ALL])
        self.assertEqual(len(j["values"]),len(set(j["values"])))

    def test_legacy_world_ids_keep_original_noise_settings(self):
        for mode in ALL:
            with self.subTest(mode=mode):
                preset=load(DATA/"world_preset"/(mode+".json"))
                dim=preset["dimensions"]["minecraft:overworld"]
                self.assertEqual(dim["generator"]["settings"],"realscale:"+mode)
                self.assertTrue((DATA/"noise_settings"/(mode+".json")).is_file())
                self.assertTrue((DATA/"density_function"/mode/"terrain_density.json").is_file())
                self.assertIn("minecraft:the_nether",preset["dimensions"])
                self.assertIn("minecraft:the_end",preset["dimensions"])

    def test_labels_cover_every_preset(self):
        for language in ("en_us","zh_cn"):
            labels=load(LANG/(language+".json"))
            for mode in ALL:
                self.assertTrue(labels.get("generator.realscale."+mode))

if __name__=="__main__":
    unittest.main()
