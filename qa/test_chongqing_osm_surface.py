#!/usr/bin/env python3
"""Non-network regression: strict evidence-based 1m OSM ground conversion."""
import sys
import tempfile
import unittest
import gzip
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/"tools"))
import chongqing_osm_surface as surface
import chongqing_export_standalone_world as exporter
from chongqing_osm_to_minecraft import Dem, DEFAULT_DEM


def fake_osm_patch(dem, tag_key, tag_value, way_id, width_m=24):
    def n(x,z):
        return (dem.lon+x/dem.m_lon,dem.lat-z/dem.m_lat)
    coords=[n(2,2),n(width_m,2),n(width_m,width_m),
            n(2,width_m),n(2,2)]
    nodes="".join(
        f'<node id="{i+1}" lat="{p[1]}" lon="{p[0]}" />'
        for i,p in enumerate(coords[:-1]))
    node_refs="".join(f'<nd ref="{id}"/>' for id in (1,2,3,4,1))
    return (f'<osm version="0.6" license="https://opendatacommons.org/licenses/odbl/1-0/">'
            f'{nodes}<way id="{way_id}">{node_refs}'
            f'<tag k="{tag_key}" v="{tag_value}"/>'
            f'</way></osm>').encode()


class SourceGroundTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.dem=Dem(DEFAULT_DEM)

    def test_real_tagged_asphalt_rasterizes_not_universal_grass(self):
        raw=fake_osm_patch(self.dem,"surface","asphalt",123)
        data=surface.classify_osm_xml(raw,self.dem)
        self.assertEqual(data["license"],"ODbL-1.0")
        self.assertGreater(data["known_surface_columns"],300)
        self.assertIn("asphalt",data["materials"])
        self.assertEqual(data["raw_osm_api_sha256"].__len__(),64)
        with tempfile.TemporaryDirectory() as d:
            path=Path(d)/"osm.gz"
            path.write_bytes(gzip.compress(json.dumps(data).encode()))
            cells,meta=exporter.read_surface_mask(path,self.dem.sha256)
        self.assertEqual(cells[(10,10)],exporter.SURFACE_BLOCK["asphalt"])
        self.assertNotIn((-100,-100),cells)

    def test_commercial_landuse_is_explicitly_low_confidence(self):
        raw=fake_osm_patch(self.dem,"landuse","commercial",123)
        data=surface.classify_osm_xml(raw,self.dem)
        self.assertTrue(data["rows"])
        self.assertTrue(all(r[4]=="low" for r in data["rows"]))
        self.assertEqual(data["rows"][0][3],"urban_inferred")

    def test_out_of_ground_buildings_are_not_flattened(self):
        raw=fake_osm_patch(self.dem,"building","yes",123)
        data=surface.classify_osm_xml(raw,self.dem)
        self.assertEqual(data["known_surface_columns"],0)
        self.assertEqual(data["rows"],[])

    def test_unknown_surfaces_do_not_get_made_up_classes(self):
        raw=fake_osm_patch(self.dem,"surface","futuristic_metal",123)
        data=surface.classify_osm_xml(raw,self.dem)
        self.assertEqual(data["known_surface_columns"],0)

    def test_improper_license_disallowed(self):
        raw=fake_osm_patch(self.dem,"surface","asphalt",123)
        raw=raw.replace(b'https://opendatacommons.org/licenses/odbl/1-0/',
                        b'https://commercial.example/nonredistributable')
        with self.assertRaises(ValueError):
            surface.classify_osm_xml(raw,self.dem)


if __name__=="__main__":
    unittest.main()
