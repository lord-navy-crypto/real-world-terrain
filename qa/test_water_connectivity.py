#!/usr/bin/env python3
"""Persistent saved-river surface graph QA math, including chunk edges."""
import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent/"neoforge"))
from check_realism_continuity import surface_water_components, classify_river_surface

class WaterGraphTests(unittest.TestCase):
    def test_snow_lid_can_hide_real_frozen_river(self):
        wet=classify_river_surface("minecraft:snow","minecraft:ice","minecraft:water")
        self.assertTrue(wet["wet"])
        self.assertTrue(wet["snow_over_ice_with_liquid"])

    def test_snow_on_grass_is_not_water(self):
        wet=classify_river_surface("minecraft:snow","minecraft:grass_block","minecraft:dirt")
        self.assertFalse(wet["wet"])

    def test_ice_over_solid_stone_not_false_water(self):
        wet=classify_river_surface("minecraft:ice","minecraft:stone","minecraft:stone")
        self.assertFalse(wet["wet"])

    def test_liquid_water_without_cover(self):
        wet=classify_river_surface("minecraft:water","minecraft:water","minecraft:gravel")
        self.assertTrue(wet["wet"])

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
