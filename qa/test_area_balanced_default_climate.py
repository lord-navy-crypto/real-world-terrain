import json
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
NOISE=ROOT/"neoforge/src/main/resources/data/realscale/worldgen/noise_settings"
SOURCE=ROOT/"neoforge/src/main/java/org/realscale/neoforge"

class ZonalDefaultClimateTest(unittest.TestCase):
    def test_all_four_default_profiles_use_z_axis_latitude(self):
        expected={
            "tva_natural_smoothed":(0,0),
            "tva_alpine_smoothed":(0,0),
            "tva_canyon_smoothed":(0,0),
            # Project's documented Big Sur source anchor: 36.24 N, 121.81 W.
            "usgs_big_sur_v2":(4027458,13559827),
        }
        for name,(equator_z,prime_meridian_x) in expected.items():
            with self.subTest(name=name):
                settings=json.loads((NOISE/(name+".json")).read_text())
                temperature=settings["noise_router"]["temperature"]
                self.assertEqual(temperature["type"],"realscale:climate_budget")
                adjusted=temperature["adjusted"]
                if name!="usgs_big_sur_v2":
                    self.assertEqual(adjusted["type"],"realscale:mountain_basin_temperature")
                    self.assertGreater(adjusted["warmth"],0)
                    latitude=adjusted["base"]
                else:
                    latitude=adjusted
                self.assertEqual(latitude["type"],"realscale:latitude_temperature")
                self.assertEqual(latitude["equator_z"],equator_z)
                self.assertEqual(latitude["prime_meridian_x"],prime_meridian_x)
                self.assertEqual(latitude["pole_distance"],10001966)
                self.assertTrue(latitude["balanced"])
                self.assertTrue(latitude["clamp_at_poles"])
                self.assertGreater(latitude["altitude_cooling"],0)
                self.assertEqual(latitude["base"]["type"],"minecraft:add")
                self.assertEqual(latitude["base"]["argument1"],temperature["original"])
                self.assertEqual(settings["sea_level"],63)
                self.assertEqual(settings["noise_router"]["final_density"]["type"],
                                 "realscale:grand_overworld_caverns")

    def test_default_temperature_graph_does_not_use_radial_geometry(self):
        for name in ("tva_natural_smoothed","tva_alpine_smoothed",
                     "tva_canyon_smoothed","usgs_big_sur_v2"):
            with self.subTest(name=name):
                settings=json.loads((NOISE/(name+".json")).read_text())
                self.assertNotIn("radial_latitude_temperature",
                                 json.dumps(settings["noise_router"]["temperature"]))

    def test_concentric_experiment_and_historical_profiles_are_preserved(self):
        radial=(SOURCE/"RadialLatitudeTemperatureDensity.java").read_text()
        math=(SOURCE/"GeoMath.java").read_text()
        self.assertIn("GeoMath.areaBalancedRadialLatitudeDeg",radial)
        self.assertIn("GeoMath.singlePoleAreaRadialLatitudeDeg",radial)
        self.assertIn("static double radialLatitudeDeg",math)
        self.assertIn("static double areaBalancedRadialLatitudeDeg",math)
        for name in ("tva_pr25_natural_climate","tva_pr25_alpine_climate",
                     "tva_pr25_canyon_climate","tva_pr25_measured_climate"):
            settings=json.loads((NOISE/(name+".json")).read_text())
            self.assertIn("radial_latitude_temperature",
                          json.dumps(settings["noise_router"]["temperature"]))

if __name__=="__main__":
    unittest.main()
