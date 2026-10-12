#!/usr/bin/env python3
"""USGS Big Sur v2 sampling regression. Do not change normal preset geometry."""
import json
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
GEO=ROOT/"neoforge/src/main/resources/data/realscale/worldgen"

class UsgsBigSurIsolation(unittest.TestCase):
    def test_realism_and_georeal_keep_original_16_block_sampling(self):
        for mode in ("realism_max","georeal_ultimate"):
            with self.subTest(mode=mode):
                settings=json.loads((GEO/"noise_settings"/(mode+".json")).read_text())
                self.assertEqual(settings["noise"]["size_horizontal"],4)
                self.assertEqual(settings["sea_level"],63)

    def test_old_usgs_worldgen_does_not_silently_change(self):
        old=json.loads((GEO/"noise_settings/usgs_big_sur.json").read_text())
        density=json.loads((GEO/"density_function/usgs_big_sur/terrain_density.json").read_text())
        self.assertEqual(old["noise"]["size_horizontal"],4)
        self.assertEqual(density["type"],"realscale:dem_density")
        self.assertEqual(density["blend_meters"],96)

    def test_new_usgs_uses_dedicated_measured_v2_density(self):
        settings=json.loads((GEO/"noise_settings/usgs_big_sur_v2.json").read_text())
        density=json.loads((GEO/"density_function/usgs_big_sur_v2/terrain_density.json").read_text())
        self.assertEqual(settings["noise"]["size_horizontal"],2)
        self.assertEqual(settings["sea_level"],63)
        self.assertEqual(settings["noise"]["height"],2096)
        self.assertEqual(settings["noise_router"]["final_density"]["input"],
                         "realscale:usgs_big_sur_v2/terrain_density")
        self.assertEqual(settings["noise_router"]["depth"],"realscale:usgs_big_sur_v2/terrain_density")
        self.assertEqual(density["type"],"realscale:dem_density_seamless")
        self.assertEqual(density["blend_meters"],768)
        reg=(ROOT/"neoforge/src/main/java/org/realscale/neoforge/RealScaleMod.java").read_text()
        self.assertIn('"dem_density_seamless"',reg)

    def test_usgs_v2_does_not_turn_all_big_sur_highlands_into_snow(self):
        old=json.loads((GEO/"noise_settings/usgs_big_sur.json").read_text())
        new=json.loads((GEO/"noise_settings/usgs_big_sur_v2.json").read_text())
        def unconditional_snow_above_550(obj):
            found=[]
            def walk(node):
                if isinstance(node,list):
                    for child in node:walk(child)
                elif isinstance(node,dict):
                    cond=node.get("if_true",{})
                    run=node.get("then_run",{})
                    if (isinstance(cond,dict) and isinstance(run,dict)
                            and cond.get("type")=="minecraft:y_above"
                            and cond.get("anchor")=={"absolute":550}
                            and run.get("result_state",{}).get("Name")=="minecraft:snow_block"):
                        found.append(node)
                    for child in node.values():walk(child)
            walk(obj["surface_rule"])
            return found
        self.assertEqual(len(unconditional_snow_above_550(old)),1)
        self.assertEqual(unconditional_snow_above_550(new),[])
        self.assertIn("minecraft:snowy_plains",json.dumps(new["surface_rule"]))

    def test_raw_dem_source_remains_verified_and_unchanged(self):
        source=(ROOT/"neoforge/src/main/java/org/realscale/neoforge/RealDemTile.java").read_text()
        self.assertIn('SHA256="d43f41f4343fb9606e9647bcdd1ebcd667ec5b9f0334ffc6fef007f7fef19557"',source)
        self.assertIn("cellMeters!=32.0",source)

if __name__=="__main__":
    unittest.main()
