#!/usr/bin/env python3
"""Regression tests for saved-world terrain continuity climate diagnostics."""
import json
import sys
import tempfile
import unittest
from pathlib import Path

QA_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(QA_DIR / "neoforge"))

import check_realism_continuity as continuity  # noqa: E402


class ContinuityLatitudeAuditTests(unittest.TestCase):
    def make_settings(self, root: Path, temperature: dict) -> Path:
        settings_dir = root / "worldgen" / "noise_settings"
        settings_dir.mkdir(parents=True)
        settings_path = settings_dir / "probe.json"
        settings_path.write_text(
            json.dumps({"noise_router": {"temperature": temperature}}),
            encoding="utf-8",
        )
        return settings_path

    def test_vanilla_shifted_noise_has_no_latitude_profile_and_is_not_applicable(self):
        with tempfile.TemporaryDirectory() as temp:
            settings = self.make_settings(
                Path(temp),
                {"type": "minecraft:shifted_noise", "noise": "minecraft:temperature"},
            )
            self.assertIsNone(continuity.meridional_profile_from_noise(settings))
            self.assertIsNone(continuity.radial_profile_from_noise(settings))
            result = continuity.meridional_biome_audit({}, {}, None)
            self.assertEqual(result["mode"], "not_applicable")
            self.assertIn("No signed north/south", result["note"])

    def test_radial_profile_is_audited_separately_from_meridional_profile(self):
        with tempfile.TemporaryDirectory() as temp:
            settings = self.make_settings(
                Path(temp),
                {
                    "type": "realscale:climate_budget",
                    "adjusted": {
                        "base": {
                            "type": "realscale:radial_latitude_temperature",
                            "center_x": 0,
                            "center_z": 0,
                            "pole_distance": 100,
                            "strength": 0.5,
                        }
                    },
                },
            )
            radial = continuity.radial_profile_from_noise(settings)
            meridional = continuity.meridional_profile_from_noise(settings)
            self.assertIsNotNone(radial)
            self.assertIsNone(meridional)
            result = continuity.radial_biome_audit(
                {(0, 0): 64, (25, 0): 80, (50, 0): 96},
                {
                    (0, 0): "minecraft:plains",
                    (25, 0): "minecraft:forest",
                    (50, 0): "minecraft:snowy_plains",
                },
                radial,
            )
            self.assertEqual(result["mode"], "radial_legacy")
            self.assertEqual(result["range_blocks"], [0, 50])
            self.assertEqual(continuity.meridional_biome_audit({}, {}, meridional)["mode"],
                             "not_applicable")

    def test_signed_meridional_profile_still_produces_north_and_south_bands(self):
        heights = {
            (0, 0): 64,
            (0, -50): 64,
            (0, 50): 64,
            (0, -90): 64,
            (0, 90): 64,
        }
        biomes = {position: "minecraft:plains" for position in heights}
        result = continuity.meridional_biome_audit(
            heights, biomes, {"equator_z": 0, "pole_distance": 100, "strength": 0.5}
        )
        self.assertEqual(result["mode"], "signed_north_south")
        self.assertEqual(
            sum(result[name]["sample_columns"] for name in (
                "north_polar_60_90", "north_mid_30_60", "equatorial_0_30",
                "south_mid_30_60", "south_polar_60_90"
            )),
            5,
        )


if __name__ == "__main__":
    unittest.main()
