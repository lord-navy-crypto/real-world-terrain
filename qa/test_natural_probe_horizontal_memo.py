#!/usr/bin/env python3
"""Conservative structural gate for the experimental Natural smoothing memo.

The original interpolated function and all noise are frozen. Only its
Y-invariant result can be memoized by X/Z; final density still includes the
unchanged Y gradient. Real saved-world/bit parity needs independent QA.
"""
import json
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]/"neoforge/src/main/resources/data/realscale/worldgen"

def read(name):
    return json.loads((ROOT/name).read_text(encoding="utf-8"))

def visit(node):
    if isinstance(node,dict):
        yield node
        for value in node.values():
            yield from visit(value)
    elif isinstance(node,list):
        for value in node:
            yield from visit(value)

class NaturalProbeMemoTests(unittest.TestCase):
    def test_memo_wraps_identical_interpolation_once(self):
        signal=read("density_function/tva_natural_smoothing_probe/height_signal.json")
        self.assertEqual(signal,{
            "type":"realscale:measured_horizontal_memo",
            "input":{
                "type":"minecraft:interpolated",
                "argument":"realscale:realism_max/height_signal"
            }
        })

    def test_source_is_proven_y_invariant(self):
        signal=read("density_function/realism_max/height_signal.json")
        self.assertEqual(signal["type"],"minecraft:flat_cache")
        for node in visit(signal):
            self.assertNotIn(node.get("type"),(
                "minecraft:y_clamped_gradient",
                "minecraft:weird_scaled_sampler",
            ))
            if "y_scale" in node:
                self.assertEqual(node["y_scale"],0)

    def test_vertical_gradient_remains_outside_memo(self):
        terrain=read("density_function/tva_natural_smoothing_probe/terrain_density.json")
        self.assertEqual(terrain,{
            "type":"minecraft:add",
            "argument1":"realscale:tva_natural_smoothing_probe/height_signal",
            "argument2":{
                "type":"minecraft:y_clamped_gradient",
                "from_y":-64,
                "to_y":2032,
                "from_value":0.9921875,
                "to_value":-15.3828125,
            }
        })
        settings=read("noise_settings/tva_natural_smoothing_probe.json")
        self.assertEqual(settings["noise"]["height"],2096)
        self.assertEqual(settings["noise_router"]["final_density"],
                         "realscale:tva_natural_smoothing_probe/terrain_density")

if __name__=="__main__":
    unittest.main()
