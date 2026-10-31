#!/usr/bin/env python3
"""Validate zonal defaults and retained radial climate experiments."""
import json
import math
import sys
import tempfile
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(Path(__file__).resolve().parent/"neoforge"))
from check_realism_continuity import (
    radial_profile_from_noise,radial_latitude_degrees,radial_biome_audit,
    zonal_profile_from_noise,zonal_latitude_degrees,zonal_biome_audit
)

class GeographicClimateAuditTest(unittest.TestCase):
    def test_active_canonical_temperature_graphs_are_zonal(self):
        root=ROOT/"neoforge/src/main/resources/data/realscale/worldgen/noise_settings"
        for preset in ("tva_natural_smoothed","tva_alpine_smoothed",
                       "tva_canyon_smoothed","usgs_big_sur_v2"):
            with self.subTest(preset=preset):
                zonal=zonal_profile_from_noise(root/(preset+".json"))
                self.assertIsNotNone(zonal)
                self.assertTrue(zonal["clamp_at_poles"])
                self.assertEqual(zonal["pole_distance"],10_001_966)
                self.assertIsNone(radial_profile_from_noise(root/(preset+".json")))

    def test_historical_pr25_profiles_remain_radial(self):
        root=ROOT/"neoforge/src/main/resources/data/realscale/worldgen/noise_settings"
        for preset in ("tva_pr25_natural_climate","tva_pr25_alpine_climate",
                       "tva_pr25_canyon_climate","tva_pr25_measured_climate"):
            with self.subTest(preset=preset):
                radial=radial_profile_from_noise(root/(preset+".json"))
                self.assertIsNotNone(radial)
                self.assertTrue(radial["area_balanced"])
                self.assertEqual(radial["pole_distance"],8192)
                self.assertIsNone(zonal_profile_from_noise(root/(preset+".json")))

    def test_zonal_latitude_is_finite_and_clamped_at_both_poles(self):
        q=10_001_966
        profile={"equator_z":0,"pole_distance":q,"clamp_at_poles":True}
        self.assertEqual(zonal_latitude_degrees(0,profile),0)
        self.assertAlmostEqual(zonal_latitude_degrees(-q,profile),90)
        self.assertAlmostEqual(zonal_latitude_degrees(q,profile),-90)
        self.assertAlmostEqual(zonal_latitude_degrees(-2*q,profile),90)
        self.assertAlmostEqual(zonal_latitude_degrees(2*q,profile),-90)

    def test_big_sur_nominal_geographic_anchor(self):
        q=10_001_966
        profile={"equator_z":4_027_458,"pole_distance":q,"clamp_at_poles":True}
        self.assertAlmostEqual(zonal_latitude_degrees(0,profile),36.24,delta=0.001)

    def test_zonal_biome_audit_reports_observed_latitude_bands(self):
        profile={"equator_z":0,"pole_distance":10_001_966,"clamp_at_poles":True}
        heights={(0,0):80,(0,-10_001_966):300,(0,10_001_966):300}
        biomes={(0,0):"minecraft:plains",
                (0,-10_001_966):"minecraft:snowy_plains",
                (0,10_001_966):"minecraft:snowy_plains"}
        result=zonal_biome_audit(heights,biomes,profile)
        self.assertEqual(result["mode"],"zonal_clamped")
        self.assertEqual(result["equator_z"],0)
        self.assertEqual(result["sample_columns"],3)

    def test_reference_nodes_and_original_single_pole_are_followed(self):
        with tempfile.TemporaryDirectory() as td:
            worldgen=Path(td)
            settings=worldgen/"noise_settings/test.json"
            referenced=worldgen/"density_function/demo/temperature.json"
            settings.parent.mkdir(parents=True)
            referenced.parent.mkdir(parents=True)
            settings.write_text(json.dumps({"noise_router":{
                "temperature":"realscale:demo/temperature"}}))
            referenced.write_text(json.dumps({
                "type":"realscale:climate_budget",
                "adjusted":{"type":"realscale:radial_latitude_temperature",
                    "base":0,"center_x":0,"center_z":0,
                    "pole_distance":8192,"strength":0.58,
                    "balanced":True,"single_pole_area":True}}))
            radial=radial_profile_from_noise(settings)
            self.assertIsNotNone(radial)
            self.assertTrue(radial["single_pole_area"])
            result=radial_biome_audit({(0,0):80},{(0,0):"minecraft:plains"},radial)
            self.assertEqual(result["mode"],"radial_equal_area_single_pole")

    def test_two_crest_equal_area_geometry_is_unchanged(self):
        q=8192
        radial={"center_x":0,"center_z":0,"pole_distance":q,
                "area_balanced":True,"balanced":True}
        self.assertAlmostEqual(radial_latitude_degrees(0,0,radial),0)
        self.assertAlmostEqual(radial_latitude_degrees(q,0,radial),90,delta=0.05)
        self.assertAlmostEqual(radial_latitude_degrees(math.sqrt(2)*q,0,radial),0,places=8)

    def test_legacy_balanced_mode_is_unchanged(self):
        q=8192
        p={"center_x":0,"center_z":0,"pole_distance":q,"balanced":True}
        self.assertAlmostEqual(radial_latitude_degrees(q/2,0,p),90*(0.5**1.15))
        p["balanced"]=False
        self.assertAlmostEqual(radial_latitude_degrees(q/2,0,p),45)

if __name__=="__main__":
    unittest.main()
