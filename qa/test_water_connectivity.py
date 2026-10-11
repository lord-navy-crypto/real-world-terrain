#!/usr/bin/env python3
"""Persistent saved-river surface graph QA math, including chunk edges."""
import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent/"neoforge"))
from check_realism_continuity import surface_water_components

class WaterGraphTests(unittest.TestCase):
    def test_empty_dry_river(self):
        r=surface_water_components(set())
        self.assertEqual(r["wet_components"],0)
        self.assertEqual(r["largest_wet_component_columns"],0)

    def test_river_spans_a_chunk_boundary(self):
        coords={(x,0) for x in range(14,19)}
        r=surface_water_components(coords)
        self.assertEqual(r["wet_components"],1)
        self.assertEqual(r["largest_wet_component_columns"],5)
        self.assertEqual(r["wet_components_crossing_chunk_boundaries"],1)

    def test_diagonal_puddles_not_connected(self):
        r=surface_water_components({(0,0),(1,1),(2,2)})
        self.assertEqual(r["wet_components"],3)
        self.assertEqual(r["isolated_one_column_puddles"],3)

    def test_dry_rock_breaks_surface_river(self):
        r=surface_water_components({(0,0),(1,0),(3,0),(4,0)})
        self.assertEqual(r["wet_components"],2)
        self.assertEqual(r["largest_wet_component_columns"],2)

if __name__=="__main__":unittest.main()
