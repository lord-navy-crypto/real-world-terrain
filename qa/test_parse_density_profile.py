import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).parent/"neoforge"))
from parse_density_profile import extract

class DensityProfileParserTests(unittest.TestCase):
    def test_shutdown_lines_only_and_correct_stage_labels(self):
        text="""[RealScale density profile] MOUNTAIN_RELIEF samples=30 stride=1024 mean_ns=7000 mean_us=7 p95_bucket_upper_us=15 max_us=21
[RealScale density profile] OVERWORLD_CAVE samples=22 stride=1024 mean_ns=80000 mean_us=80 p95_bucket_upper_us=127 max_us=271
[RealScale density profile] SKY_BIOME samples=8 stride=1024 mean_ns=2000 mean_us=2 p95_bucket_upper_us=7 max_us=11
"""
        r=extract(text)
        d=r["sampled_inclusive_stages"]
        self.assertEqual(list(d)[0],"OVERWORLD_CAVE")
        self.assertEqual(d["MOUNTAIN_RELIEF"]["approx_sampled_inclusive_microseconds"],210)
        self.assertEqual(d["SKY_BIOME"]["samples"],8)
        self.assertEqual(d["OVERWORLD_CAVE"]["mean_nanoseconds"],80000)
        self.assertEqual(d["MOUNTAIN_RELIEF"]["approx_sampled_inclusive_nanoseconds"],210000)
        submicro=extract("""[RealScale density profile] OVERWORLD_CAVE samples=22 stride=1024 mean_ns=123 mean_us=0 p95_bucket_upper_us=0 max_us=3
[RealScale density profile] MOUNTAIN_RELIEF samples=33 stride=1024 mean_ns=93 mean_us=0 p95_bucket_upper_us=0 max_us=4
""")
        self.assertEqual(submicro["sampled_inclusive_stages"]["OVERWORLD_CAVE"]["mean_nanoseconds"],123)
        self.assertIn("NOT wall-clock",r["interpretation"])
        self.assertIsNone(r["ecotone_cache"])
        c=extract(text+"""[RealScale ecotone cache profile] resolution_calls=400 base_hits=1000 base_misses=250
""")["ecotone_cache"]
        self.assertEqual(c["resolution_calls"],400)
        self.assertEqual(c["underlying_hit_pct"],80)
        self.assertEqual(c["underlying_climate_calls"],250)
        self.assertEqual(c["full_output_cache"],"disabled_due_to_0.03pct_real_hit_rate")
        with self.assertRaises(ValueError):
            extract("[RealScale density profile] MOUNTAIN_RELIEF samples=1 stride=1024 mean_ns=7000 mean_us=7 p95_bucket_upper_us=15 max_us=20")

if __name__=="__main__":unittest.main()
