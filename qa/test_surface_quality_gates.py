#!/usr/bin/env python3
"""Unit tests for fail-closed saved Anvil terrain quality gates."""
import sys,unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent/"neoforge"))
from check_realism_continuity import adjacent_height_deltas,terrain_quality_gates

class SurfaceGates(unittest.TestCase):
    def audit(self, borders, insides, grids=None, inner=None, spikes=0, water=0,
              chunk_curve=(None,None),grid_curve=(None,None)):
        return terrain_quality_gates(
            borders,insides,grids if grids is not None else borders,
            inner if inner is not None else insides,spikes,2000,water,
            chunk_curvature_boundary=chunk_curve[0],
            chunk_curvature_inner=chunk_curve[1],
            grid_curvature_boundary=grid_curve[0],
            grid_curvature_inner=grid_curve[1])
    def test_natural_cliffs_do_not_fail(self):
        self.assertTrue(self.audit([2]*1000,[2]*1000,spikes=1,water=20,
            chunk_curve=([1]*1000,[1]*1000),
            grid_curve=([1]*1000,[1]*1000))["pass"])
    def test_chunk_border_step_pattern_fails(self):
        r=self.audit([9]*1000,[1]*1000,chunk_curve=([9]*1000,[1]*1000),
            grid_curve=([1]*1000,[1]*1000))
        self.assertFalse(r["pass"])
        self.assertIn("Systematic 16-block chunk-border height seam",r["issues"])
    def test_four_block_interpolation_seam_fails(self):
        r=self.audit([1]*1000,[1]*1000,[8]*1000,[1]*1000,
            chunk_curve=([1]*1000,[1]*1000),
            grid_curve=([8]*1000,[1]*1000))
        self.assertFalse(r["pass"])
        self.assertIn("Systematic 4-block interpolation-grid height seam",r["issues"])
    def test_steep_but_linear_gradient_at_boundaries_does_not_fail(self):
        # First differences alone cannot distinguish a smooth 8-block rise
        # per sample cell from a discontinuous jump at the cell boundary.
        r=self.audit([8]*1000,[1]*1000,[8]*1000,[1]*1000,
            chunk_curve=([0.5]*1000,[0.5]*1000),
            grid_curve=([0.5]*1000,[0.5]*1000))
        self.assertTrue(r["pass"],r["issues"])
    def test_saved_heightmap_curvature_detects_a_real_grid_step(self):
        heights={(x,z):2*x+3*z+(8 if x>=8 else 0)
                 for x in range(-2,22) for z in range(-2,6)}
        (_,grid_edge,grid_inner,chunk_edge,chunk_inner,
         grid_curve,inner_grid_curve,chunk_curve,inner_chunk_curve)=adjacent_height_deltas(heights)
        self.assertTrue(grid_curve)
        self.assertGreater(max(grid_curve),4.0)
        r=terrain_quality_gates(chunk_edge,chunk_inner,grid_edge,grid_inner,
            0,len(heights),0,
            chunk_curvature_boundary=chunk_curve,
            chunk_curvature_inner=inner_chunk_curve,
            grid_curvature_boundary=grid_curve,
            grid_curvature_inner=inner_grid_curve)
        self.assertFalse(r["pass"])
        self.assertIn("Systematic 4-block interpolation-grid height seam",r["issues"])
    def test_towers_and_pits_fail(self):
        self.assertFalse(self.audit([1]*1000,[1]*1000,spikes=12,
            chunk_curve=([1]*1000,[1]*1000),
            grid_curve=([1]*1000,[1]*1000))["pass"])
    def test_high_elevation_inland_ocean_fails(self):
        r=self.audit([1]*1000,[1]*1000,water=400,
            chunk_curve=([1]*1000,[1]*1000),
            grid_curve=([1]*1000,[1]*1000))
        self.assertFalse(r["pass"])
        self.assertIn("Unexpected widespread high-altitude water outside River biomes",r["issues"])

if __name__=="__main__":unittest.main()
