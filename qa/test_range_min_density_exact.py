#!/usr/bin/env python3
"""Frozen RangeChoice + Min vs fused RealScale density expression.

Ensure both the actual USGS/Measured preset AND climate preset use exactly
the old input, cave and interval, without deleting a single cave or structure.
This tests numeric semantics (including signed zero/NaN) but not performance.
"""
import json
import math
import random
import struct
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
DIR=ROOT/"neoforge/src/main/resources/data/realscale/worldgen/noise_settings"
SOURCES=ROOT/"neoforge/src/main/java/org/realscale/neoforge"

# Frozen source graph, from the Measured preset before the optimization.
ORIGINAL={
    "type":"minecraft:range_choice",
    "input":"realscale:usgs_big_sur_v2/terrain_density",
    "min_inclusive":0.25,
    "max_exclusive":4,
    "when_in_range":{
        "type":"minecraft:min",
        "argument1":"realscale:usgs_big_sur_v2/terrain_density",
        "argument2":"realscale:usgs_big_sur_v2/cave_density",
    },
    "when_out_of_range":"realscale:usgs_big_sur_v2/terrain_density",
}

def bits(value):
    return struct.pack("!d",value)

def vanilla_min(a,b):
    # Java Math.min retains the sign of negative zero, unlike Python min().
    if math.isnan(a) or math.isnan(b):
        return float("nan")
    if a==0.0 and b==0.0:
        return -0.0 if math.copysign(1.0,a)<0 or math.copysign(1.0,b)<0 else 0.0
    return a if a<=b else b

def frozen_original(value,cave):
    if 0.25<=value<4:
        return vanilla_min(value,cave)
    return value

def fused(value,cave):
    if value>=0.25 and value<4:
        return vanilla_min(value,cave)
    return value

class RangeMinExactTests(unittest.TestCase):
    def test_actual_world_preset_is_wired_to_fused_codec(self):
        for filename in ("usgs_big_sur_v2.json",
                         "tva_pr25_measured_climate.json"):
            data=json.loads((DIR/filename).read_text(encoding="utf-8"))
            node=data["noise_router"]["final_density"]["base"]
            self.assertEqual(node,{
                "type":"realscale:range_min_exact",
                "base":ORIGINAL["input"],
                "cave":ORIGINAL["when_in_range"]["argument2"],
                "min_inclusive":ORIGINAL["min_inclusive"],
                "max_exclusive":ORIGINAL["max_exclusive"],
            })
            self.assertEqual(data["noise"]["height"],2096)
            self.assertEqual(data["noise"]["min_y"],-64)
            self.assertTrue(data["aquifers_enabled"])
            self.assertEqual(data["noise_router"]["final_density"]["type"],
                             "realscale:grand_overworld_caverns")

    def test_registration_and_no_redundant_base_compute(self):
        registry=(SOURCES/"RealScaleMod.java").read_text(encoding="utf-8")
        java=(SOURCES/"RangeMinDensity.java").read_text(encoding="utf-8")
        self.assertIn('"range_min_exact"), RangeMinDensity.MAP_CODEC',registry)
        compute=java.split("public double compute(",1)[1].split(
            "@Override",1)[0]
        self.assertEqual(compute.count("base.compute(ctx)"),1)
        self.assertEqual(compute.count("cave.compute(ctx)"),1)
        self.assertIn("first >= minInclusive && first < maxExclusive",compute)
        self.assertIn("Math.min(first, cave.compute(ctx))",compute)

    def test_raw_value_parity_over_wide_noise_distribution(self):
        values=[
            float("-inf"),-100,-4,-1,-0.0,0.0,0.249999999999,
            0.25,0.5,1,3.999999999,4,4.000000001,100,float("inf"),
            float("nan")
        ]
        rng=random.Random(2032096)
        values += [rng.uniform(-80,80) for _ in range(1200)]
        for height in values:
            for cave in values[:25]:
                a=frozen_original(height,cave)
                b=fused(height,cave)
                if math.isnan(a):
                    self.assertTrue(math.isnan(b))
                else:
                    self.assertEqual(bits(a),bits(b))

    def test_other_biomes_preserved(self):
        for name in ("tva_alpine_smoothed.json","tva_canyon_smoothed.json",
                     "tva_natural_smoothed.json"):
            node=json.loads((DIR/name).read_text())[
                "noise_router"]["final_density"]
            self.assertEqual(node["type"],"realscale:grand_overworld_caverns")
            self.assertNotEqual(node["base"] if isinstance(node["base"],str)
                                else node["base"].get("type",""),
                                "realscale:range_min_exact")

if __name__=="__main__":
    unittest.main()
