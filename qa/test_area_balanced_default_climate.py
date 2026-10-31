import json
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
NOISE=ROOT/"neoforge/src/main/resources/data/realscale/worldgen/noise_settings"
SOURCE=ROOT/"neoforge/src/main/java/org/realscale/neoforge"

SURFACE_PROFILES=(
    "tva_natural_smoothed",
    "tva_alpine_smoothed", "tva_canyon_smoothed",
    "tva_alpine_fitted", "tva_canyon_fitted",
    "tva_dune_fitted", "tva_glacial_fitted",
    "tva_karst_fitted", "tva_volcanic_fitted",
    "usgs_big_sur_v2",
)

class NorthSouthDefaultClimateTest(unittest.TestCase):
    def test_every_shipped_surface_profile_uses_signed_north_south_latitude(self):
        for name in SURFACE_PROFILES:
            with self.subTest(name=name):
                d=json.loads((NOISE/(name+".json")).read_text())
                temp=d["noise_router"]["temperature"]
                self.assertEqual(temp["type"],"realscale:climate_budget")
                def nodes(x):
                    if isinstance(x,dict):
                        yield x
                        for value in x.values():
                            yield from nodes(value)
                    elif isinstance(x,list):
                        for value in x:
                            yield from nodes(value)
                latitude=[x for x in nodes(temp) if x.get("type")=="realscale:latitude_temperature"]
                radial=[x for x in nodes(temp) if x.get("type")=="realscale:radial_latitude_temperature"]
                self.assertEqual(len(latitude),1,"must contain exactly one signed latitude node")
                self.assertEqual(radial,[],"radial latitude must not drive a shipped surface profile")
                lat=latitude[0]
                self.assertEqual(lat["equator_z"],0)
                self.assertEqual(lat["pole_distance"],8192)
                self.assertEqual(lat["strength"],0.58)
                self.assertEqual(d["sea_level"],63)
                self.assertEqual(d["noise_router"]["final_density"]["type"],
                                 "realscale:grand_overworld_caverns")

    def test_natural_preserves_mountain_basin_and_climate_budget_layers(self):
        d=json.loads((NOISE/"tva_natural_smoothed.json").read_text())
        temp=d["noise_router"]["temperature"]
        self.assertEqual(temp["adjusted"]["type"],"realscale:mountain_basin_temperature")
        self.assertEqual(temp["adjusted"]["warmth"],0.1)
        self.assertEqual(temp["max_cooling"],0.66)
        self.assertEqual(temp["max_warming"],0.34)

    def test_older_radial_math_remains_available_for_explicit_opt_in_modes(self):
        source=(SOURCE/"RadialLatitudeTemperatureDensity.java").read_text()
        self.assertIn('optionalFieldOf("area_balanced",false)',source)
        self.assertIn("GeoMath.balancedRadialLatitudeDeg",source)
        self.assertIn("GeoMath.areaBalancedRadialLatitudeDeg",source)

if __name__=="__main__":
    unittest.main()
