#!/usr/bin/env python3
"""Structural proof of the strictly X/Z-only Natural Automate memo.

The exact existing cache_2d + six-family morphology is wrapped unchanged.
Final density still uses the full 2096-block Y gradient and cave network.
This gate is not a substitute for real fresh-world FULL chunk and Anvil parity.
"""
import json
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
WORLD=ROOT/"neoforge/src/main/resources/data/realscale/worldgen"
DENSITY=WORLD/"density_function"
JAVA=ROOT/"neoforge/src/main/java/org/realscale/neoforge"


def read_density(name):
    return json.loads((DENSITY/(name+".json")).read_text(encoding="utf-8"))


def walk(root):
    if isinstance(root,dict):
        yield root
        for value in root.values():
            yield from walk(value)
    elif isinstance(root,list):
        for value in root:
            yield from walk(value)


class NaturalAutomateExactMemo(unittest.TestCase):
    def test_exact_previous_graph_is_wrapped_once(self):
        signal=read_density("natural_automate/height_signal")
        self.assertEqual(signal["type"],"realscale:measured_horizontal_memo")
        native=signal["input"]
        self.assertEqual(native["type"],"minecraft:cache_2d")
        expected={
            "type":"realscale:terrain_archetype",
            "base":"realscale:tva_natural_smoothed/height_signal",
            "continents":"realscale:realism_max/continental_field",
            "selector":{
                "type":"minecraft:noise",
                "noise":"minecraft:erosion","xz_scale":0.006,"y_scale":0
            },
            "family":"natural_automate",
            "macro_gain":0.92,"meso_gain":0.38,"micro_gain":0.07,
            "relief_gain":0.72,"ridge_sharpness":0.36,
            "cliff_index":0.3,"valley_index":0.5,"ridge_index":0.5,
            "anisotropy_gain":1.05,"dune_axis_radians":0.25,
            "dominant_wavelength_m":12000,
            "environment":{
                "region_selector":{
                    "type":"minecraft:noise","noise":"minecraft:offset",
                    "xz_scale":0.00042,"y_scale":0
                },
                "temperature":{
                    "type":"minecraft:shifted_noise","noise":"minecraft:temperature",
                    "shift_x":"minecraft:shift_x","shift_y":0,
                    "shift_z":"minecraft:shift_z",
                    "xz_scale":0.15,"y_scale":0
                },
                "humidity":{
                    "type":"minecraft:shifted_noise","noise":"minecraft:vegetation",
                    "shift_x":"minecraft:shift_x","shift_y":0,
                    "shift_z":"minecraft:shift_z",
                    "xz_scale":0.15,"y_scale":0
                }
            }
        }
        self.assertEqual(native["argument"],expected)
        self.assertEqual(sum(node.get("type")=="realscale:measured_horizontal_memo"
                             for node in walk(signal)),1)
        self.assertEqual(len(tuple(walk(native["argument"]["environment"]))),4)

    def test_signal_is_independent_of_vertical_coordinate(self):
        node=read_density("natural_automate/height_signal")
        for item in walk(node):
            self.assertNotIn(item.get("type"),(
                "minecraft:y_clamped_gradient","minecraft:weird_scaled_sampler"
            ))
            if "y_scale" in item:
                self.assertEqual(item["y_scale"],0)
            if "shift_y" in item:
                self.assertEqual(item["shift_y"],0)
        source=(JAVA/"TerrainArchetypeDensity.java").read_text(encoding="utf-8")
        self.assertNotIn("blockY()",source)
        morph=(JAVA/"TerrainArchetypeMath.java").read_text(encoding="utf-8")
        self.assertNotIn("blockY()",morph)
        self.assertEqual(read_density("tva_natural_smoothed/height_signal")["type"],
                         "realscale:measured_horizontal_memo")
        self.assertEqual(read_density("realism_max/height_signal")["type"],
                         "minecraft:flat_cache")

    def test_height_gradient_and_cave_assembly_remain_unchanged(self):
        terrain=read_density("natural_automate/terrain_density")
        self.assertEqual(terrain,{
            "type":"minecraft:add",
            "argument1":"realscale:natural_automate/height_signal",
            "argument2":{
                "type":"minecraft:y_clamped_gradient",
                "from_y":-64,"to_y":2032,
                "from_value":0.9921875,"to_value":-15.3828125
            }
        })
        settings=json.loads((WORLD/"noise_settings/natural_automate_v2.json")
                            .read_text(encoding="utf-8"))
        self.assertEqual(settings["noise"]["height"],2096)
        self.assertEqual(settings["noise"]["min_y"],-64)
        self.assertTrue(settings["aquifers_enabled"])
        final=settings["noise_router"]["final_density"]
        self.assertEqual(final["type"],"realscale:grand_overworld_caverns")
        self.assertEqual(final["base"],"realscale:natural_automate/terrain_density")
        self.assertTrue(final["gallery_v2"])
        self.assertEqual(final["small_caves"],"realscale:realism_max/cave_core")
        self.assertEqual(settings["default_block"]["Name"],"minecraft:stone")
        self.assertEqual(settings["default_fluid"]["Name"],"minecraft:water")

    def test_memo_implementation_is_bounded_per_worker_per_seed(self):
        memo=(JAVA/"MeasuredHorizontalMemoDensity.java").read_text(encoding="utf-8")
        impl=(JAVA/"MeasuredColumnMemo.java").read_text(encoding="utf-8")
        self.assertIn("ThreadLocal<MeasuredColumnMemo>",memo)
        self.assertIn("input.mapAll(visitor)",memo)
        self.assertIn("if(hit)return memo.value();",memo)
        self.assertIn("double value=input.compute(ctx);",memo)
        self.assertIn("cacheSlots(",impl)
        self.assertIn("if (valid[first] && coordinates[first] == coordinate)",impl)


if __name__=="__main__":
    unittest.main()
