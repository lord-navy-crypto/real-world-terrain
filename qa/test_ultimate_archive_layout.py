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


def regular_json_ids(directory: Path) -> set[str]:
    return {path.stem for path in directory.glob("*.json") if not path.is_symlink()}


def symlink_json_ids(directory: Path) -> set[str]:
    return {path.stem for path in directory.glob("*.json") if path.is_symlink()}


def assert_compatibility_pointers(test: unittest.TestCase, active: Path, archived: Path):
    for pointer in active.glob("*.json"):
        if pointer.is_symlink():
            test.assertEqual(pointer.resolve(), (archived / pointer.name).resolve(), pointer.name)
            test.assertTrue(pointer.resolve().is_file(), pointer.name)


class UltimateArchiveLayoutTest(unittest.TestCase):
    def test_only_natural_automate_is_a_regular_active_preset(self):
        self.assertEqual(regular_json_ids(ACTIVE_PRESETS), PUBLIC_IDS)

    def test_all_hidden_and_legacy_presets_are_archived_with_compatibility_pointers(self):
        self.assertEqual(regular_json_ids(ARCHIVED_PRESETS), HIDDEN_AND_LEGACY_IDS)
        self.assertEqual(symlink_json_ids(ACTIVE_PRESETS), HIDDEN_AND_LEGACY_IDS)
        assert_compatibility_pointers(self, ACTIVE_PRESETS, ARCHIVED_PRESETS)

    def test_only_three_current_noise_settings_remain_as_active_source_files(self):
        self.assertEqual(regular_json_ids(ACTIVE_NOISE), CURRENT_NOISE_IDS)

    def test_all_31_legacy_noise_settings_are_archived_with_compatibility_pointers(self):
        archived = regular_json_ids(ARCHIVED_NOISE)
        self.assertEqual(len(archived), 31)
        self.assertEqual(symlink_json_ids(ACTIVE_NOISE), archived)
        self.assertEqual(len(archived | regular_json_ids(ACTIVE_NOISE)), 34)
        assert_compatibility_pointers(self, ACTIVE_NOISE, ARCHIVED_NOISE)

    def test_archive_contains_manifest_and_performance_evidence(self):
        manifest = json.loads((ROOT / "archive/ultimate/MANIFEST.json").read_text(encoding="utf-8"))
        performance = json.loads(
            (ROOT / "archive/ultimate/performance/benchmark-summary.json").read_text(encoding="utf-8")
        )
        self.assertEqual(len(manifest["world_presets"]), 40)
        self.assertEqual(len(manifest["noise_settings"]), 31)
        self.assertEqual(performance["measurement_policy"], "one paired sample per profile; not statistically stable")

    def test_release_workflow_checks_archive_resources_in_the_built_jar(self):
        workflow = (ROOT / ".github/workflows/public-eight-mode-test-jar.yml").read_text(encoding="utf-8")
        self.assertIn("'archive/ultimate/**'", workflow)
        self.assertIn("ultimate archive registry resources", workflow)


if __name__ == "__main__":
    unittest.main()
