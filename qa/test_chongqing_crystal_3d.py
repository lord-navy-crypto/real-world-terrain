#!/usr/bin/env python3
"""Actual archived OSM water-Crystal 3D tag/geometry tests, no fake tower XY."""
import sys
import unittest
from pathlib import Path
from collections import Counter
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/"tools"))
from chongqing_crystal_3d import build_crystal,CRYSTAL_OSM,ANCHOR_TOWERS,RADIUS

class CrystalSurveyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.blocks,cls.info=build_crystal()

    def test_original_osm_geometry_and_tags_are_preserved(self):
        m=self.info
        self.assertEqual(m["osm_source_id"],f"way/{CRYSTAL_OSM}")
        self.assertEqual(m["source_min_height_m"],250)
        self.assertEqual(m["source_height_m"],276.5)
        self.assertEqual(m["source_vertical_span_m"],26)
        self.assertEqual(m["adjacent_real_osm_towers"],list(ANCHOR_TOWERS))
        self.assertGreater(m["source_polygon_vertex_count"],30)
        self.assertGreater(m["source_polygon_1m_cells"],1000)
        self.assertEqual(m["vertical_registration_confidence"][:3],"LOW")

    def test_tubular_geometry_is_above_city_not_ground_paint(self):
        m=self.info
        floor=m["anchored_floor_y"]
        self.assertGreater(floor,450)
        self.assertLess(floor,700)
        self.assertGreater(len(self.blocks),m["source_polygon_1m_cells"]*2)
        self.assertTrue(all(-RADIUS<=x<=RADIUS and -RADIUS<=z<=RADIUS
                            for x,y,z,block in self.blocks))
        self.assertTrue(all(floor<=y<=floor+26
                            for x,y,z,block in self.blocks))
        self.assertTrue(all(block in (16,17,18)
                            for x,y,z,block in self.blocks))
        self.assertEqual(sum(1 for x,y,z,block in self.blocks if block==18),
                         m["source_polygon_1m_cells"])
        self.assertTrue(any(block==17 for x,y,z,block in self.blocks))
        self.assertTrue(any(block==16 for x,y,z,block in self.blocks))
        positions=[(x,y,z) for x,y,z,block in self.blocks]
        self.assertEqual(len(positions),len(set(positions)),
                         "Source Crystal must not write any Minecraft location twice")

if __name__=="__main__":unittest.main()
