import json
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
NOISE=ROOT/"neoforge/src/main/resources/data/realscale/worldgen/noise_settings"
DENSITY=ROOT/"neoforge/src/main/resources/data/realscale/worldgen/density_function"
SOURCE=ROOT/"neoforge/src/main/java/org/realscale/neoforge"

class ZonalDefaultClimateTest(unittest.TestCase):
    PRESETS=("tva_natural_smoothed","tva_alpine_smoothed",
             "tva_canyon_smoothed","usgs_big_sur_v2")

    def latitude_node(self,settings):
        temperature=settings["noise_router"]["temperature"]
        self.assertEqual(temperature["type"],"realscale:horizontal_scale")
        self.assertEqual(temperature["scale"],20)
        temperature=temperature["base"]
        self.assertEqual(temperature["type"],"realscale:climate_budget")
        adjusted=temperature["adjusted"]
        if settings["noise_router"].get("final_density",{}).get("type")=="realscale:horizontal_scale":
            if "mountain_basin_temperature" in json.dumps(adjusted):
                self.assertEqual(adjusted["type"],"realscale:mountain_basin_temperature")
                return temperature,adjusted["base"]
        return temperature,adjusted

    def test_all_four_default_profiles_use_periodic_z_latitude_and_1_to_20_scale(self):
        expected={
            "tva_natural_smoothed":(0,False),
            "tva_alpine_smoothed":(0,False),
            "tva_canyon_smoothed":(0,False),
            "usgs_big_sur_v2":(4_027_458,False),
        }
        for name,(equator_z,clamped) in expected.items():
            with self.subTest(name=name):
                settings=json.loads((NOISE/(name+".json")).read_text())
                router=settings["noise_router"]
                for key,value in router.items():
                    if isinstance(value,(dict,str)):
                        self.assertEqual(value["type"],"realscale:horizontal_scale")
                        self.assertEqual(value["scale"],20)
                temperature,latitude=self.latitude_node(settings)
                self.assertEqual(latitude["type"],"realscale:latitude_temperature")
                self.assertEqual(latitude["equator_z"],equator_z)
                self.assertEqual(latitude["pole_distance"],10_001_966)
                self.assertFalse(latitude["clamp_at_poles"])
                self.assertTrue(latitude["balanced"])
                self.assertGreater(latitude["altitude_cooling"],0)
                self.assertEqual(settings["sea_level"],63)
                final=router["final_density"]["base"]
                self.assertEqual(final["type"],"realscale:grand_overworld_caverns")

    def test_default_temperature_graph_does_not_use_radial_geometry_or_longitude(self):
        for name in self.PRESETS:
            with self.subTest(name=name):
                settings=json.loads((NOISE/(name+".json")).read_text())
                self.assertNotIn("radial_latitude_temperature",
                                 json.dumps(settings["noise_router"]["temperature"]))
                self.assertNotIn("prime_meridian_x",
                                 json.dumps(settings["noise_router"]["temperature"]))

    def test_horizontal_scale_is_registered_and_scales_only_xz(self):
        density=(SOURCE/"HorizontalScaleDensity.java").read_text()
        math=(SOURCE/"GeoMath.java").read_text()
        mod=(SOURCE/"RealScaleMod.java").read_text()
        self.assertIn('"horizontal_scale"',mod)
        self.assertIn("scaleHorizontalCoordinate(delegate.blockX(),scale)",density)
        self.assertIn("scaleHorizontalCoordinate(delegate.blockZ(),scale)",density)
        self.assertIn("delegate.blockY()",density)
        self.assertIn("ThreadLocal.withInitial",density)
        self.assertIn("Math.multiplyExact",math)
        self.assertNotIn("longitudeDeg",math)

    def test_concentric_experiment_and_historical_profiles_are_preserved(self):
        radial=(SOURCE/"RadialLatitudeTemperatureDensity.java").read_text()
        math=(SOURCE/"GeoMath.java").read_text()
        self.assertIn("GeoMath.areaBalancedRadialLatitudeDeg",radial)
        self.assertIn("GeoMath.singlePoleAreaRadialLatitudeDeg",radial)
        self.assertIn("static double radialLatitudeDeg",math)
        self.assertIn("static double areaBalancedRadialLatitudeDeg",math)
        for name in ("tva_pr25_natural_climate","tva_pr25_alpine_climate",
                     "tva_pr25_canyon_climate","tva_pr25_measured_climate"):
            settings=json.loads((DENSITY/name/"biome_temperature.json").read_text())
            self.assertIn("radial_latitude_temperature",
                          json.dumps(settings["adjusted"]))

if __name__=="__main__":
    unittest.main()
