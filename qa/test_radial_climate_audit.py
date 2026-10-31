#!/usr/bin/env python3
"""Validate that live-world climate QA measures the ACTUAL radial density graph."""
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
    meridional_profile_from_noise,meridional_latitude_degrees
)


class RadialClimateAuditTest(unittest.TestCase):
    def test_active_canonical_temperature_graphs_use_1_to_20_north_south_latitude(self):
        root=ROOT/"neoforge/src/main/resources/data/realscale/worldgen/noise_settings"
        for preset in ("tva_natural_smoothed","tva_alpine_smoothed",
                       "tva_canyon_smoothed","usgs_big_sur_v2"):
            with self.subTest(preset=preset):
                settings=root/(preset+".json")
                profile=meridional_profile_from_noise(settings)
                self.assertIsNotNone(profile)
                self.assertEqual(profile["equator_z"],0)
                self.assertEqual(profile["pole_distance"],500098)
                self.assertAlmostEqual(meridional_latitude_degrees(-500098,profile),90)
                self.assertAlmostEqual(meridional_latitude_degrees(500098,profile),-90)
                self.assertAlmostEqual(meridional_latitude_degrees(0,profile),0)
                self.assertIsNone(radial_profile_from_noise(settings),
                    "No playable surface profile may use radial climate latitude")

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

    def test_two_crest_equal_area_geometry(self):
        q=8192
        radial={"center_x":0,"center_z":0,"pole_distance":q,
                "area_balanced":True,"balanced":True}
        self.assertAlmostEqual(radial_latitude_degrees(0,0,radial),0)
        # Disk area quarters: at r=Q (quarter area) latitude is 90.
        self.assertAlmostEqual(radial_latitude_degrees(q,0,radial),90,delta=0.05)

    def test_canonical_area_quarters_and_single_pole(self):
        # Use floating world coordinates to test mathematical parity independently
        # of Minecraft's integer coordinate rounding.
        q=8192
        profile={"center_x":0,"center_z":0,"pole_distance":q,
                 "area_balanced":True}
        self.assertAlmostEqual(radial_latitude_degrees(q,0,profile),90)
        self.assertAlmostEqual(radial_latitude_degrees(math.sqrt(2)*q,0,profile),0,places=8)
        self.assertAlmostEqual(radial_latitude_degrees(math.sqrt(3)*q,0,profile),90,places=8)
        self.assertAlmostEqual(radial_latitude_degrees(2*q,0,profile),0,places=8)
        profile["single_pole_area"]=True
        self.assertAlmostEqual(radial_latitude_degrees(math.sqrt(2)*q,0,profile),90,places=8)
        self.assertAlmostEqual(radial_latitude_degrees(q,0,profile),45,places=8)
        self.assertAlmostEqual(radial_latitude_degrees(2*q,0,profile),0,places=8)

    def test_legacy_balanced_mode_preserved(self):
        q=8192
        p={"center_x":0,"center_z":0,"pole_distance":q,"balanced":True}
        self.assertAlmostEqual(radial_latitude_degrees(q/2,0,p),90*(0.5**1.15))
        p["balanced"]=False
        self.assertAlmostEqual(radial_latitude_degrees(q/2,0,p),45)


if __name__=="__main__":
    unittest.main()
