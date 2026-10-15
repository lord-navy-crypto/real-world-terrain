#!/usr/bin/env python3
"""Unit tests for fail-closed saved Anvil terrain quality gates."""
import sys,unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent/"neoforge"))
from check_realism_continuity import terrain_quality_gates

class SurfaceGates(unittest.TestCase):
    def audit(self, borders, insides, grids=None, inner=None, spikes=0, water=0):
        return terrain_quality_gates(
            borders, insides, grids if grids is not None else borders,
            inner if inner is not None else insides, spikes,2000,water)
    def test_natural_cliffs_do_not_fail(self):
        self.assertTrue(self.audit([2]*1000,[2]*1000,spikes=1,water=20)["pass"])
    def test_chunk_border_step_pattern_fails(self):
        r=self.audit([9]*1000,[1]*1000)
        self.assertFalse(r["pass"])
        self.assertIn("Systematic 16-block chunk-border height seam",r["issues"])
    def test_four_block_interpolation_seam_fails(self):
        r=self.audit([1]*1000,[1]*1000,[8]*1000,[1]*1000)
        self.assertFalse(r["pass"])
        self.assertIn("Systematic 4-block interpolation-grid height seam",r["issues"])
    def test_towers_and_pits_fail(self):
        self.assertFalse(self.audit([1]*1000,[1]*1000,spikes=12)["pass"])
    def test_high_elevation_inland_ocean_fails(self):
        r=self.audit([1]*1000,[1]*1000,water=400)
        self.assertFalse(r["pass"])
        self.assertIn("Unexpected widespread high-altitude water outside River biomes",r["issues"])

if __name__=="__main__":unittest.main()
