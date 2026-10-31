import json
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
NOISE=ROOT/"neoforge/src/main/resources/data/realscale/worldgen/noise_settings"
SOURCE=ROOT/"neoforge/src/main/java/org/realscale/neoforge"
class AreaBalancedDefaultClimateTest(unittest.TestCase):
    def test_other_default_profiles_preserve_area_balance(self):
        for name in ("tva_alpine_smoothed", "tva_canyon_smoothed", "usgs_big_sur_v2"):
            with self.subTest(name=name):
                d=json.loads((NOISE/(name+".json")).read_text())
                temp=d["noise_router"]["temperature"]
                self.assertEqual(temp["type"],"realscale:climate_budget")
                adjusted=temp["adjusted"]
                if name!="usgs_big_sur_v2":
                    self.assertEqual(adjusted["type"],"realscale:mountain_basin_temperature")
                    self.assertGreater(adjusted["warmth"],0)
                    radial=adjusted["base"]
                else:
                    radial=adjusted
                self.assertEqual(radial["type"],"realscale:radial_latitude_temperature")
                self.assertTrue(radial["area_balanced"])
                self.assertTrue(radial["balanced"])
                self.assertEqual(radial["pole_distance"],8192)
                self.assertGreater(radial["altitude_cooling"],0)
                self.assertEqual(radial["base"]["type"],"minecraft:add")
                self.assertEqual(radial["base"]["argument1"],temp["original"])
                self.assertEqual(d["sea_level"],63)
                self.assertEqual(d["noise_router"]["final_density"]["type"],
                                 "realscale:grand_overworld_caverns")
    def test_natural_defaults_to_signed_north_south_latitude(self):
        d=json.loads((NOISE/"tva_natural_smoothed.json").read_text())
        temp=d["noise_router"]["temperature"]
        self.assertEqual(temp["type"], "realscale:climate_budget")
        self.assertEqual(temp["adjusted"]["type"], "realscale:mountain_basin_temperature")
        latitude=temp["adjusted"]["base"]
        self.assertEqual(latitude["type"], "realscale:latitude_temperature")
        self.assertEqual(latitude["equator_z"], 0)
        self.assertEqual(latitude["pole_distance"], 8192)
        self.assertEqual(latitude["strength"], 0.58)
        self.assertEqual(latitude["base"]["type"], "minecraft:add")
        self.assertEqual(latitude["base"]["argument1"], temp["original"])
        self.assertEqual(d["noise_router"]["final_density"]["type"],
                         "realscale:grand_overworld_caverns")
        self.assertEqual(d["sea_level"], 63)

    def test_older_radial_modes_keep_unmodified_math(self):
        source=(SOURCE/"RadialLatitudeTemperatureDensity.java").read_text()
        self.assertIn('optionalFieldOf("area_balanced",false)',source)
        self.assertIn("GeoMath.balancedRadialLatitudeDeg",source)
        self.assertIn("GeoMath.areaBalancedRadialLatitudeDeg",source)
if __name__=="__main__":unittest.main()
