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

    def test_reads_active_integrated_world_not_next_world_draft(self):
        source = (JAVA / "GeographicClimateInspectorClient.java").read_text()
        self.assertIn("getSingleplayerServer()", source)
        self.assertIn("getChunkSource().getGenerator()", source)
        self.assertIn("generatorSettings()", source)
        self.assertIn("noiseRouter().temperature()", source)
        self.assertIn("LatitudeTemperatureDensity", source)
        self.assertIn("RadialLatitudeTemperatureDensity", source)
        self.assertNotIn("Draft.read()", source)

    def test_all_known_realscale_temperature_wrappers_are_followed(self):
        source = (JAVA / "GeographicClimateInspectorClient.java").read_text()
        for wrapper in (
            "ClimateBudgetDensity",
            "MountainBasinTemperatureDensity",
            "BasinTemperatureDensity",
            "ValleyTemperatureDensity",
            "DensityFunctions.HolderHolder",
        ):
            with self.subTest(wrapper=wrapper):
                self.assertIn(wrapper, source)

    def test_report_includes_coordinates_biome_latitude_and_chat_announcement(self):
        source = (JAVA / "GeographicClimateInspectorScreen.java").read_text()
        for field in (
            "Current position (X / Y / Z)",
            "Current biome",
            "Calculated latitude",
            "Hemisphere",
            "Astronomical climate belt",
            "Active latitude mode",
            "Announce to Chat",
            "displayClientMessage",
        ):
            with self.subTest(field=field):
                self.assertIn(field, source)

    def test_longitude_is_not_fabricated_from_flat_map_x(self):
        source = (JAVA / "GeographicClimateInspectorScreen.java").read_text()
        self.assertIn("X is the east–west map coordinate, not a true longitude.", source)
        self.assertIn("not a Köppen climate classification", source)

    def test_belt_math_and_smoke_test_are_present(self):
        math_source = (JAVA / "GeographicClimateMath.java").read_text()
        smoke = (ROOT / "neoforge/src/test/java/org/realscale/neoforge/GeographicClimateMathSmoke.java").read_text()
        self.assertIn("23.4366", math_source)
        self.assertIn("66.5634", math_source)
        self.assertIn("TROPICAL", smoke)
        self.assertIn("TEMPERATE", smoke)
        self.assertIn("POLAR", smoke)


if __name__ == "__main__":
    unittest.main()
