#!/usr/bin/env python3
"""Release exporter must work with a JAR, without a Gradle resource build."""
import json
from pathlib import Path
import sys
import tempfile
import unittest
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from export_terrain_lab_datapack import export, _draft_options


class ExporterTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.jar = self.base / "realscale.jar"
        resources = ROOT / "neoforge/src/main/resources"
        with ZipFile(self.jar, "w") as archive:
            for prefix in ("data/realscale/worldgen", "data/minecraft/tags/worldgen/world_preset",
                           "assets/minecraft/lang"):
                for file in (resources / prefix).rglob("*.json"):
                    archive.write(file, file.relative_to(resources).as_posix())
        self.draft = self.base / "realscale-terrain-lab.json"
        self.draft.write_text(json.dumps({
            "preset": "landscape", "reliefPercent": 105, "elevation": 4,
            "caves": 50, "riverGrade": 6, "inland": True,
            "blendPercent": 25, "climatePercent": 65,
            "latitudeMode": "radial_balanced", "latitudeStrength": 70,
            "coldLimitPercent": 60
        }), encoding="utf-8")
        self.output = self.base / "Custom-World.zip"

    def test_round_trip_yacl_to_standalone_datapack_and_language_pack(self):
        profile, resource = export(self.jar, self.draft, self.output)
        self.assertTrue(profile.startswith("realscale:lab_landscape_"))
        with ZipFile(self.output) as archive:
            names = set(archive.namelist())
            self.assertEqual(json.loads(archive.read("pack.mcmeta"))["pack"]["pack_format"], 48)
            stem = profile.split(":")[1]
            preset = json.loads(archive.read(
                "data/realscale/worldgen/world_preset/" + stem + ".json"))
            self.assertEqual(preset["dimensions"]["minecraft:overworld"]["generator"]["settings"], profile)
            self.assertIn("data/realscale/worldgen/noise_settings/" + stem + ".json", names)
            self.assertIn("data/realscale/worldgen/density_function/terrain_lab/" + stem + "/height_signal.json", names)
            self.assertTrue(all(not k.startswith("assets/") for k in names))
            for tag in ("normal", "extended"):
                value = json.loads(archive.read(
                    "data/minecraft/tags/worldgen/world_preset/" + tag + ".json"))
                self.assertEqual(value, {"replace": False, "values": [profile]})
        self.assertTrue(resource.exists())
        with ZipFile(resource) as archive:
            self.assertEqual(json.loads(archive.read("pack.mcmeta"))["pack"]["pack_format"], 34)
            en = json.loads(archive.read("assets/minecraft/lang/en_us.json"))
            self.assertIn("generator.realscale." + stem, en)
            self.assertFalse(any(s.startswith("data/") for s in archive.namelist()))

    def test_no_silent_overwrite(self):
        export(self.jar, self.draft, self.output)
        with self.assertRaises(FileExistsError):
            export(self.jar, self.draft, self.output)

    def test_bad_and_missing_yacl_fields_rejected(self):
        for bad in ({"preset": "landscape"},
                    {"preset": "landscape", "reliefPercent": True,
                     "elevation": 0, "caves": 50, "riverGrade": 6, "inland": True}):
            with self.subTest(bad=bad):
                self.draft.write_text(json.dumps(bad), encoding="utf-8")
                with self.assertRaises(ValueError):
                    _draft_options(self.draft)

    def test_reject_modified_usgs_measured_source(self):
        data = json.loads(self.draft.read_text(encoding="utf-8"))
        data["preset"] = "usgs_big_sur"
        self.draft.write_text(json.dumps(data), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "measured DEM"):
            export(self.jar, self.draft, self.output)
        self.assertFalse(self.output.exists())

    def test_invalid_jar_not_silently_accepted(self):
        with ZipFile(self.jar, "w"):
            pass
        with self.assertRaisesRegex(ValueError, "Not a compatible"):
            export(self.jar, self.draft, self.output)
        self.assertFalse(self.output.exists())


if __name__ == "__main__":
    unittest.main()
