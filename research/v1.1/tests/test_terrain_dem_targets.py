import importlib.util
import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "data" / "terrain_dem_targets.json"
ACQUIRE = ROOT / "tools" / "acquire_terrain_dem.py"
SPEC = importlib.util.spec_from_file_location("acquire_terrain_dem", ACQUIRE)
ACQUISITION = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(ACQUISITION)


def tile_bounds(tile):
    match = re.fullmatch(r"([NS])(\d{2})_00_([EW])(\d{3})_00", tile)
    if not match:
        raise ValueError(tile)
    lat = int(match.group(2)) * (1 if match.group(1) == "N" else -1)
    lon = int(match.group(4)) * (1 if match.group(3) == "E" else -1)
    south, west = lat, lon
    if match.group(1) == "S":
        south = lat
    if match.group(3) == "W":
        west = lon
    return west, south, west + 1, south + 1


class RealLandscapeTargetManifestTests(unittest.TestCase):
    def test_manifest_has_six_distinct_data_fitted_landscape_families(self):
        manifest = ACQUISITION.read_manifest(MANIFEST)
        targets = manifest["targets"]
        self.assertEqual(len(targets), 6)
        self.assertEqual(
            {target["family"] for target in targets},
            {"alpine", "canyon", "glacial", "volcanic", "karst", "dune"},
        )
        self.assertEqual(len({target["id"] for target in targets}), 6)
        self.assertEqual(len({target["tile"] for target in targets}), 6)
        self.assertEqual(manifest["default_mode"]["id"], "natural")
        self.assertFalse(manifest["default_mode"]["data_fit_required"])

    def test_each_study_window_is_inside_its_declared_one_degree_tile(self):
        manifest = ACQUISITION.read_manifest(MANIFEST)
        for target in manifest["targets"]:
            with self.subTest(target=target["id"]):
                west, south, east, north = tile_bounds(target["tile"])
                bbox = target["study_window_degrees"]
                self.assertGreaterEqual(bbox["west"], west)
                self.assertGreaterEqual(bbox["south"], south)
                self.assertLessEqual(bbox["east"], east)
                self.assertLessEqual(bbox["north"], north)
                self.assertLess(bbox["west"], bbox["east"])
                self.assertLess(bbox["south"], bbox["north"])

    def test_public_copernicus_tile_url_uses_correct_glo90_resolution_code(self):
        url = ACQUISITION.copernicus_tile_url("N27_00_E086_00")
        self.assertIn("copernicus-dem-90m.s3.eu-central-1.amazonaws.com", url)
        self.assertIn("Copernicus_DSM_COG_30_N27_00_E086_00_DEM.tif", url)
        with self.assertRaisesRegex(ValueError, "Invalid Copernicus"):
            ACQUISITION.copernicus_tile_url("../invalid")

    def test_targets_preserve_data_quality_and_provenance_caveats(self):
        manifest = ACQUISITION.read_manifest(MANIFEST)
        self.assertEqual(manifest["data_policy"]["vertical_datum"], "EGM2008")
        self.assertEqual(manifest["data_policy"]["surface_type"], "DSM")
        self.assertEqual(manifest["data_policy"]["nominal_spacing_m"], 90)
        self.assertIn("not fine cliff", manifest["data_policy"]["note"])
        self.assertIn("license_url", manifest["data_policy"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
