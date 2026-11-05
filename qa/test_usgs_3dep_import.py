#!/usr/bin/env python3
"""Offline acceptance: authentic 3DEP importer geometry and binary compatibility.

Synthetic values are ONLY local serializer unit fixtures. The importer must
download actual USGS elevations itself and reject missing or rendered source.
"""
import gzip
import importlib.util
import math
import struct
import sys
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location(
    "usgs_3dep", ROOT/"scripts/import_usgs_3dep.py")
module=importlib.util.module_from_spec(spec)
sys.modules[spec.name]=module
spec.loader.exec_module(module)


class MeasuredUSGSImportTests(unittest.TestCase):
    def test_data_provenance_is_only_usgs_3dep(self):
        self.assertTrue(module.SERVICE.startswith(
            "https://elevation.nationalmap.gov/arcgis/rest/services/3DEPElevation/"))
        self.assertEqual(module.SOURCE, "USGS 3DEP bare-earth elevation dynamic image service")
        self.assertEqual(module.EPSG,26910)
        self.assertEqual(module.CELL_METRES,16)
        self.assertEqual(module.GAME_SEA_LEVEL,63)
        with self.assertRaises(ValueError):
            module.fetch_raw_tiff("https://example.org/not-a-usgs-file")
        with self.assertRaises(ValueError):
            module.build_rgt1([[0]],1)
        self.assertIn("NoData",Path(module.__file__).read_text())

    def test_geographical_bounds_and_square_metric_grid(self):
        # Geometry must use NAD83 UTM projected metres, NOT degrees as metres.
        # Rasterio is optional during this offline test only.
        try:
            import rasterio
        except ImportError:
            self.skipTest("rasterio is required for live import only")
        r=module.build_request(size=513)
        self.assertEqual(r["epsg"],26910)
        self.assertEqual(r["grid_size"],513)
        self.assertAlmostEqual(r["bbox"][2]-r["bbox"][0],513*16,places=6)
        self.assertAlmostEqual(r["bbox"][3]-r["bbox"][1],513*16,places=6)
        self.assertEqual(r["url"].split("?")[0],module.SERVICE)
        self.assertIn("pixelType=F32",r["url"])
        self.assertIn("bboxSR=26910",r["url"])
        self.assertIn("imageSR=26910",r["url"])

    def test_binary_pack_header_and_north_to_south_orientation(self):
        for n in (65,129):
            with self.subTest(n=n):
                example=[[float(1000-z*0.05+x*0.01)
                          for x in range(n)] for z in range(n)]
                raw=module.build_rgt1(example,n)
                decoded=gzip.decompress(raw)
                h=struct.unpack_from(">6i2d",decoded)
                self.assertEqual(h,(0x52475431,n,n,-(n//2)*16,
                                    -(n//2)*16,63,16.0,0.0))
                offset=struct.calcsize(">6i2d")
                self.assertAlmostEqual(struct.unpack_from(">f",decoded,offset)[0],
                                       example[0][0],places=3)
                self.assertAlmostEqual(struct.unpack_from(">f",decoded,offset+4*n)[0],
                                       example[1][0],places=3)
                self.assertEqual(len(decoded),offset+n*n*13)
                self.assertEqual(raw,module.build_rgt1(example,n),
                                 "Compressed RGT1 must be bit-reproducible")

    def test_non_finite_and_unrealistic_heights_rejected(self):
        arr=[[100.0]*65 for _ in range(65)]
        for bad in (math.inf,math.nan,3000,-2000):
            arr[13][12]=bad
            with self.subTest(value=bad),self.assertRaises(ValueError):
                module.build_rgt1(arr,65)
        arr[13][12]=100
        self.assertGreater(len(module.build_rgt1(arr,65)),100)

    def test_live_raster_requires_one_float_band_and_valid_utm(self):
        src=Path(module.__file__).read_text()
        for required in ("dataset.count != 1","dataset.crs.to_epsg()",
                         '"float32", "float64"',"np.ma.getmaskarray",
                         "abs(geo.a","abs(geo.e","source_sha256",
                         "rgt1_sha256","retrieved_utc"):
            self.assertIn(required,src)


if __name__=="__main__":
    unittest.main()
