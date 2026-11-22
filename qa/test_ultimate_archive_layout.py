"""Regression tests for the source-level ultimate archive layout."""
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "neoforge/src/main/resources"
CATALOG = json.loads((ROOT / "docs/PRESET_CATALOG.json").read_text(encoding="utf-8"))
ARCHIVE = ROOT / "archive/ultimate/resource-overlay"
ACTIVE_PRESETS = RES / "data/realscale/worldgen/world_preset"
ARCHIVED_PRESETS = ARCHIVE / "data/realscale/worldgen/world_preset"
ACTIVE_NOISE = RES / "data/realscale/worldgen/noise_settings"
ARCHIVED_NOISE = ARCHIVE / "data/realscale/worldgen/noise_settings"

PUBLIC_IDS = {entry["id"] for entry in CATALOG["public_profiles"]}
HIDDEN_AND_LEGACY_IDS = {
    entry["id"]
    for entry in CATALOG["hidden_profiles"] + CATALOG["legacy_profiles"]
}
CURRENT_NOISE_IDS = {"natural_automate_v2", "grand_end", "grand_nether"}


def json_ids(directory: Path) -> set[str]:
    return {path.stem for path in directory.glob("*.json")}


class UltimateArchiveLayoutTest(unittest.TestCase):
    def test_active_world_preset_source_contains_only_public_natural_automate(self):
        self.assertEqual(json_ids(ACTIVE_PRESETS), PUBLIC_IDS)

    def test_every_hidden_and_legacy_world_preset_is_in_archive(self):
        self.assertEqual(json_ids(ARCHIVED_PRESETS), HIDDEN_AND_LEGACY_IDS)
        self.assertFalse(json_ids(ACTIVE_PRESETS) & HIDDEN_AND_LEGACY_IDS)

    def test_only_three_current_noise_settings_remain_in_active_resources(self):
        self.assertEqual(json_ids(ACTIVE_NOISE), CURRENT_NOISE_IDS)

    def test_all_legacy_noise_settings_are_in_archive_without_duplicates(self):
        archived = json_ids(ARCHIVED_NOISE)
        self.assertEqual(len(archived), 31)
        self.assertFalse(archived & CURRENT_NOISE_IDS)
        self.assertEqual(len(archived | json_ids(ACTIVE_NOISE)), 34)

    def test_gradle_packages_archive_overlay_at_original_resource_paths(self):
        gradle = (ROOT / "neoforge/build.gradle").read_text(encoding="utf-8")
        self.assertIn(
            'sourceSets.main.resources.srcDir(file("../archive/ultimate/resource-overlay"))',
            gradle,
        )


if __name__ == "__main__":
    unittest.main()
