#!/usr/bin/env python3
"""OSM importer query and network safety tests; NO live OSM request in CI."""
import importlib.util
import io
import json
import unittest
from pathlib import Path
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[1]
import sys
sys.path.insert(0,str(ROOT/"tools"))
import chongqing_fetch_osm as osm
import chongqing_osm_to_minecraft as minecraft

class Response:
    status=200
    def __init__(self,data):
        self.bytes=data
    def read(self,n):
        return self.bytes[:n]
    def __enter__(self):
        return self
    def __exit__(self,*args):
        return False

class OSMFetchTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.dem=minecraft.Dem(minecraft.DEFAULT_DEM)

    def test_query_matches_actual_dem_center_and_500m_radius(self):
        q,bbox=osm.query(self.dem,500)
        self.assertIn("out meta geom;",q)
        self.assertIn('way["building"]',q)
        self.assertIn('way["highway"]',q)
        self.assertIn('way["natural"="water"]',q)
        self.assertIn('relation["natural"="water"]["type"="multipolygon"]',q)
        self.assertIn('relation["building"]["type"="multipolygon"]',q)
        south,west,north,east=map(float,bbox.split(","))
        self.assertLess(south,self.dem.lat)
        self.assertLess(west,self.dem.lon)
        self.assertGreater(north,self.dem.lat)
        self.assertGreater(east,self.dem.lon)
        self.assertAlmostEqual((north+south)/2,self.dem.lat,places=6)
        self.assertAlmostEqual((east+west)/2,self.dem.lon,places=6)
        with self.assertRaises(ValueError):
            osm.query(self.dem,10000)

    def test_invalid_or_empty_response_fails_no_fabricated_map(self):
        with patch.object(osm.urllib.request,"urlopen",
                          return_value=Response(b'{"elements":[]}')):
            with self.assertRaisesRegex(ValueError,"no complete"):
                osm.retrieve(osm.DEFAULT_ENDPOINT,"[out:json];node;out;")
        with patch.object(osm.urllib.request,"urlopen",
                          return_value=Response(b'<html>503</html>')):
            with self.assertRaises(json.JSONDecodeError):
                osm.retrieve(osm.DEFAULT_ENDPOINT,"[out:json];node;out;")

    def test_single_real_way_shape_is_preserved_from_overpass_format(self):
        data={"elements":[{"type":"way","id":12345,
               "tags":{"highway":"path"},
               "geometry":[{"lon":self.dem.lon,"lat":self.dem.lat},
                    {"lon":self.dem.lon+0.00001,"lat":self.dem.lat}]}]}
        with patch.object(osm.urllib.request,"urlopen",
                          return_value=Response(json.dumps(data).encode())):
            result=osm.retrieve(osm.DEFAULT_ENDPOINT,
                                "[out:json];way;out geom;")
        self.assertEqual(result,data)
        self.assertEqual(minecraft.osm_features(result)[0]["id"],"way/12345")

if __name__=="__main__":
    unittest.main()
