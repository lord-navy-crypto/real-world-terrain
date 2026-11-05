"""Guard sky-biome fast path without relaxing offshore eligibility."""
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
SKY=ROOT/"neoforge/src/main/java/org/realscale/neoforge/SkyOceanBiomeSource.java"

class SkyBiomeFastpathTest(unittest.TestCase):
    def test_lazily_computed_tier_and_bound_satellite_loop(self):
        source=SKY.read_text()
        self.assertIn("int tier=-1;",source)
        self.assertIn("if(tier<0)",source)
        self.assertIn("tier=isolationTier(base,sampler);",source)
        self.assertIn("eligibleSlots=SkyIslandLayout.countForTier(tier);",source)
        self.assertIn("if(slot<eligibleSlots)return blossomIsle;",source)
        self.assertIn("if(!SkyIslandLayout.insideBiome(island,x,y,z))continue;",source)
        self.assertIn("if(y<160||y>224)return source.getNoiseBiome",source)

if __name__=="__main__": unittest.main()
