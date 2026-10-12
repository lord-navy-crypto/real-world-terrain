#!/usr/bin/env python3
"""Four main categories in UI, eight retained generator IDs."""
import json
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]/"neoforge/src/main/resources"
TAG=ROOT/"data/minecraft/tags/worldgen/world_preset"
DATA=ROOT/"data/realscale/worldgen"
LANG=ROOT/"assets/minecraft/lang"
PRIMARY=("realism_max","himalayan","grand_canyon","usgs_big_sur")
HIDDEN=("georeal_ultimate","continental","realistic_mountains","ultimate")
ALL=PRIMARY+HIDDEN

def load(path):
    return json.loads(path.read_text(encoding="utf-8"))

class PresetCatalogChecks(unittest.TestCase):
    def test_only_four_main_choices(self):
        j=load(TAG/"normal.json")
        self.assertIs(j["replace"],False)
        expected=["realscale:"+s for s in PRIMARY]
        self.assertEqual(j["values"][:len(expected)],expected)
        self.assertEqual(j["values"][len(expected)],"realscale:usgs_big_sur_v2")
        self.assertTrue(all(s.startswith("realscale:lab_") for s in j["values"][len(expected)+1:]))
        self.assertEqual(len(j["values"]),len(set(j["values"])))

    def test_extended_preserves_all_eight(self):
        j=load(TAG/"extended.json")
        self.assertIs(j["replace"],False)
        expected=["realscale:"+s for s in ALL]
        self.assertEqual(j["values"][:len(expected)],expected)
        self.assertEqual(j["values"][len(expected)],"realscale:usgs_big_sur_v2")
        self.assertTrue(all(s.startswith("realscale:lab_") for s in j["values"][len(expected)+1:]))
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

    def test_usgs_v2_is_separate_world_preset(self):
        old=load(DATA/"world_preset/usgs_big_sur.json")
        fixed=load(DATA/"world_preset/usgs_big_sur_v2.json")
        original=old["dimensions"]["minecraft:overworld"]["generator"]["settings"]
        new=fixed["dimensions"]["minecraft:overworld"]["generator"]["settings"]
        self.assertEqual(original,"realscale:usgs_big_sur")
        self.assertEqual(new,"realscale:usgs_big_sur_v2")
        self.assertEqual(fixed["dimensions"]["minecraft:the_nether"],old["dimensions"]["minecraft:the_nether"])
        self.assertEqual(fixed["dimensions"]["minecraft:the_end"],old["dimensions"]["minecraft:the_end"])


    def test_labels_cover_every_preset(self):
        for language in ("en_us","zh_cn"):
            labels=load(LANG/(language+".json"))
            for mode in ALL:
                self.assertTrue(labels.get("generator.realscale."+mode))

if __name__=="__main__":
    unittest.main()
