"""Safeguard bounded worker-local Nether cave topology caches."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/"neoforge/src/main/java/org/realscale/neoforge/GrandNetherCaveNetwork.java"

class GrandNetherLocalityGuards(unittest.TestCase):
    def test_region_and_column_cache_retains_bounded_multiway_locality(self):
        code=SRC.read_text()
        for phrase in (
            "REGION_SLOTS=8",
            "COLUMN_SLOTS = 512",
            "COLUMN_BUCKETS = COLUMN_SLOTS / 2",
            "lastColumnValid",
            "columnVictim[bucket]^=1",
            "lastColumnMask",
            "ThreadLocal<RegionCache>",
            "regionBuilds++",
            "c.maskBuilds++",
        ):
            self.assertIn(phrase,code)
        self.assertNotIn("static final java.util.Map",code)

    def test_same_cave_domain_and_bit_parity_oracle_remain(self):
        code=SRC.read_text()
        self.assertIn("public static final int MIN_Y = 144",code)
        self.assertIn("public static final int MAX_Y = 440",code)
        self.assertIn("strengthFromColumn(",code)
        oracle=(ROOT/"neoforge/src/test/java/org/realscale/neoforge/GrandNetherCaveNetworkReference.java").read_text()
        self.assertIn("public static double strength(",oracle)

if __name__=="__main__": unittest.main()
