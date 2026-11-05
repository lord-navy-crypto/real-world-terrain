#!/usr/bin/env python3
"""Regression checks for the in-game Geographic Climate Inspector."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "neoforge/src/main/java/org/realscale/neoforge"


class GeographicClimateInspectorTests(unittest.TestCase):
    def test_pause_menu_button_opens_live_inspector(self):
        source = (JAVA / "GeographicClimateInspectorClient.java").read_text()
        self.assertIn("ScreenEvent.Init.Post", source)
        self.assertIn("instanceof PauseScreen", source)
        self.assertIn("Geographic Climate Report", source)
        self.assertIn("new GeographicClimateInspectorScreen(pause)", source)

    def test_uses_fixed_1_to_20_geographic_model_not_generator_node_discovery(self):
        source = (JAVA / "GeographicClimateInspectorClient.java").read_text()
        self.assertIn("GeoMath.CLIMATE_EQUATOR_Z", source)
        self.assertIn('return "1:20";', source)
        self.assertIn("GeoMath.CLIMATE_POLE_DISTANCE_BLOCKS", source)
        self.assertIn("Level.OVERWORLD", source)
        self.assertNotIn("getSingleplayerServer", source)
        self.assertNotIn("getChunkSource().getGenerator()", source)
        self.assertNotIn("noiseRouter().temperature()", source)
        self.assertNotIn("RadialLatitudeTemperatureDensity", source)
        self.assertNotIn("1:1", source)

    def test_no_generator_node_required_or_misleading_failure_message(self):
        client = (JAVA / "GeographicClimateInspectorClient.java").read_text()
        screen = (JAVA / "GeographicClimateInspectorScreen.java").read_text()
        self.assertIn("No world-generation node is required", screen)
        self.assertNotIn("No RealScale latitude node is active", screen)
        self.assertNotIn("Active generator metadata", screen)
        self.assertIn("The 1:20 geographic latitude report applies to the Overworld only.", screen)

    def test_report_includes_coordinates_biome_latitude_and_chat_announcement(self):
        source = (JAVA / "GeographicClimateInspectorScreen.java").read_text()
        for field in (
            "Current position (X / Y / Z)",
            "Current biome",
            "Calculated latitude",
            "Hemisphere",
            "Latitude climate belt",
            "Latitude scale",
            "Announce to Chat",
            "displayClientMessage",
        ):
            with self.subTest(field=field):
                self.assertIn(field, source)

    def test_longitude_is_not_fabricated_from_flat_map_x(self):
        source = (JAVA / "GeographicClimateInspectorScreen.java").read_text()
        self.assertIn("X is the east–west map coordinate, not a true longitude.", source)
        self.assertIn("not a Köppen classification", source)

    def test_five_latitude_belts_and_1_to_20_math_are_present(self):
        math_source = (JAVA / "GeographicClimateMath.java").read_text()
        smoke = (ROOT / "neoforge/src/test/java/org/realscale/neoforge/GeographicClimateMathSmoke.java").read_text()
        geo_math = (JAVA / "GeoMath.java").read_text()
        for threshold in ("23.4366", "35.0", "55.0", "66.5634"):
            self.assertIn(threshold, math_source)
        for belt in ("TROPICAL", "SUBTROPICAL", "TEMPERATE", "SUBPOLAR", "POLAR"):
            self.assertIn(belt, smoke)
        self.assertIn("CLIMATE_LATITUDE_COMPRESSION = 20", geo_math)
        self.assertIn("CLIMATE_POLE_DISTANCE_BLOCKS", smoke)
        self.assertIn("negative Z is northern latitude", smoke)
        self.assertIn("positive Z is southern latitude", smoke)


if __name__ == "__main__":
    unittest.main()
