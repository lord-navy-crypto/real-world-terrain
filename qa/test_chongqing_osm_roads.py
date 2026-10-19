#!/usr/bin/env python3
"""Test true-source highway tags, inferred widths, negative XY and no 3D flattening."""
import sys
import tempfile
import unittest
import gzip
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/"tools"))
import chongqing_osm_roads as roads
import chongqing_export_standalone_world as exporter
from chongqing_osm_to_minecraft import Dem,DEFAULT_DEM

def patch(dem,highway="primary",width=None,bridge=False,lanes=None):
    nodes=[]
    for k,(x,z) in enumerate([(-22,0),(-5,0),(17,0),(35,0)]):
        lon=dem.lon+x/dem.m_lon
        lat=dem.lat-z/dem.m_lat
        nodes.append(f'<node id="{k+1}" lon="{lon}" lat="{lat}"/>')
    attrs=f'<tag k="highway" v="{highway}"/>'
    if width:attrs+=f'<tag k="width" v="{width}"/>'
    if lanes:attrs+=f'<tag k="lanes" v="{lanes}"/>'
    if bridge:attrs+='<tag k="bridge" v="yes"/>'
    refs="".join(f'<nd ref="{i}"/>' for i in range(1,5))
    return ('<osm version="0.6" license="https://opendatacommons.org/licenses/odbl/1-0/">'
            +''.join(nodes)+f'<way id="9988123">{refs}{attrs}</way></osm>').encode()

class RealRoadClassTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.dem=Dem(DEFAULT_DEM)

    def test_width_is_actual_osm_tag_when_present(self):
        self.assertEqual(roads.parse_width({"highway":"primary","width":"10 m"}),
                         (10,"high","osm_width_m"))
        self.assertEqual(roads.parse_width({"highway":"secondary","lanes":"3"}),
                         (9.600000000000001,"medium","osm_lanes_estimate"))
        self.assertEqual(roads.parse_width({"highway":"residential"}),
                         (5,"low","highway_class_inference"))
        self.assertIsNone(roads.parse_width({"highway":"elevator"}))

    def test_real_osm_centerline_expands_without_moving_geographic_axis(self):
        raw=patch(self.dem,width="8")
        plan=roads.classify_osm_roads(raw,self.dem)
        self.assertEqual(plan["license"],"ODbL-1.0")
        self.assertEqual(plan["osm_road_type_count"]["primary"],1)
        self.assertGreater(plan["classified_road_surface_cells"],350)
        self.assertLess(plan["classified_road_surface_cells"],800)
        self.assertTrue(all(row[4]=="high" for row in plan["rows"]))
        with tempfile.TemporaryDirectory() as temp:
            file=Path(temp)/"roads.json.gz"
            file.write_bytes(gzip.compress(json.dumps(plan).encode()))
            cells,meta=exporter.read_road_corridors(file,self.dem.sha256)
        self.assertIn((-20,0),cells)
        self.assertIn((0,0),cells)
        self.assertIn((30,3),cells)
        self.assertNotIn((30,11),cells)
        self.assertEqual(len(cells),plan["classified_road_surface_cells"])

    def test_bridge_not_projected_to_ground(self):
        plan=roads.classify_osm_roads(
            patch(self.dem,bridge=True,width="12"),self.dem)
        self.assertEqual(plan["classified_road_surface_cells"],0)
        self.assertEqual(plan["deferred_road_types"]["elevated_or_buried"],1)

    def test_surface_width_of_paths_is_smaller_than_actual_road(self):
        paved=roads.classify_osm_roads(
            patch(self.dem,highway="footway"),self.dem)
        highway=roads.classify_osm_roads(
            patch(self.dem,highway="primary"),self.dem)
        self.assertLess(paved["classified_road_surface_cells"],
                        highway["classified_road_surface_cells"])

    def test_no_license_no_road_corpus(self):
        raw=patch(self.dem).replace(b"licenses/odbl/1-0/",b"nonopen/")
        with self.assertRaises(ValueError):
            roads.classify_osm_roads(raw,self.dem)

if __name__=="__main__":unittest.main()
