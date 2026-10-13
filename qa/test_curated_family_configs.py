#!/usr/bin/env python3
"""Curated 4-way menu and independent YACL family settings, legacy-safe."""
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "neoforge/src/main/resources"
TAG = RES / "data/minecraft/tags/worldgen/world_preset"
WORLD = RES / "data/realscale/worldgen"
JAVA = ROOT / "neoforge/src/main/java/org/realscale/neoforge/RealScaleTerrainLabClient.java"

CURATED = (
    "realism_max", "himalayan", "grand_canyon", "usgs_big_sur_v2"
)
LEGACY = (
    "georeal_ultimate", "continental", "realistic_mountains",
    "ultimate", "usgs_big_sur"
)


class ConsolidationTests(unittest.TestCase):
    def test_four_curated_choices_even_in_extended_menu(self):
        expected = ["realscale:" + n for n in CURATED]
        for menu in ("normal", "extended"):
            with self.subTest(menu=menu):
                data = json.loads((TAG / (menu + ".json")).read_text())
                self.assertIs(data["replace"], False)
                self.assertEqual(data["values"], expected)
                self.assertEqual(len(set(data["values"])), 4)

    def test_hiding_old_algorithms_does_not_delete_any_worldgen_id(self):
        for name in CURATED + LEGACY:
            with self.subTest(name=name):
                world = json.loads((WORLD / "world_preset" / (name + ".json")).read_text())
                self.assertEqual(
                    world["dimensions"]["minecraft:overworld"]["generator"]["settings"],
                    "realscale:" + name,
                )
                self.assertTrue((WORLD / "noise_settings" / (name + ".json")).is_file())
                self.assertTrue((WORLD / "density_function" / name / "terrain_density.json").is_file())

    def test_real_world_dem_remains_separate_read_only_and_visible(self):
        from_tag = json.loads((TAG / "normal.json").read_text())["values"]
        self.assertIn("realscale:usgs_big_sur_v2", from_tag)
        self.assertNotIn("realscale:usgs_big_sur", from_tag)
        client = JAVA.read_text(encoding="utf-8")
        self.assertIn("USGS Big Sur v2", client)
        self.assertIn("measured/read-only", client)

    def test_three_independent_saved_family_slots_and_export_sync(self):
        client = JAVA.read_text(encoding="utf-8")
        for name in ("landscape", "alpine", "canyon"):
            self.assertIn('d.profiles.get("' + name + '")', client)
            self.assertIn('case "' + name + '"', client)
        self.assertEqual(client.count('.category(familyCategory('), 3)
        self.assertIn("Map<String,FamilySettings> profiles", client)
        self.assertIn("FamilySettings.fromDraft(this)", client)
        self.assertIn("profiles.get(preset).copyTo(this)", client)
        self.assertIn("data.initializeProfiles()", client)
        self.assertIn(".save(d::save)", client)
        self.assertIn("Shared Climate & Latitude / Export", client)
        self.assertNotIn("Restart/Build Required", client)

    def test_family_presets_have_different_reasonable_defaults(self):
        client = JAVA.read_text(encoding="utf-8")
        self.assertIn("f.climatePercent=60; f.valleyPercent=15", client)
        self.assertIn("f.climatePercent=70; f.valleyPercent=25", client)
        self.assertIn("f.climatePercent=35; f.valleyPercent=35", client)
        self.assertIn("f.riverGrade=3;", client)
        self.assertIn("f.erosionPercent=18;", client)
        self.assertIn("f.ridgePercent=18;", client)
        self.assertIn("(!lithostitchedGeology || blendPercent>0)", client)

    def test_legacy_yacl_draft_fields_and_new_exporter_remain_compatible(self):
        client = JAVA.read_text(encoding="utf-8")
        exporter = (ROOT / "scripts/export_terrain_lab_datapack.py").read_text()
        self.assertIn('String preset = "landscape"', client)
        for field in ("blendPercent", "reliefPercent", "elevation",
                      "caves", "riverGrade", "inland"):
            self.assertIn(field + "=", client)
            self.assertIn('"' + field + '"', exporter)
        # The independent family map is extra metadata. The matching
        # exporter still reads a single active flattened draft.
        self.assertIn('preset=draft["preset"]', exporter)


if __name__ == "__main__":
    unittest.main()
