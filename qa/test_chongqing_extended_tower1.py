#!/usr/bin/env python3
"""True OSM Raffles T1 test: only uncovered 500..600m source coordinates."""
import sys,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/"tools"))
from chongqing_extended_tower1 import build_real_outer_t1,T1_OSM

class ActualOutside500mTowerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.writes,cls.source=build_real_outer_t1()

    def test_osm_t1_is_actual_missing_eighth_tower(self):
        self.assertEqual(self.source["osm_source"],f"way/{T1_OSM}")
        self.assertEqual(self.source["source_height_m"],250)
        self.assertEqual(self.source["world_extent_m"],600)
        self.assertTrue(-600<=self.source["bbox_xz"][0]<-520)
        self.assertTrue(self.source["bbox_xz"][2]<-500)
        self.assertTrue(self.source["source_footprint_1m_columns"]>=1000)

    def test_tower_shell_writes_no_fictional_inside_500m(self):
        self.assertGreater(len(self.writes),30000)
        self.assertTrue(all(abs(x)>500 or abs(z)>500
                            for x,y,z,block in self.writes))
        self.assertTrue(all(-600<=x<=600 and -600<=z<=600
                            for x,y,z,block in self.writes))
        self.assertTrue(all(block in (4,5)
                            for x,y,z,block in self.writes))
        cells={(x,z) for x,y,z,b in self.writes}
        self.assertEqual(len(cells),self.source["source_footprint_1m_columns"])
        self.assertEqual(len(self.writes),len({(x,y,z) for x,y,z,b in self.writes}),
                         "Never write T1 local block twice")

if __name__=="__main__":unittest.main()
