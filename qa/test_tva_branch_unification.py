#!/usr/bin/env python3
"""Regression gate: older RealScale feature families stay available in TVA trunk.

This source/resource test is intentionally *not* proof of their saved-world
behavior. Live Anvil and RCON suites remain separate mandatory merge gates.
"""
import json
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/"neoforge/src/main/java/org/realscale/neoforge"
DATA=ROOT/"neoforge/src/main/resources/data/realscale/worldgen"

FAMILIES={
    "Legacy caves":("GrandOverworldCaverns.java","GrandOverworldCaveNetwork.java",
                    "CaveHabitatMath.java"),
    "Enlarged secondary dimensions":("GrandNetherCaverns.java",
                    "GrandNetherCaveNetwork.java","GrandEndDensity.java"),
    "Oceans and sky archipelagos":("SkyIslandFeature.java",
                    "SkyIslandLayout.java","SkyOceanBiomeSource.java"),
    "Ecology and transitions":("NaturalEcotoneBiomeSource.java",
                    "EcotoneMath.java","GrandEcologyFeature.java"),
    "River connectivity":("RiverChannelFeature.java","WatershedRoutingMath.java"),
    "Measured USGS terrain":("RealDemDensity.java","RealDemSeamlessDensity.java",
                    "RealDemColumnCache.java","RealDemDataSource.java"),
    "Highland geology":("MountainReliefDensity.java","MountainReliefMath.java",
                    "MountainBasinTemperatureDensity.java"),
    "Natural wonders opt-in":("GeoWonderRegionFeature.java",
                    "EarthWonderFeature.java","CaveWonderNetworkFeature.java",
                    "GeoWondersBiomeSource.java"),
}

class BranchFamilyUnificationTest(unittest.TestCase):
    def test_legacy_and_recent_production_modules_remain_present(self):
        for family,files in FAMILIES.items():
            for file in files:
                with self.subTest(family=family,module=file):
                    self.assertTrue((JAVA/file).is_file(),
                        "Mainline candidate is missing a previous subsystem: "+family+"/"+file)

    def test_sacred_settings_use_signed_1_to_20_latitude_and_keep_caves(self):
        for preset in ("tva_natural_smoothed","tva_alpine_smoothed",
                       "tva_canyon_smoothed","usgs_big_sur_v2"):
            with self.subTest(preset=preset):
                config=json.loads((DATA/"noise_settings"/(preset+".json")).read_text())
                temp=config["noise_router"]["temperature"]
                self.assertEqual(temp["type"],"realscale:climate_budget")
                if preset=="usgs_big_sur_v2":
                    # DEM mode deliberately preserves its independent relief
                    # climate rather than borrowing procedural basin sites.
                    latitude=temp["adjusted"]
                else:
                    self.assertEqual(temp["adjusted"]["type"],
                                     "realscale:mountain_basin_temperature")
                    latitude=temp["adjusted"]["base"]
                self.assertEqual(latitude["type"],
                                 "realscale:latitude_temperature")
                self.assertEqual(latitude["equator_z"],0)
                self.assertEqual(latitude["pole_distance"],500098)
                self.assertEqual(config["noise_router"]["final_density"]["type"],
                                 "realscale:grand_overworld_caverns")

    def test_only_experimental_world_has_wonders_opt_in(self):
        normal=json.loads((DATA/"world_preset"/
                         "tva_sacred_timeline_three_realms.json").read_text())
        wonder=json.loads((DATA/"world_preset"/
                         "tva_natural_wonders_experimental_three_realms.json").read_text())
        assert_key=lambda j: json.dumps(j)
        self.assertNotIn("geo_wonders_opt_in",assert_key(normal))
        self.assertIn("geo_wonders_opt_in",assert_key(wonder))
        self.assertNotIn("cross_realm_invasion",assert_key(normal))
        self.assertEqual(normal["dimensions"]["minecraft:the_nether"],
                         wonder["dimensions"]["minecraft:the_nether"])

    def test_both_area_climate_algorithms_remain_selectable(self):
        src=(JAVA/"GeoMath.java").read_text()
        codec=(JAVA/"RadialLatitudeTemperatureDensity.java").read_text()
        exporter=(ROOT/"scripts/terrain_lab.py").read_text()
        ui=(JAVA/"RealScaleTerrainLabClient.java").read_text()
        for method in ("balancedRadialLatitudeDeg",
                       "areaBalancedRadialLatitudeDeg",
                       "singlePoleAreaRadialLatitudeDeg"):
            self.assertIn(method,src)
        self.assertIn('optionalFieldOf("single_pole_area",false)',codec)
        for mode in ("radial_balanced","radial_equal_area",
                     "radial_equal_area_single_pole"):
            self.assertIn(mode,exporter)
            self.assertIn(mode,ui)

if __name__=="__main__":
    unittest.main()
