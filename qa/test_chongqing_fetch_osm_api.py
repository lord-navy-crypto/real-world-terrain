#!/usr/bin/env python3
"""Synthetic XML unit fixtures; separate from the checked-in genuine source."""
import base64
import gzip
import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/"tools"))
import chongqing_fetch_osm_api as api
import chongqing_osm_to_minecraft as gis


FIXTURE = b"""<?xml version="1.0" encoding="UTF-8"?>
<osm version="0.6" generator="test fixture"
     license="http://opendatacommons.org/licenses/odbl/1-0/">
 <node id="1" lat="29.5667" lon="106.5874"/>
 <node id="2" lat="29.5667" lon="106.5875"/>
 <node id="3" lat="29.5668" lon="106.5875"/>
 <node id="4" lat="29.5668" lon="106.5874"/>
 <way id="100" version="3"><nd ref="1"/><nd ref="2"/><nd ref="3"/>
   <nd ref="4"/><nd ref="1"/>
   <tag k="building" v="yes"/><tag k="name" v="Unit-test elevated bridge"/>
   <tag k="min_height" v="250"/><tag k="layer" v="1"/>
 </way>
 <way id="101" version="4"><nd ref="1"/><nd ref="2"/>
   <tag k="highway" v="residential"/>
 </way>
 <way id="102" version="1"><nd ref="1"/><nd ref="99"/>
   <tag k="building" v="yes"/>
 </way>
 <relation id="200" version="1"><member type="way" ref="102" role="outer"/>
   <tag k="type" v="multipolygon"/><tag k="natural" v="water"/>
 </relation>
</osm>"""


class OfficialAPIAdapterTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.dem=gis.Dem(gis.DEFAULT_DEM)

    def test_local_bounds_preserve_real_dem_origin_and_buffer(self):
        bbox,url=api.bounds(self.dem,500,100)
        self.assertEqual(url.split("?")[0],api.API)
        self.assertLess(bbox[0],self.dem.lon)
        self.assertLess(bbox[1],self.dem.lat)
        self.assertGreater(bbox[2],self.dem.lon)
        self.assertGreater(bbox[3],self.dem.lat)
        self.assertAlmostEqual((bbox[0]+bbox[2])/2,self.dem.lon,places=6)
        self.assertAlmostEqual((bbox[1]+bbox[3])/2,self.dem.lat,places=6)
        with self.assertRaises(ValueError):
            api.bounds(self.dem,513,100)

    def test_preserve_exact_osm_node_geometry_and_defer_missing_members(self):
        bbox,url=api.bounds(self.dem,100)
        data=api.convert_xml(FIXTURE,url,bbox,"2026-10-19T00:00:00Z")
        source=data["realscale_source"]
        self.assertEqual(source["raw_xml_sha256"],hashlib.sha256(FIXTURE).hexdigest())
        self.assertEqual(source["total_nodes"],4)
        self.assertEqual(source["total_ways"],3)
        self.assertEqual(source["included_elements"],2)
        self.assertIn("way/102",source["rejected_incomplete_objects"])
        self.assertIn("relation/200",source["rejected_incomplete_objects"])
        features={f["id"]:f for f in gis.osm_features(data)}
        self.assertEqual(features["way/100"]["tags"]["min_height"],"250")
        self.assertEqual(features["way/100"]["coordinates"][0][0],
                         [106.5874,29.5667])
        self.assertEqual(features["way/101"]["kind"],"LineString")
        # No ground-level fake of the high-altitude building.
        with self.assertRaisesRegex(ValueError,"No real geometries"):
            gis.prepare(self.dem,[features["way/100"]],100)

    def test_roundtrip_archived_official_xml_adapter(self):
        bbox,url=api.bounds(self.dem,100)
        source=api.convert_xml(FIXTURE,url,bbox,"2026-10-19T00:00:00Z")
        with tempfile.TemporaryDirectory() as t:
            path=Path(t)/"safe.json.gz.b64"
            payload=json.dumps(source,ensure_ascii=False).encode()
            path.write_text(base64.b64encode(gzip.compress(payload,mtime=0)).decode())
            self.assertEqual(gis.load_gis_source(path),source)

    def test_reject_imaginary_xml_license_and_empty_geography(self):
        bbox,url=api.bounds(self.dem,100)
        with self.assertRaises(ValueError):
            api.convert_xml(b"<html>Fake</html>",url,bbox,"2026-10-19")
        with self.assertRaisesRegex(ValueError,"ODbL"):
            api.convert_xml(FIXTURE.replace(b"odbl/1-0/",b"proprietary/"),
                            url,bbox,"2026-10-19")
        with self.assertRaisesRegex(ValueError,"No complete supported"):
            api.convert_xml(b'''<osm version="0.6"
                license="http://opendatacommons.org/licenses/odbl/1-0/"></osm>''',
                url,bbox,"2026-10-19")


if __name__=="__main__":
    unittest.main()
