#!/usr/bin/env python3
"""Regression tests for saved-water depth/gradient navigation diagnostics."""
import sys
import unittest
from pathlib import Path

sys.path.insert(0,str(Path(__file__).resolve().parent/"neoforge"))
from check_realism_continuity import navigable_water_components


class RiverNavigationGeometryTest(unittest.TestCase):
    def test_flat_deep_water_crosses_chunk_border(self):
        columns={(x,3):(80,3) for x in range(13,20)}
        result=navigable_water_components(columns)
        self.assertEqual(result["largest_navigable_component_columns"],7)
        self.assertEqual(result["cross_chunk_navigable_components"],1)
        self.assertEqual(result["steep_adjacent_water_edges"],0)

    def test_one_block_step_breaks_vanilla_but_not_craft_proxy(self):
        water={(x,0):(95 if x<16 else 94,3) for x in range(12,20)}
        vanilla=navigable_water_components(water,0,2)
        hover=navigable_water_components(water,1,2)
        self.assertEqual(vanilla["navigable_components"],2)
        self.assertEqual(vanilla["steep_adjacent_water_edges"],1)
        self.assertEqual(hover["navigable_components"],1)

    def test_two_block_waterfall_is_not_claimed_navigable(self):
        water={(0,0):(200,3),(1,0):(198,3)}
        self.assertEqual(navigable_water_components(water,1,2)["navigable_components"],2)

    def test_shallow_gravel_river_is_not_declared_navigable(self):
        water={(0,0):(70,1),(1,0):(70,3),(2,0):(70,3)}
        r=navigable_water_components(water,0,2)
        self.assertEqual(r["sampled_deep_water_columns"],2)
        self.assertEqual(r["largest_navigable_component_columns"],2)

    def test_noncontiguous_diagonal_water_is_not_a_canal(self):
        water={(0,0):(90,3),(1,1):(90,3)}
        self.assertEqual(navigable_water_components(water)["navigable_components"],2)


if __name__=="__main__":
    unittest.main()
