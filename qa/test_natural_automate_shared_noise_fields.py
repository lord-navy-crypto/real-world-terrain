import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
WORLDGEN = ROOT / "neoforge/src/main/resources/data/realscale/worldgen"


def load(relative):
    return json.loads((WORLDGEN / relative).read_text(encoding="utf-8"))


class NaturalAutomateSharedNoiseFieldsTest(unittest.TestCase):
    def test_shared_fields_are_exact_horizontal_cache_2d_nodes(self):
        expected = {
            "density_function/shared/raw_erosion_selector.json": {
                "type": "minecraft:noise",
                "noise": "minecraft:erosion",
                "xz_scale": 0.006,
                "y_scale": 0.0,
            },
            "density_function/shared/raw_temperature_noise.json": {
                "type": "minecraft:shifted_noise",
                "noise": "minecraft:temperature",
                "shift_x": "minecraft:shift_x",
                "shift_y": 0.0,
                "shift_z": "minecraft:shift_z",
                "xz_scale": 0.15,
                "y_scale": 0.0,
            },
            "density_function/shared/raw_vegetation_noise.json": {
                "type": "minecraft:shifted_noise",
                "noise": "minecraft:vegetation",
                "shift_x": "minecraft:shift_x",
                "shift_y": 0.0,
                "shift_z": "minecraft:shift_z",
                "xz_scale": 0.15,
                "y_scale": 0.0,
            },
        }
        for relative, expected_input in expected.items():
            with self.subTest(relative=relative):
                node = load(relative)
                self.assertEqual("minecraft:cache_2d", node["type"])
                self.assertEqual(expected_input, node["argument"])
                self.assertEqual(0.0, node["argument"].get("y_scale", 0.0))
                self.assertEqual(0.0, node["argument"].get("shift_y", 0.0))

    def test_both_natural_automate_presets_reuse_the_same_noise_nodes(self):
        for name in ("natural_automate.json", "natural_automate_v2.json"):
            with self.subTest(preset=name):
                router = load(f"noise_settings/{name}")["noise_router"]
                climate = router["temperature"]
                self.assertEqual(
                    "realscale:shared/raw_temperature_noise",
                    climate["original"],
                )
                self.assertEqual(
                    "realscale:shared/raw_temperature_noise",
                    climate["adjusted"]["base"]["base"]["argument1"],
                )
                self.assertEqual(
                    "realscale:shared/raw_erosion_selector",
                    climate["adjusted"]["selector"],
                )
                self.assertEqual(
                    "realscale:shared/raw_vegetation_noise",
                    router["vegetation"],
                )

    def test_height_signal_and_smoothed_baseline_share_erosion_and_climate(self):
        natural = load("density_function/natural_automate/height_signal.json")
        argument = natural["input"]["argument"]
        self.assertEqual(
            "realscale:shared/raw_erosion_selector",
            argument["selector"],
        )
        self.assertEqual(
            "realscale:shared/raw_temperature_noise",
            argument["environment"]["temperature"],
        )
        self.assertEqual(
            "realscale:shared/raw_vegetation_noise",
            argument["environment"]["humidity"],
        )

        smoothed = load("density_function/tva_natural_smoothed/height_signal.json")
        self.assertEqual(
            "realscale:shared/raw_erosion_selector",
            smoothed["input"]["argument"]["selector"],
        )


if __name__ == "__main__":
    unittest.main()
