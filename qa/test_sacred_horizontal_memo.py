#!/usr/bin/env python3
"""Guard exact Y-independent Sacred relief memo against accidental 3D caching."""
import json
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]/"neoforge/src/main/resources/data/realscale/worldgen"

def read(key):
    return json.loads((ROOT/key).read_text(encoding="utf-8"))

def nodes(value):
    if isinstance(value,dict):
        yield value
        for x in value.values():
            yield from nodes(x)
    elif isinstance(value,list):
        for x in value:yield from nodes(x)

class SacredHorizontalMemoTest(unittest.TestCase):
    def test_only_outer_horizontal_memo_is_new(self):
        graph=read("density_function/tva_natural_smoothed/height_signal.json")
        self.assertEqual(graph, {
            "type":"realscale:measured_horizontal_memo",
            "input":{
                "type":"minecraft:cache_2d",
                "argument":{
                    "type":"realscale:mountain_relief",
                    "base":"realscale:tva_natural_smoothed/original_height_signal",
                    "continents":"realscale:realism_max/continental_field",
                    "selector":{"type":"minecraft:noise","noise":"minecraft:erosion",
                                "xz_scale":0.006,"y_scale":0},
                    "basin_depth":0.9,"plateau_rise":0.65
                }
            }
        })

    def test_wrapped_subgraph_is_horizontal(self):
        interpolated=read("density_function/tva_natural_smoothed/original_height_signal.json")
        self.assertEqual(interpolated, {
            "type":"minecraft:interpolated",
            "argument":"realscale:realism_max/height_signal"
        })
        for field in ("height_signal","continental_field"):
            base=read("density_function/realism_max/"+field+".json")
            self.assertEqual(base["type"],"minecraft:flat_cache")
            for node in nodes(base):
                self.assertNotIn(node.get("type"),(
                    "minecraft:y_clamped_gradient",
                    "minecraft:weird_scaled_sampler"))
                if "y_scale" in node:self.assertEqual(node["y_scale"],0)

    def test_vertical_gradient_and_cave_remain_outside(self):
        terrain=read("density_function/tva_natural_smoothed/terrain_density.json")
        self.assertEqual(terrain["argument1"],"realscale:tva_natural_smoothed/height_signal")
        self.assertEqual(terrain["argument2"]["type"],"minecraft:y_clamped_gradient")
        settings=read("noise_settings/tva_natural_smoothed.json")
        self.assertEqual(settings["noise"]["height"],2096)
        final_scale=settings["noise_router"]["final_density"]
        self.assertEqual(final_scale["type"],"realscale:horizontal_scale")
        self.assertEqual(final_scale["scale"],20)
        final=final_scale["base"]
        self.assertEqual(final["type"],"realscale:grand_overworld_caverns")
        self.assertEqual(final["base"],"realscale:tva_natural_smoothed/terrain_density")
        self.assertEqual(final["small_caves"],"realscale:realism_max/cave_core")

        depth_scale=settings["noise_router"]["depth"]
        self.assertEqual(depth_scale["type"],"realscale:horizontal_scale")
        self.assertEqual(depth_scale["scale"],20)
        self.assertEqual(depth_scale["base"],
                         "realscale:tva_natural_smoothed/terrain_density")

        horizontal_scale=(ROOT.parents[6]/"neoforge/src/main/java/org/realscale/neoforge/HorizontalScaleDensity.java")
        source=horizontal_scale.read_text(encoding="utf-8")
        self.assertIn("delegate.blockX()",source)
        self.assertIn("delegate.blockZ()",source)
        self.assertIn("delegate.blockY()",source)

if __name__=="__main__":unittest.main()
