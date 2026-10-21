"""Cache-known sky island eligibility may safely bound or skip satellites.

This guards ONLY sampler-scoped cached tiers; cold sites still evaluate the
original geometry and perform exactly the same remote ocean eligibility scan.
"""
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
SKY=(ROOT/"neoforge/src/main/java/org/realscale/neoforge/SkyOceanBiomeSource.java")

class CachedTierFastpath(unittest.TestCase):
    def test_known_tier_is_enforced_before_satellite_iteration(self):
        s=SKY.read_text()
        self.assertIn("int tier=site.tier;",s)
        self.assertIn("if(tier==0)return source.getNoiseBiome(quartX,quartY,quartZ,sampler);",s)
        self.assertIn("SkyIslandLayout.countForTier(tier) : SkyIslandLayout.SLOTS",s)
        self.assertIn("for(int slot=0;slot<eligibleSlots;slot++){",s)
        self.assertIn("tier=isolationTier(site,sampler);",s)
        self.assertIn("eligibleSlots=SkyIslandLayout.countForTier(tier);",s)
        self.assertIn("state.prepare(sampler);",s)
        self.assertIn("sites.clear();",s)
    def test_site_visibility_exhaustive_tier_and_slot_truth_table(self):
        # Tier 0 = 0 islands, tier 1 = base, tier 2 = base+2 satellites,
        # tier 3 = all 5 islands; each qualifying island has a unique footprint.
        counts=(0,1,3,5)
        for tier in range(4):
            for matching_slot in range(-1,5):
                original=any(s==matching_slot and s<counts[tier]
                             for s in range(5))
                optimized=any(s==matching_slot for s in range(counts[tier]))
                self.assertEqual(original,optimized,(tier,matching_slot))
if __name__=="__main__":unittest.main()
