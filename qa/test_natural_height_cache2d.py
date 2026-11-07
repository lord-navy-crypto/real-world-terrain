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
        # Outer memo must see the identical Minecraft native 2D cache.
        self.assertEqual(root["type"],"realscale:measured_horizontal_memo")
        native=root["input"]
        self.assertEqual(native["type"],"minecraft:cache_2d")
        relief=native["argument"]
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

    def test_smoothed_and_fitted_profiles_memoize_only_the_horizontal_signal(self):
        profiles={
            "tva_alpine_smoothed":("realscale:mountain_relief",
                                   "himalayan/height_signal","himalayan/continents",True),
            "tva_canyon_smoothed":("realscale:mountain_relief",
                                   "grand_canyon/height_signal","grand_canyon/continents",True),
            "tva_alpine_fitted":("realscale:terrain_archetype",
                                 "himalayan/height_signal","himalayan/continents",False),
            "tva_canyon_fitted":("realscale:terrain_archetype",
                                 "grand_canyon/height_signal","grand_canyon/continents",False),
            "tva_glacial_fitted":("realscale:terrain_archetype",
                                  "himalayan/height_signal","himalayan/continents",False),
            "tva_volcanic_fitted":("realscale:terrain_archetype",
                                   "realism_max/height_signal","realism_max/continental_field",False),
            "tva_karst_fitted":("realscale:terrain_archetype",
                                "grand_canyon/height_signal","grand_canyon/continents",False),
            "tva_dune_fitted":("realscale:terrain_archetype",
                               "realism_max/height_signal","realism_max/continental_field",False),
        }
        for profile,(kernel,base_graph,continents,memo_at_signal) in profiles.items():
            with self.subTest(profile=profile):
                root=load(profile+"/height_signal")
                if memo_at_signal:
                    self.assertEqual(root["type"],"realscale:measured_horizontal_memo")
                    node=root["input"]
                else:
                    self.assertEqual(root["type"],kernel)
                    node=root
                self.assertEqual(node["type"],kernel)
                self.assertEqual(node["continents"],"realscale:"+continents)
                self.assertEqual(load(continents)["type"],"minecraft:flat_cache")
                self.assertEqual(node["selector"]["type"],"minecraft:noise")
                self.assertEqual(node["selector"]["y_scale"],0)
                original=load(profile+"/original_height_signal")
                self.assertEqual(original["type"],"minecraft:interpolated")
                self.assertEqual(original["argument"],"realscale:"+base_graph)
                self.assertEqual(load(base_graph)["type"],"minecraft:flat_cache")

                # Fitted profiles already memoize at terrain_density; do not
                # add a second cache around their source height signal.
                terrain=load(profile+"/terrain_density")
                self.assertEqual(terrain["argument2"]["type"],
                                 "minecraft:y_clamped_gradient")
                if memo_at_signal:
                    self.assertEqual(terrain["argument1"],
                                     "realscale:"+profile+"/height_signal")
                else:
                    self.assertEqual(terrain["argument1"],{
                        "type":"realscale:measured_horizontal_memo",
                        "input":"realscale:"+profile+"/height_signal"
                    })

        # Both custom kernels are horizontal-only. Their Y gradient remains
        # outside the memo in terrain_density.json.
        for source in ("MountainReliefDensity.java","TerrainArchetypeDensity.java"):
            text=(ROOT/"neoforge/src/main/java/org/realscale/neoforge"/source).read_text(
                encoding="utf-8")
            self.assertNotIn("blockY()",text,source)
            self.assertIn("blockX()",text,source)
            self.assertIn("blockZ()",text,source)

    def test_other_families_do_not_change_to_2d_cache(self):
        for name in ("tva_alpine_smoothed/height_signal",
                     "tva_canyon_smoothed/height_signal"):
            root=load(name)
            self.assertNotEqual(root.get("type"),"minecraft:cache_2d")

if __name__=="__main__":
    unittest.main()
