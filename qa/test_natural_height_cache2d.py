#!/usr/bin/env python3
"""Guard the optional Natural/Sacred horizontal cache experiment.

The inner mountain-relief node is frozen exactly; it is only wrapped in
Minecraft's native cache_2d. Parent signals must be 2D. Actual block parity
and throughput STILL require saved-world A/B (not proved by this static test).
"""
import json
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
D=ROOT/"neoforge/src/main/resources/data/realscale/worldgen/density_function"

def load(name):
    return json.loads((D/(name+".json")).read_text(encoding="utf-8"))

class NaturalCache2DTests(unittest.TestCase):
    def test_root_is_only_wrapped_without_cave_or_relief_changes(self):
        root=load("tva_natural_smoothed/height_signal")
        self.assertEqual(root["type"],"minecraft:cache_2d")
        relief=root["argument"]
        self.assertEqual(relief,{
            "type":"realscale:mountain_relief",
            "base":"realscale:tva_natural_smoothed/original_height_signal",
            "continents":"realscale:realism_max/continental_field",
            "selector":{
                "type":"minecraft:noise",
                "noise":"minecraft:erosion",
                "xz_scale":0.006,
                "y_scale":0
            },
            "basin_depth":0.9,
            "plateau_rise":0.65
        })

    def test_inputs_are_built_from_true_2d_cached_noise(self):
        raw=load("tva_natural_smoothed/original_height_signal")
        self.assertEqual(raw,{
            "type":"minecraft:interpolated",
            "argument":"realscale:realism_max/height_signal",
        })
        for name in ("realism_max/height_signal",
                     "realism_max/continental_field"):
            self.assertEqual(load(name)["type"],"minecraft:flat_cache")
        node=load("realism_max/height_signal")
        def scan(node):
            if isinstance(node,list):
                for item in node:
                    yield from scan(item)
            if isinstance(node,dict):
                yield node
                for item in node.values():
                    yield from scan(item)
        self.assertFalse(any(x.get("type") in (
            "minecraft:y_clamped_gradient","minecraft:weird_scaled_sampler",
        ) for x in scan(node)))
        for datum in scan(node):
            if "y_scale" in datum:
                self.assertEqual(datum["y_scale"],0)

    def test_other_families_do_not_change_to_2d_cache(self):
        for name in ("tva_alpine_smoothed/height_signal",
                     "tva_canyon_smoothed/height_signal"):
            root=load(name)
            self.assertNotEqual(root.get("type"),"minecraft:cache_2d")

if __name__=="__main__":
    unittest.main()
