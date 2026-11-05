#!/usr/bin/env python3
"""RealScale fixed WGS84 1:20 climate latitude integration tests."""
import importlib.util
import json
from pathlib import Path
import shutil
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("terrain_latitude", ROOT / "scripts/terrain_lab.py")
lab = importlib.util.module_from_spec(spec)
spec.loader.exec_module(lab)


class LatitudeClimateTests(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.root = Path(tmp.name)
        source = ROOT / "neoforge/src/main/resources"
        for path in (
            "data/realscale/worldgen",
            "data/minecraft/tags/worldgen/world_preset",
            "assets/minecraft/lang",
        ):
            shutil.copytree(source / path, self.root / path)

    def make(self, **opts):
        return lab.files_for(self.root, "landscape", 1.0, 0, 50, 6, **opts)

    def get_temperature(self, outputs, name):
        return outputs[lab.GEO_ROOT / "density_function/terrain_lab" / name / "biome_temperature.json"]

    def test_fixed_1_20_uses_one_equator_and_wgs84_quarter_meridian(self):
        name, options, outputs = self.make(
            latitude="fixed_1_20", latitude_strength=70, equator_z=-512
        )
        temperature = self.get_temperature(outputs, name)
        self.assertEqual(temperature["type"], "realscale:latitude_temperature")
        self.assertEqual(temperature["equator_z"], 0)
        self.assertEqual(temperature["pole_distance"], 500_098)
        self.assertEqual(temperature["strength"], 0.7)
        self.assertEqual(options["latitude"], "fixed_1_20")
        self.assertEqual(options["equator_z"], 0)
        noise = outputs[lab.GEO_ROOT / "noise_settings" / (name + ".json")]
        self.assertEqual(noise["noise_router"]["temperature"],
                         f"realscale:terrain_lab/{name}/biome_temperature")
        self.assertEqual(noise["sea_level"], 63)

    def test_every_historical_mode_collapses_to_identical_fixed_model(self):
        aliases = (
            "earth", "preview", "radial_earth", "radial_preview",
            "radial_balanced", "radial_equal_area",
            "radial_equal_area_single_pole",
        )
        canonical = self.make(latitude="fixed_1_20", latitude_strength=55)
        for mode in aliases:
            with self.subTest(mode=mode):
                result = self.make(
                    latitude=mode, latitude_strength=55,
                    equator_z=-512, center_x=1024, cold_limit=60,
                )
                self.assertEqual(result, canonical)
                profile = self.get_temperature(result[2], result[0])
                self.assertEqual(profile["type"], "realscale:latitude_temperature")
                self.assertEqual(profile["pole_distance"], 500_098)
                self.assertEqual(profile["equator_z"], 0)

    def test_strength_zero_is_the_single_disable_switch(self):
        disabled = self.make(latitude="fixed_1_20", latitude_strength=0)
        off = self.make(latitude="off", latitude_strength=70)
        self.assertEqual(disabled, off)
        self.assertNotIn("latitude", disabled[1])

    def test_basin_warming_wraps_the_fixed_meridional_node(self):
        name, options, outputs = self.make(
            latitude="fixed_1_20", latitude_strength=80,
            climate=65, valley=25, basin=30,
        )
        temperature = self.get_temperature(outputs, name)
        self.assertEqual(temperature["type"], "realscale:basin_temperature")
        self.assertEqual(temperature["warmth"], 0.105)
        self.assertEqual(temperature["radius"], 192)
        self.assertEqual(temperature["height_signal"],
                         f"realscale:terrain_lab/{name}/height_signal")
        latitude = temperature["base"]["base"]
        self.assertEqual(latitude["type"], "realscale:latitude_temperature")
        self.assertEqual(latitude["equator_z"], 0)
        self.assertEqual(latitude["pole_distance"], 500_098)
        self.assertEqual(latitude["strength"], 0.8)
        self.assertEqual(latitude["base"]["type"], "minecraft:add")
        self.assertEqual(options["basin"], 30)

    def test_legacy_yacl_draft_is_normalized_when_imported(self):
        draft = self.root / "legacy-latitude-draft.json"
        draft.write_text(json.dumps({
            "preset": "alpine", "blendPercent": 25, "reliefPercent": 100,
            "elevation": 0, "caves": 50, "riverGrade": 6, "inland": True,
            "ridgePercent": 0, "erosionPercent": 0, "strataPercent": 0,
            "climatePercent": 65, "valleyPercent": 0,
            "latitudeMode": "radial_equal_area", "latitudeStrength": 70,
            "equatorZ": -512, "radialCenterX": 2048, "coldLimitPercent": 60,
            "basinPercent": 20,
        }))
        before = set((self.root / lab.GEO_ROOT / "noise_settings").glob("lab_alpine_*.json"))
        self.assertEqual(lab.main(["--root", str(self.root), "--from-yacl", str(draft)]), 0)
        after = set((self.root / lab.GEO_ROOT / "noise_settings").glob("lab_alpine_*.json"))
        self.assertEqual(len(after - before), 1)
        name = (after - before).pop().stem
        temperature = lab.jread(
            self.root,
            lab.GEO_ROOT / "density_function/terrain_lab" / name / "biome_temperature.json",
        )
        self.assertEqual(temperature["type"], "realscale:basin_temperature")
        latitude = temperature["base"]
        self.assertEqual(latitude["type"], "realscale:latitude_temperature")
        self.assertEqual(latitude["equator_z"], 0)
        self.assertEqual(latitude["pole_distance"], 500_098)

    def test_old_presets_and_measured_dem_are_not_silently_rewritten(self):
        for old in lab.PRESETS:
            with self.subTest(preset=old):
                with self.assertRaises(ValueError):
                    lab.files_for(self.root, old, 1.0, 0, 50, 6,
                                  latitude="fixed_1_20")
        with self.assertRaises(ValueError):
            lab.files_for(self.root, "usgs_big_sur", 1.0, 0, 50, 6)

    def test_invalid_latitude_strength_and_basin_settings_are_rejected(self):
        invalid = (
            {"latitude": "planet"},
            {"latitude_strength": 101},
            {"latitude_strength": -5},
            {"basin": -1},
            {"basin": 101},
            {"latitude": "fixed_1_20", "latitude_strength": "70"},
        )
        for values in invalid:
            with self.subTest(values=values), self.assertRaises(ValueError):
                self.make(**values)

    def test_live_report_and_worldgen_share_the_same_wgs84_math(self):
        source = ROOT / "neoforge/src/main/java/org/realscale/neoforge"
        density = (source / "LatitudeTemperatureDensity.java").read_text()
        report = (source / "GeographicClimateInspectorClient.java").read_text()
        screen = (source / "GeographicClimateInspectorScreen.java").read_text()
        ui = (source / "RealScaleTerrainLabClient.java").read_text()
        self.assertIn("GeographicClimateMath.geodeticLatitudeDeg(ctx.blockZ())", density)
        self.assertIn("GeographicClimateMath.geodeticLatitudeDeg(z)", report)
        self.assertIn("profile.latitudeAt(pos.getZ())", screen)
        self.assertIn("Fixed 1:20 Latitude Temperature Strength", ui)
        self.assertNotIn("radial_", ui)
        self.assertNotIn("Equator Z / Ring Center Z", ui)
        self.assertNotIn("Radial Cold Climate Limit", ui)

    def test_new_generator_output_never_contains_radial_nodes(self):
        for mode in ("fixed_1_20", *sorted(lab.LEGACY_LATITUDE_MODES)):
            with self.subTest(mode=mode):
                name, _, outputs = self.make(latitude=mode, latitude_strength=70)
                temperature = self.get_temperature(outputs, name)
                self.assertEqual(temperature["type"], "realscale:latitude_temperature")
                serialized = json.dumps(temperature)
                self.assertNotIn("radial_latitude_temperature", serialized)
                self.assertNotIn("climate_budget", serialized)


if __name__ == "__main__":
    unittest.main()
