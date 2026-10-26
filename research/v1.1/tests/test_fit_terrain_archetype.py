import gzip
import hashlib
import importlib.util
import json
import struct
import tempfile
import unittest
from pathlib import Path

import numpy as np
import rasterio
from rasterio.transform import from_origin

ROOT = Path(__file__).resolve().parents[1]
TOOL = ROOT / "tools" / "fit_terrain_archetype.py"
SPEC = importlib.util.spec_from_file_location("terrain_archetype_fit", TOOL)
FIT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(FIT)


def synthetic_relief(rows=64, cols=64, cell=32.0):
    z, x = np.mgrid[0:rows, 0:cols]
    return (
        1200.0
        + 70.0 * np.sin(x / 7.0)
        + 45.0 * np.cos(z / 9.0)
        + 18.0 * np.sin((x + z) / 2.7)
        + 7.0 * np.cos((2.0 * x - z) / 1.9)
    ).astype(np.float32)


def write_rgt(path, elevation, cell=32.0):
    rows, cols = elevation.shape
    count = rows * cols
    raw = bytearray(b"RGT1")
    raw.extend(struct.pack(">5i2d", cols, rows, 0, 0, 63, cell, 0.0))
    for field in (
        elevation,
        np.full((rows, cols), np.nan, dtype=np.float32),
        np.full((rows, cols), np.nan, dtype=np.float32),
    ):
        raw.extend(np.asarray(field, dtype=">f4").tobytes())
    raw.extend(bytes(count))
    with path.open("wb") as stream:
        with gzip.GzipFile(fileobj=stream, mode="wb", filename="", mtime=0) as compressed:
            compressed.write(raw)


class TerrainArchetypeFitTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def fit(self, array, family="alpine", cell_x=32.0, cell_z=32.0, input_meta=None):
        return FIT.fit_profile(
            elevation=array.astype(np.float64),
            cell_x=cell_x,
            cell_z=cell_z,
            family=family,
            source_name="Synthetic test fixture, not a real DEM",
            source_url="https://example.invalid/synthetic-fixture",
            vertical_datum="synthetic-test-datum",
            source_sha256="a" * 64,
            source_file="synthetic-not-real.tif",
            input_meta=input_meta or {
                "input_format": "test fixture",
                "crs": "synthetic local metric grid",
                "native_spacing_m": [cell_x, cell_z],
            },
        )

    def test_fit_is_compact_and_never_embeds_height_grid(self):
        profile = self.fit(synthetic_relief())
        encoded = json.dumps(profile)
        self.assertEqual(profile["schema"], "realscale:terrain_archetype_fit/v1")
        self.assertFalse(profile["generation_contract"]["raw_elevation_samples_embedded"])
        self.assertNotIn("height_grid", profile)
        self.assertNotIn("elevation_grid", profile)
        self.assertNotIn("raw_samples", profile)
        self.assertGreater(profile["observed_statistics"]["relief_p95_minus_p05_m"], 0)
        self.assertIn("macro_gain", profile["fit_parameters"])
        self.assertGreaterEqual(profile["fit_parameters"]["relief_gain"], 0.10)
        self.assertIn("semivariograms", profile["observed_statistics"])
        self.assertIn("vertical_mapping_requires_explicit_choice", profile["generation_contract"])
        self.assertIn("procedural_from_fitted_coefficients", encoded)

    def test_family_changes_kernel_not_measured_statistics(self):
        array = synthetic_relief()
        families = {family: self.fit(array, family) for family in FIT.FAMILIES}
        kernels = [profile["family_kernel"] for profile in families.values()]
        self.assertEqual(len(kernels), len(set(kernels)))
        for profile in families.values():
            self.assertEqual(profile["fit_parameters"], families["natural"]["fit_parameters"])
        self.assertIn("dominant_wavelength_m", families["dune"]["fit_parameters"])
        self.assertIn("dune_axis_radians", families["dune"]["fit_parameters"])
        self.assertLessEqual(families["dune"]["fit_parameters"]["dominant_wavelength_m"], 5000.0)

    def test_fitted_profile_generates_new_repeatable_fields_without_source_grid(self):
        profile = self.fit(synthetic_relief(), family="alpine")
        first = FIT.synthesize_heightfield(profile, 64, 64, 32.0, 32.0, seed=42)
        repeat = FIT.synthesize_heightfield(profile, 64, 64, 32.0, 32.0, seed=42)
        other_seed = FIT.synthesize_heightfield(profile, 64, 64, 32.0, 32.0, seed=43)
        self.assertEqual(first.shape, (64, 64))
        self.assertTrue(np.isfinite(first).all())
        self.assertTrue(np.allclose(first, repeat))
        self.assertFalse(np.allclose(first, other_seed))
        self.assertGreater(float(np.std(first)), 0.0)

    def test_family_kernel_changes_synthetic_shape(self):
        array = synthetic_relief()
        array = synthetic_relief()
        fields = {
            family: FIT.synthesize_heightfield(
                self.fit(array, family), 64, 64, 32.0, 32.0, seed=7
            )
            for family in FIT.FAMILIES
        }
        for i, family_a in enumerate(FIT.FAMILIES):
            for family_b in FIT.FAMILIES[i + 1:]:
                with self.subTest(family_a=family_a, family_b=family_b):
                    self.assertFalse(np.allclose(fields[family_a], fields[family_b]))

    def test_rgt_input_keeps_source_hash_and_metric_spacing(self):
        source = self.root / "fixture.rgt.gz"
        array = synthetic_relief()
        write_rgt(source, array)
        elevation, cell_x, cell_z, meta = FIT.read_dem(source, "meter")
        profile = self.fit(elevation, cell_x=cell_x, cell_z=cell_z)
        self.assertEqual((cell_x, cell_z), (32.0, 32.0))
        self.assertEqual(meta["input_format"], "RGT1")
        self.assertEqual(FIT.sha256_file(source), hashlib.sha256(source.read_bytes()).hexdigest())
        self.assertEqual(profile["source"]["native_spacing_m"], [32.0, 32.0])

    def test_geotiff_spacing_and_metadata_are_read(self):
        source = self.root / "fixture_not_real.tif"
        array = synthetic_relief()
        with rasterio.open(
            source,
            "w",
            driver="GTiff",
            height=array.shape[0],
            width=array.shape[1],
            count=1,
            dtype="float32",
            crs="EPSG:4326",
            transform=from_origin(-121.04, 36.04, 0.0004, 0.0004),
        ) as dst:
            dst.write(array, 1)
        elevation, cell_x, cell_z, meta = FIT.read_dem(source, "meter")
        self.assertTrue(np.isfinite(elevation).all())
        self.assertGreater(cell_x, 20)
        self.assertGreater(cell_z, 20)
        self.assertEqual(meta["input_format"], "GeoTIFF")
        self.assertEqual(meta["crs"], "EPSG:4326")
        profile = self.fit(elevation, cell_x=cell_x, cell_z=cell_z, input_meta=meta)
        self.assertEqual(profile["source"]["input_crs"], "EPSG:4326")

    def test_nodata_is_rejected_not_silently_filled(self):
        source = self.root / "fixture_nodata.tif"
        array = synthetic_relief()
        array[10, 10] = -9999
        with rasterio.open(
            source,
            "w",
            driver="GTiff",
            height=array.shape[0],
            width=array.shape[1],
            count=1,
            dtype="float32",
            crs="EPSG:4326",
            transform=from_origin(-121.04, 36.04, 0.0004, 0.0004),
            nodata=-9999,
        ) as dst:
            dst.write(array, 1)
        with self.assertRaisesRegex(ValueError, "NoData"):
            FIT.read_dem(source, "meter")

    def test_too_small_or_flat_inputs_are_rejected(self):
        with self.assertRaisesRegex(ValueError, "at least 32"):
            self.fit(synthetic_relief(16, 16))
        with self.assertRaisesRegex(ValueError, "too small"):
            self.fit(synthetic_relief(32, 32), cell_x=20.0, cell_z=20.0)
        with self.assertRaisesRegex(ValueError, "effectively flat"):
            self.fit(np.full((64, 64), 500.0))


if __name__ == "__main__":
    unittest.main(verbosity=2)
