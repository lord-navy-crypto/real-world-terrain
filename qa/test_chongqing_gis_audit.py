#!/usr/bin/env python3
"""Synthetic vector regression tests for offline Chaotianmen GIS audit."""
import hashlib
import json
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/"tools"))
import chongqing_gis_audit as audit
import chongqing_osm_to_minecraft as gis

def ll(dem,x,z):
    return [dem.lon+x/dem.m_lon,dem.lat-z/dem.m_lat]

def feat(fid,kind,tags,geometry):
    return {"type":"Feature","id":fid,"properties":tags,
            "geometry":{"type":kind,"coordinates":geometry}}

class AuditTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.dem=gis.Dem(gis.DEFAULT_DEM)

    def synthetic_source(self):
        d=self.dem
        building = [[ll(d,x,z) for x,z in
                   ((-20,-20),(-10,-20),(-10,-10),(-20,-10),(-20,-20))]]
        lake = [[ll(d,x,z) for x,z in
                ((20,20),(40,20),(40,40),(20,40),(20,20))]]
        return {"type":"FeatureCollection","features":[
            feat("QA-building","Polygon",{
                "building":"yes","name":"重庆来福士 [SYNTHETIC TEST]","height":"100"},building),
            feat("QA-road","LineString",{"highway":"residential","bridge":"yes"},
                 [ll(d,-30,0),ll(d,30,0)]),
            feat("QA-river","Polygon",{"natural":"water"},lake),
            feat("QA-far-road","LineString",{"highway":"primary"},
                 [ll(d,1000,1000),ll(d,1050,1050)])]}

    def test_report_identifies_candidate_without_claiming_true_city(self):
        data=self.synthetic_source()
        raw=json.dumps(data).encode("utf-8")
        report,entries=audit.inspect(
            data,self.dem,100,hashlib.sha256(raw).hexdigest())
        self.assertEqual(len(entries),3)
        self.assertEqual(report["counts"]["input_highway"],2)
        self.assertEqual(report["counts"]["features_outside_pilot_bbox"],1)
        self.assertEqual(report["counts"]["building_has_height_tag"],1)
        self.assertEqual(report["counts"]["transport_requires_vertical_survey"],1)
        self.assertEqual(report["counts"]["water_polygon_candidates"],1)
        self.assertEqual(report["named_landmark_candidates"]["Raffles City / 重庆来福士"],
                         ["QA-building"])
        self.assertIn("Chaotianmen Square / 朝天门广场",
                      report["missing_landmark_names_in_source"])
        self.assertIn("NOT independently verified",report["data_validation"])

    def test_svg_keeps_real_coordinates_and_escapes_untrusted_names(self):
        data=self.synthetic_source()
        data["features"][0]["properties"]["name"] = "<script>bad</script>"
        report,entries=audit.inspect(data,self.dem,100,"synthetic-test-sha")
        xml=audit.render_svg(report,entries,100)
        self.assertNotIn("<script>",xml)
        self.assertIn("&lt;script&gt;",xml)
        root=ET.fromstring(xml)
        self.assertTrue(root.tag.endswith("svg"))
        self.assertEqual(root.attrib["viewBox"],"-100 -100 200 200")
        paths=root.findall(".//{http://www.w3.org/2000/svg}path")
        self.assertTrue(any(p.attrib.get("data-osm-id")=="QA-river" for p in paths))
        self.assertFalse(any(p.attrib.get("data-osm-id")=="QA-far-road" for p in paths))

    def test_cli_exports_in_new_directory_and_refuses_overwrite(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            src=root/"qa-only.geojson"
            src.write_text(json.dumps(self.synthetic_source()),encoding="utf-8")
            dest=root/"audit"
            self.assertEqual(audit.main(
                ["--input",str(src),"--output",str(dest),"--radius","100"]),0)
            self.assertTrue((dest/"plan.svg").is_file())
            self.assertEqual(json.loads((dest/"audit.json").read_text())
                             ["radius_m"],100)
            with self.assertRaises(SystemExit):
                audit.main(["--input",str(src),"--output",str(dest)])

if __name__ == "__main__":
    unittest.main()
