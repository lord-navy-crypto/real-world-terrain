#!/usr/bin/env python3
"""Graph tests; not a substitute for real Minecraft saved-world scans."""
import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent/"neoforge"))
from check_realism_continuity import cave_components,dry_walkable_components

class CaveWalkabilityTests(unittest.TestCase):
    def test_dry_two_high_crosses_chunk_edge(self):
        dry={(x,y,0) for x in (14,15,16,17) for y in (5,6)}
        r=dry_walkable_components(dry,dry)
        self.assertEqual(r["two_high_grounded_floor_cells"],4)
        self.assertEqual(r["walkable_components"],1)
        self.assertEqual(r["walkable_components_crossing_chunk_boundaries"],1)
        self.assertEqual(r["largest_walkable_component_chunk_span"],2)

    def test_one_block_step_up(self):
        dry={(0,5,0),(0,6,0),(1,6,0),(1,7,0)}
        r=dry_walkable_components(dry,dry)
        self.assertEqual(r["walkable_components"],1)
        self.assertEqual(r["two_high_grounded_floor_cells"],2)

    def test_water_headspace_or_one_high_is_not_walkable(self):
        dry={(0,5,0),(0,6,0),(1,5,0)}
        flooded=dry|{(1,6,0)}
        r=dry_walkable_components(flooded,dry)
        self.assertEqual(r["two_high_grounded_floor_cells"],1)

    def test_missing_sampled_floor_is_not_support(self):
        dry={(0,5,0),(0,6,0)}
        r=dry_walkable_components(dry,dry,sampled_min_y={(0,0):5})
        self.assertEqual(r["two_high_grounded_floor_cells"],0)

    def test_lava_floor_is_not_safe(self):
        dry={(0,5,0),(0,6,0)}
        r=dry_walkable_components(dry,dry,invalid_floor_voxels={(0,4,0)})
        self.assertEqual(r["two_high_grounded_floor_cells"],0)

    def test_genuine_3d_six_neighbor_link(self):
        voxels={(15,4,0),(16,4,0),(16,5,0),(17,5,0)}
        r=cave_components(voxels)
        self.assertEqual(r["component_count"],1)
        self.assertEqual(r["components_crossing_chunk_boundary"],1)
        self.assertEqual(r["largest_component_chunk_span"],2)

if __name__=="__main__": unittest.main()
