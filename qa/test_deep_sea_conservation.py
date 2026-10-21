"""Wiring and safety checks for geological marine habitat extension."""
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/"neoforge/src/main/java/org/realscale/neoforge"
class DeepMarineQA(unittest.TestCase):
    def test_geological_habitats(self):
        s=(JAVA/"MarineEcologyZonationMath.java").read_text()
        for key in ("COLD_SEEP","VENT_MINERAL_FIELD","ROCKY_SEAMOUNT",
                    "geologicalDeepHabitat","relief>=5","relief>=3",
                    "Math.floorDiv(x,192)"):
            self.assertIn(key,s)
    def test_uses_existing_seafloor_only(self):
        s=(JAVA/"MarineEcologyFeature.java").read_text()
        self.assertIn("MarineEcologyZonationMath.geologicalDeepHabitat",s)
        self.assertIn("isHardSeafloor(support)",s)
        self.assertIn("Blocks.WET_SPONGE.defaultBlockState()",s)
        self.assertNotIn("Blocks.LAVA.defaultBlockState()",s)
        self.assertNotIn("Blocks.CAVE_AIR.defaultBlockState()",s)
if __name__=="__main__":unittest.main()
