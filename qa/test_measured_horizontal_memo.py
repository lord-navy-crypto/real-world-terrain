#!/usr/bin/env python3
"""Measuring the same 2D signal at many Y levels must never change worldgen geometry."""
import json
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/"neoforge/src/main/resources/data/realscale/worldgen/density_function"
JAVA=ROOT/"neoforge/src/main/java/org/realscale/neoforge"

class MeasuredHorizontalMemoTests(unittest.TestCase):
    def test_only_y_invariant_original_height_signal_is_memoized(self):
        height=json.loads((DATA/"realism_max/height_signal.json").read_text())
        self.assertEqual(height["type"],"minecraft:flat_cache")
        source=json.dumps(height).lower()
        for disallowed in ("y_clamped_gradient","shifted_y","old_blended_noise",
                           "weird_scaled_sampler","range_choice","final_density"):
            self.assertNotIn(disallowed,source)
        measured=json.loads((DATA/"usgs_big_sur_v2/terrain_density.json").read_text())
        self.assertEqual(measured["type"],"realscale:dem_density_seamless")
        self.assertEqual(measured["blend_meters"],2048)
        new_base=measured["base"]
        old_base=json.loads((DATA/"realism_max/terrain_density.json").read_text())
        self.assertEqual(new_base["type"],old_base["type"])
        self.assertEqual(new_base["argument2"],old_base["argument2"])
        self.assertEqual(old_base["argument1"],"realscale:realism_max/height_signal")
        self.assertEqual(new_base["argument1"],{
            "type":"realscale:measured_horizontal_memo",
            "input":old_base["argument1"]})

    def test_per_seed_per_thread_bounded_cache_and_visitor(self):
        source=(JAVA/"MeasuredHorizontalMemoDensity.java").read_text()
        self.assertIn("ThreadLocal.withInitial",source)
        core=(JAVA/"MeasuredColumnMemo.java").read_text()
        self.assertIn("static final int SLOTS=8192",core)
        self.assertIn("input.compute(ctx)",source)
        self.assertIn("input.mapAll(visitor)",source)
        self.assertIn("new MeasuredHorizontalMemoDensity(",source)
        self.assertIn("memo.lookup(ctx.blockX(),ctx.blockZ())",source)
        self.assertIn("memo.store(value)",source)
        self.assertIn("lastCoordinate==coordinate",core)
        self.assertIn("coordinates[slot]==coordinate",core)
        self.assertNotIn("static final ThreadLocal<MeasuredColumnMemo>",source)
        reg=(JAVA/"RealScaleMod.java").read_text()
        self.assertIn('"measured_horizontal_memo"',reg)

if __name__=="__main__":
    unittest.main()
