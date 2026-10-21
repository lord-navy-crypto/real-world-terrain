import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).parent/"neoforge"))
from parse_density_profile import extract

class DensityProfileParserTests(unittest.TestCase):
    def test_shutdown_lines_only_and_correct_stage_labels(self):
        text="""[RealScale density profile] MOUNTAIN_RELIEF samples=30 stride=1024 mean_us=7 p95_bucket_upper_us=15 max_us=21
[RealScale density profile] OVERWORLD_CAVE samples=22 stride=1024 mean_us=80 p95_bucket_upper_us=127 max_us=271
[RealScale density profile] SKY_BIOME samples=8 stride=1024 mean_us=2 p95_bucket_upper_us=7 max_us=11
"""
        r=extract(text)
        d=r["sampled_inclusive_stages"]
        self.assertEqual(list(d)[0],"OVERWORLD_CAVE")
        self.assertEqual(d["MOUNTAIN_RELIEF"]["approx_sampled_inclusive_microseconds"],210)
        self.assertEqual(d["SKY_BIOME"]["samples"],8)
        self.assertIn("NOT wall-clock",r["interpretation"])
        with self.assertRaises(ValueError):
            extract("[RealScale density profile] MOUNTAIN_RELIEF samples=1 stride=1024 mean_us=7 p95_bucket_upper_us=15 max_us=20")

if __name__=="__main__":unittest.main()
