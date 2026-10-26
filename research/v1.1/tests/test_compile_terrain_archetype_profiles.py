import importlib.util
import json
import tempfile
import unittest
import zipfile
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / "tools"
FIT_SPEC = importlib.util.spec_from_file_location("terrain_archetype_fit", TOOLS / "fit_terrain_archetype.py")
FIT = importlib.util.module_from_spec(FIT_SPEC)
FIT_SPEC.loader.exec_module(FIT)
COMPILER_SPEC = importlib.util.spec_from_file_location("compile_terrain_archetype_profiles", TOOLS / "compile_terrain_archetype_profiles.py")
COMPILER = importlib.util.module_from_spec(COMPILER_SPEC)
COMPILER_SPEC.loader.exec_module(COMPILER)


def synthetic_relief(rows=64, cols=64, cell=32.0):
    z, x = np.mgrid[0:rows, 0:cols]
    return (1200.0 + 70.0 * np.sin(x / 7.0) + 45.0 * np.cos(z / 9.0)
            + 18.0 * np.sin((x + z) / 2.7) + 7.0 * np.cos((2.0 * x - z) / 1.9))


class TerrainProfileCompilerTests(unittest.TestCase):
    def test_compiler_emits_six_fitted_modes_plus_generic_default(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            profiles = root / "profiles"
            out = root / "compiled"
            profiles.mkdir()
            array = synthetic_relief()
            input_meta = {
                "input_format": "test fixture",
                "crs": "synthetic local metric grid",
                "native_spacing_m": [32.0, 32.0],
            }
            for family, config in COMPILER.FAMILY_CONFIG.items():
                profile = FIT.fit_profile(
                    elevation=array.astype(np.float64),
                    cell_x=32.0,
                    cell_z=32.0,
                    family=family,
                    source_name="Synthetic fixture — test only",
                    source_url="https://example.invalid/synthetic-fixture",
                    vertical_datum="synthetic test datum",
                    source_sha256=(family.encode().hex() * 64)[:64],
                    source_file="synthetic-not-real.tif",
                    input_meta=input_meta,
                )
                profile["source"]["source_tile_sha256"] = "b" * 64
                (profiles / f"{config['target']}.json").write_text(
                    json.dumps(profile, ensure_ascii=False, indent=2), encoding="utf-8"
                )

            entries = COMPILER.compile_profiles(
                profiles, COMPILER.DEFAULT_RESOURCES, out
            )
            self.assertEqual(len(entries), 6)
            for tag_name in ("normal", "extended"):
                tag = json.loads((out / "data/minecraft/tags/worldgen/world_preset" / f"{tag_name}.json").read_text())
                self.assertEqual(len(tag["values"]), 7)
                self.assertEqual(tag["values"][0], "realscale:natural_three_realms")
                self.assertIn("realscale:dune_three_realms", tag["values"])
                self.assertNotIn("realscale:measured_three_realms", tag["values"])

            for family, config in COMPILER.FAMILY_CONFIG.items():
                with self.subTest(family=family):
                    root_id = config["density_root"]
                    preset = json.loads((out / "data/realscale/worldgen/world_preset" / f"{config['preset']}.json").read_text())
                    self.assertEqual(
                        preset["dimensions"]["minecraft:overworld"]["generator"]["settings"],
                        "realscale:" + root_id,
                    )
                    height = json.loads((out / "data/realscale/worldgen/density_function" / root_id / "height_signal.json").read_text())
                    self.assertEqual(height["type"], "realscale:terrain_archetype")
                    self.assertEqual(height["family"], family)

                    # Every compiled runtime coefficient must preserve the
                    # corresponding value from the fitted profile. This guards
                    # against menu/resource generation silently dropping data.
                    fitted_profile = json.loads(
                        (profiles / f"{config['target']}.json").read_text(encoding="utf-8")
                    )
                    parameter_fields = (
                        ("macro_gain", "macro_gain"),
                        ("meso_gain", "meso_gain"),
                        ("micro_gain", "micro_gain"),
                        ("relief_gain", "relief_gain"),
                        ("ridge_sharpness", "ridge_sharpness"),
                        ("cliff_index", "cliff_index"),
                        ("valley_index", "valley_index"),
                        ("ridge_index", "ridge_index"),
                        ("anisotropy_gain", "anisotropy_gain"),
                        ("dune_axis_radians", "dune_axis_radians"),
                        ("dominant_wavelength_m", "dominant_wavelength_m"),
                    )
                    for runtime_name, profile_name in parameter_fields:
                        expected = float(fitted_profile["fit_parameters"][profile_name])
                        if runtime_name == "dune_axis_radians":
                            expected = max(-float(np.pi), min(float(np.pi), expected))
                        elif runtime_name == "dominant_wavelength_m":
                            expected = max(1.0, min(1_000_000.0, expected))
                        self.assertAlmostEqual(
                            height[runtime_name], expected, places=6,
                            msg=f"{family}: {runtime_name} did not map from {profile_name}",
                        )
                    legacy_id = config["legacy_source"]
                    signal_fields = COMPILER.LEGACY_SIGNAL_FIELDS[legacy_id]
                    self.assertEqual(height["continents"], f"realscale:{legacy_id}/{signal_fields['continents']}")
                    self.assertNotIn("height_grid", height)
                    settings = json.loads((out / "data/realscale/worldgen/noise_settings" / f"{root_id}.json").read_text())
                    self.assertEqual(settings["noise_router"]["depth"], f"realscale:{root_id}/terrain_density")
                    router = settings["noise_router"]
                    self.assertEqual(router["continents"], f"realscale:{legacy_id}/{signal_fields['continents']}")
                    self.assertEqual(router["erosion"], f"realscale:{legacy_id}/{signal_fields['erosion']}")
                    self.assertEqual(router["ridges"], f"realscale:{legacy_id}/{signal_fields['ridges']}")
                    adjusted = router["temperature"]["adjusted"]
                    self.assertEqual(adjusted["continents"], f"realscale:{legacy_id}/{signal_fields['continents']}")
                    source_density_root = COMPILER.DEFAULT_RESOURCES / "data/realscale/worldgen/density_function" / legacy_id
                    for field in signal_fields.values():
                        self.assertTrue((source_density_root / f"{field}.json").is_file(), field)

            provenance = json.loads((out / "terrain-profile-provenance.json").read_text())
            self.assertEqual(provenance["preset_count"], 7)
            self.assertEqual(provenance["profile_count"], 6)
            self.assertFalse(provenance["raw_dem_grids_embedded"])
            datapack_zip = out.parent / "RealScale-Terrain-Archetypes-Datapack.zip"
            language_zip = out.parent / "RealScale-Terrain-Archetypes-Language-Pack.zip"
            self.assertTrue(datapack_zip.is_file())
            self.assertTrue(language_zip.is_file())
            with zipfile.ZipFile(datapack_zip) as archive:
                names = set(archive.namelist())
                self.assertIn("pack.mcmeta", names)
                self.assertIn("data/minecraft/tags/worldgen/world_preset/normal.json", names)
                self.assertIn("data/realscale/worldgen/world_preset/dune_three_realms.json", names)
            with zipfile.ZipFile(language_zip) as archive:
                names = set(archive.namelist())
                self.assertIn("pack.mcmeta", names)
                self.assertIn("assets/minecraft/lang/zh_cn.json", names)


if __name__ == "__main__":
    unittest.main(verbosity=2)
