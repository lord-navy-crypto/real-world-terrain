#!/usr/bin/env python3
"""Test radial biome distribution auditing from saved chunk palette samples."""
import json
import sys
import tempfile
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent/"neoforge"))
from check_realism_continuity import radial_biome_audit,radial_profile_from_noise

class RadialBiomeAuditTests(unittest.TestCase):
    def test_radial_belts_report_measured_cold_hot_fractions(self):
        radial={"center_x":0,"center_z":0,"pole_distance":1000,"strength":.7}
        h={(0,0):70,(250,0):100,(550,0):280,(750,0):330,(1000,0):240}
        biomes={(0,0):"minecraft:jungle",(250,0):"minecraft:plains",
                (550,0):"minecraft:forest",(750,0):"minecraft:snowy_slopes",
                (1000,0):"minecraft:frozen_peaks"}
        result=radial_biome_audit(h,biomes,radial)
        self.assertEqual(result["equatorial_0_30"]["sample_columns"],2)
        self.assertEqual(result["middle_30_60"]["sample_columns"],1)
        self.assertEqual(result["polar_60_90"]["sample_columns"],2)
        self.assertEqual(result["polar_60_90"]["cold_biome_fraction"],1)
        self.assertEqual(result["equatorial_0_30"]["warm_biome_fraction"],.5)
        self.assertEqual(result["range_blocks"],[0,1000])

    def test_balanced_mapping_and_high_tropical_snow_warning(self):
        radial={"center_x":0,"center_z":0,"pole_distance":8192,
                "strength":.7,"balanced":True}
        heights={(i,0):300 for i in range(80)}
        biomes={(i,0):"minecraft:snowy_slopes" for i in range(80)}
        report=radial_biome_audit(heights,biomes,radial)
        self.assertEqual(report["mode"],"radial_balanced")
        self.assertEqual(report["equatorial_plateau_check"]["high_plateau_columns_y240_plus"],80)
        self.assertIsNotNone(report["equatorial_plateau_check"]["warning"])

    def test_non_linear_band_at_radius_one_third_pole(self):
        radial={"center_x":0,"center_z":0,"pole_distance":8192,
                "strength":.7,"balanced":True}
        h={(2730,0):260}
        b={(2730,0):"minecraft:plains"}
        out=radial_biome_audit(h,b,radial)
        self.assertEqual(out["equatorial_0_30"]["sample_columns"],1)

    def test_no_radial_mode_is_explicitly_reported(self):
        self.assertEqual(radial_biome_audit({}, {}, None),{"mode":"not_radial"})

    def test_reads_actual_registered_climate_function_graph(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td)
            world=root/"worldgen"
            settings=world/"noise_settings"/"sample.json"
            settings.parent.mkdir(parents=True)
            settings.write_text(json.dumps({"noise_router":{
                "temperature":"realscale:terrain_lab/test/biome_temperature"}}))
            df=world/"density_function"/"terrain_lab"/"test"/"biome_temperature.json"
            df.parent.mkdir(parents=True)
            df.write_text(json.dumps({"type":"realscale:climate_budget",
                "adjusted":{"type":"realscale:radial_latitude_temperature",
                "center_x":200,"center_z":-500,"pole_distance":16384,"strength":.7},
                "original":{"type":"minecraft:shifted_noise"}}))
            result=radial_profile_from_noise(settings)
            self.assertEqual(result["center_x"],200)
            self.assertEqual(result["pole_distance"],16384)
            self.assertEqual(result["center_z"],-500)

if __name__=="__main__":
    unittest.main()
