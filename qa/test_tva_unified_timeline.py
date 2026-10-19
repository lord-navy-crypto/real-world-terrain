#!/usr/bin/env python3
"""Fail-closed inventory for the unified TVA release candidate."""
import json
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/"neoforge/src/main/java/org/realscale/neoforge"
DATA=ROOT/"neoforge/src/main/resources/data/realscale/worldgen"
class UnifiedTimelineTests(unittest.TestCase):
    def test_all_opt_in_client_sources_and_profile_defaults(self):
        for p in ("AdaptiveVisibilityBudget.java","AdaptiveVisibilityTelemetry.java",
                  "ClientFrameIntervalStats.java","RealScaleAdaptiveVisibilityClient.java",
                  "RealScaleClientFrameProfiler.java"):
            self.assertTrue((SRC/p).is_file(),p)
        for p in ("RealScaleAdaptiveVisibilityClient.java","RealScaleClientFrameProfiler.java"):
            code=(SRC/p).read_text()
            self.assertIn("Boolean.getBoolean(",code)
            self.assertIn("Dist.CLIENT",code)
            self.assertNotIn("setRenderDistance",code)
            self.assertNotIn("setSimulationDistance",code)
        self.assertIn("TRANSITION", (SRC/"AdaptiveVisibilityBudget.java").read_text())
    def test_exact_2096_no_cave_or_structure_culling(self):
        for name in ("tva_natural_smoothed","usgs_big_sur_v2"):
            data=json.loads((DATA/"noise_settings"/(name+".json")).read_text())
            self.assertEqual(data["noise"]["height"],2096)
        sacred=json.loads((DATA/"density_function/tva_natural_smoothed/height_signal.json").read_text())
        self.assertEqual(sacred["type"],"minecraft:cache_2d")
        self.assertEqual(sacred["argument"]["type"],"realscale:mountain_relief")
        measured=json.loads((DATA/"noise_settings/usgs_big_sur_v2.json").read_text())
        final=measured["noise_router"]["final_density"]
        self.assertEqual(final["type"],"realscale:grand_overworld_caverns")
        self.assertEqual(final["base"]["type"],"realscale:range_min_exact")
        self.assertEqual(final["base"]["cave"],"realscale:usgs_big_sur_v2/cave_density")
    def test_no_false_green_qas_lost_during_consolidation(self):
        cmd=(ROOT/"qa/neoforge/rcon_worldgen_smoke.py").read_text()
        self.assertIn("await_full_chunk(rcon,1024,1024,",cmd)
        self.assertIn("remaining=180.0-(time.monotonic()-budget_start)",cmd)
        wf=(ROOT/".github/workflows/ci.yml").read_text()
        for term in ("Verify far-distance cave, mountain and sky worldgen parity",
                     "Verify distant forceload never passes from ticket ACK alone",
                     "Verify adaptive visibility classification",
                     "Verify reproducible client FPS log comparisons",
                     "Verify read-only tall-world Section inventory"):
            self.assertIn(term,wf)
if __name__=="__main__":
    unittest.main()
