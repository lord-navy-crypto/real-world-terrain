"""Stable release world-preset menu and fast-path wiring regression checks.

Execute with: python3 -m unittest qa.test_stable_natural_usgs_menu -v
These are static guards; they do not replace real NeoForge worldgen timing tests.
"""
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "neoforge/src/main/resources/data"
TAG_ROOT = RESOURCES / "minecraft/tags/worldgen/world_preset"
PRESETS = RESOURCES / "realscale/worldgen/world_preset"
NOISE = RESOURCES / "realscale/worldgen/noise_settings"
DENSITY = RESOURCES / "realscale/worldgen/density_function"
STABLE = ["realscale:natural_three_realms", "realscale:usgs_big_sur"]
ENGINE = {
    "natural_three_realms": "realscale:tva_natural_smoothed",
    "usgs_big_sur": "realscale:usgs_big_sur",
}


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


class StableNaturalUsgsMenuTest(unittest.TestCase):
    def test_both_world_creation_menus_show_only_stable_presets(self):
        for menu in ("normal", "extended"):
            with self.subTest(menu=menu):
                tag = read_json(TAG_ROOT / f"{menu}.json")
                self.assertEqual(tag["values"], STABLE)
                # Do not replace the vanilla/Minecraft tag completely.
                self.assertFalse(tag.get("replace", False))

    def test_both_stable_presets_have_valid_worldgen_targets(self):
        for name, expected_settings in ENGINE.items():
            with self.subTest(preset=name):
                preset = read_json(PRESETS / f"{name}.json")
                dimensions = preset["dimensions"]
                self.assertEqual(
                    set(dimensions),
                    {"minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"},
                )
                overworld = dimensions["minecraft:overworld"]["generator"]
                self.assertEqual(overworld["type"], "minecraft:noise")
                self.assertEqual(overworld["settings"], expected_settings)
                noise_name = expected_settings.split(":", 1)[1]
                noise_settings = read_json(NOISE / f"{noise_name}.json")
                self.assertEqual(noise_settings["noise"]["height"], 2096)
                self.assertEqual(noise_settings["noise"]["min_y"], -64)
                # The default release modes must not accidentally be rewired
                # to the expensive fitted terrain archetype kernel.
                self.assertNotIn("realscale:terrain_archetype", json.dumps(noise_settings))

    def test_usgs_uses_original_dem_kernel_not_experimental_v2(self):
        # Keep the original, bounded ~480m DEM pilot as the release entry.
        # Switching to seamless v2 requires separate perf + saved-world proof.
        usgs = read_json(PRESETS / "usgs_big_sur.json")
        self.assertEqual(
            usgs["dimensions"]["minecraft:overworld"]["generator"]["settings"],
            "realscale:usgs_big_sur",
        )
        legacy_dem = read_json(DENSITY / "usgs_big_sur/terrain_density.json")
        self.assertEqual(legacy_dem["type"], "realscale:dem_density")
        self.assertEqual(legacy_dem["base"], "realscale:realism_max/terrain_density")
        self.assertEqual(legacy_dem["blend_meters"], 96)

        v2 = read_json(PRESETS / "usgs_big_sur_v2.json")
        self.assertEqual(
            v2["dimensions"]["minecraft:overworld"]["generator"]["settings"],
            "realscale:usgs_big_sur_v2",
        )
        v2_dem = read_json(DENSITY / "usgs_big_sur_v2/terrain_density.json")
        self.assertEqual(v2_dem["type"], "realscale:dem_density_seamless")

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
