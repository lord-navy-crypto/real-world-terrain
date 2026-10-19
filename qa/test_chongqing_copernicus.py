#!/usr/bin/env python3
"""Offline synthetic multispectral tests; no fake 1m imagery is claimed."""
import sys,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/"tools"))
from chongqing_copernicus_landcover import spectral_decision,to_surface_rows
from chongqing_osm_to_minecraft import Dem,DEFAULT_DEM

class SatelliteEvidenceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.dem=Dem(DEFAULT_DEM)

    def test_vegetation_requires_scene_classification_and_ndvi(self):
        self.assertEqual(spectral_decision(1300,800,2500,4),("grass","medium"))
        self.assertIsNone(spectral_decision(1300,800,2500,8))
        self.assertIsNone(spectral_decision(1300,800,2500,6))
        self.assertIsNone(spectral_decision(1300,800,2500,3))

    def test_bare_cannot_be_confidently_called_concrete(self):
        self.assertEqual(spectral_decision(1000,2500,2600,5),
                         ("urban_inferred","low"))
        self.assertIsNone(spectral_decision(1000,2500,5000,5))

    def test_exact_ten_metre_grid_does_not_pretend_one_metre_pixels(self):
        try:import numpy as np
        except ImportError:self.skipTest("numpy optional, used in satellite workflow")
        a=np.full((101,101),1000,dtype=np.uint16)
        b=np.full((101,101),500,dtype=np.uint16)
        c=np.full((101,101),2500,dtype=np.uint16)
        d=np.full((101,101),4,dtype=np.uint16)
        item="S2B_MSIL2A_20261001T123456_N0500_R000_T48RUS"
        result=to_surface_rows(a,b,c,d,item,self.dem)
        self.assertEqual(result["resolution_m"],10)
        self.assertEqual(result["known_surface_columns"],1002001)
        self.assertEqual(result["materials"]["grass"],1002001)
        self.assertEqual(result["rows"][0][:3],[-500,-500,-491])
        self.assertEqual(result["rows"][-1][:3],[500,500,500])
        self.assertEqual(result["unmapped_surface_columns"],0)

if __name__=="__main__":unittest.main()
