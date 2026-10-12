#!/usr/bin/env python3
"""Guards against the high-frequency, huge-relief spike regression.

Minecraft samples the horizontal noise cell in units of four blocks.
A 16-block horizontal density cell combined with rapidly varying high-gain
ridges produced visibly coarse, repeating spikes far from server spawn.
These static checks are not a substitute for real distant-chunk Anvil QA.
"""
from pathlib import Path
import json
import unittest

ROOT=Path(__file__).resolve().parents[1]
GEO=ROOT/"neoforge/src/main/resources/data/realscale/worldgen"

class HighReliefSampling(unittest.TestCase):
    def test_problematic_realistic_presets_use_finer_cells(self):
        for name in ("realism_max","georeal_ultimate","usgs_big_sur"):
            with self.subTest(name=name):
                obj=json.loads((GEO/"noise_settings"/(name+".json")).read_text())
                self.assertEqual(obj["noise"]["size_horizontal"],2)
                self.assertEqual(obj["noise"]["size_vertical"],1)
                self.assertEqual(obj["noise"]["min_y"],-64)
                self.assertEqual(obj["noise"]["height"],2096)
                self.assertEqual(obj["sea_level"],63)

    def test_major_spines_are_broad_not_dense_needles(self):
        major={
            "realism_max":{
                "mountain_axis":0.010,
                "secondary_axis":0.018,
                "erosion_field":0.008,
                "alpine_valley_axis":0.022
            },
            "georeal_ultimate":{
                "ridges":0.010,
                "secondary_ridges":0.019,
                "erosion":0.008,
                "valley_lines":0.026
            }
        }
        for mode,fields in major.items():
            for field,maximum in fields.items():
                with self.subTest(mode=mode,field=field):
                    j=json.loads((GEO/"density_function"/mode/(field+".json")).read_text())
                    self.assertEqual(j["type"],"minecraft:flat_cache")
                    self.assertLessEqual(j["argument"]["xz_scale"],maximum)
                    self.assertEqual(j["argument"]["y_scale"],0)

    def test_details_do_not_alias_8_block_density_grid(self):
        for mode,fields in {
            "realism_max":("foothill_field","mid_detail_field","micro_detail_field"),
            "georeal_ultimate":("broad_hills","mid_detail","micro_detail")
        }.items():
            for field in fields:
                with self.subTest(mode=mode,field=field):
                    j=json.loads((GEO/"density_function"/mode/(field+".json")).read_text())
                    self.assertLessEqual(j["argument"]["xz_scale"],0.08)

    def test_measured_dem_data_remains_byte_preserved(self):
        src=(ROOT/"neoforge/src/main/java/org/realscale/neoforge/RealDemTile.java").read_text()
        self.assertIn('SHA256="d43f41f4343fb9606e9647bcdd1ebcd667ec5b9f0334ffc6fef007f7fef19557"',src)
        self.assertIn("cellMeters!=32.0",src)

if __name__=="__main__":
    unittest.main()
