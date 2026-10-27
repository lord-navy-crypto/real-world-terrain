"""Stable release world-preset menu regression check.

Execute with: python3 -m unittest qa.test_stable_natural_usgs_menu -v
"""
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "neoforge/src/main/resources/data"
TAG_ROOT = RESOURCES / "minecraft/tags/worldgen/world_preset"
PRESETS = RESOURCES / "realscale/worldgen/world_preset"
NOISE = RESOURCES / "realscale/worldgen/noise_settings"
STABLE = ["realscale:natural_three_realms", "realscale:usgs_big_sur"]


class StableNaturalUsgsMenuTest(unittest.TestCase):
    def test_both_world_creation_menus_show_only_stable_presets(self):
        for menu in ("normal", "extended"):
            with self.subTest(menu=menu):
                tag = json.loads((TAG_ROOT / f"{menu}.json").read_text())
                self.assertEqual(tag["values"], STABLE)
                self.assertFalse(tag.get("replace", False))

    def test_both_stable_presets_have_valid_worldgen_targets(self):
        for name in ("natural_three_realms", "usgs_big_sur"):
            with self.subTest(preset=name):
                preset = json.loads((PRESETS / f"{name}.json").read_text())
                dimensions = preset["dimensions"]
                self.assertEqual(set(dimensions), {
                    "minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"
                })
                overworld_settings = dimensions["minecraft:overworld"]["generator"]["settings"]
                self.assertTrue(overworld_settings.startswith("realscale:"))
                noise_name = overworld_settings.split(":", 1)[1]
                self.assertTrue((NOISE / f"{noise_name}.json").is_file())

    def test_experimental_fitted_presets_are_preserved_but_not_exposed(self):
        for name in ("alpine", "canyon", "glacial", "volcanic", "karst", "dune"):
            with self.subTest(name=name):
                preset = f"{name}_three_realms"
                self.assertTrue((PRESETS / f"{preset}.json").is_file())
                self.assertNotIn(f"realscale:{preset}", STABLE)

    def test_legacy_usgs_v2_is_not_deleted(self):
        self.assertTrue((PRESETS / "usgs_big_sur_v2.json").is_file())


if __name__ == "__main__":
    unittest.main()
