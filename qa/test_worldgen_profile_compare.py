import tempfile
import unittest
from pathlib import Path
import importlib.util


ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "compare_worldgen_profiles", ROOT / "scripts" / "compare_worldgen_profiles.py")
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class WorldgenProfileCompareTest(unittest.TestCase):
    def test_log_parser_keeps_newest_count_per_section(self):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / "server.log"
            path.write_text(
                "[RealScale worldgen profile] RIVER calls=4096 mean_us=42 "
                "p95_bucket_upper_us=127 max_us=300\n"
                "[RealScale worldgen profile] RIVER calls=8192 mean_us=35 "
                "p95_bucket_upper_us=63 max_us=350\n"
                "[RealScale worldgen profile] GRAND_ECOLOGY calls=4096 "
                "mean_us=11 p95_bucket_upper_us=15 max_us=45\n",
                encoding="utf-8",
            )
            result = MODULE.parse(path)
            self.assertEqual(result["RIVER"], (8192, 35, 63, 350))
            self.assertEqual(result["GRAND_ECOLOGY"], (4096, 11, 15, 45))
            self.assertNotIn("SKY_ISLAND", result)

    def test_compare_disclaims_chunk_speed(self):
        report = MODULE.compare({"RIVER": (4096, 50, 127, 200)},
                                {"RIVER": (4096, 25, 63, 150)})
        self.assertIn("2.00x", report)
        self.assertIn("not", report)
        self.assertIn("missing comparable samples", report)


if __name__ == "__main__":
    unittest.main()
