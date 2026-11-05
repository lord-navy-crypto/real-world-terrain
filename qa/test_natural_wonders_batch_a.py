"""Natural Wonders first manufacturing batch source-level safety contracts."""
import unittest
from pathlib import Path

SOURCE=(Path(__file__).resolve().parents[1]/
    "neoforge/src/main/java/org/realscale/neoforge/EarthSystemsAccentFeature.java")

class NaturalWondersBatchATest(unittest.TestCase):
    def test_first_three_real_landforms_are_active(self):
        s=SOURCE.read_text()
        for item in ("Kind.SALT_PLAYA","Kind.ALLUVIAL_FAN","Kind.COLD_DESERT"):
            self.assertIn(item,s)
        self.assertIn("Blocks.CALCITE.defaultBlockState()",s)
        self.assertIn("Blocks.WHITE_TERRACOTTA.defaultBlockState()",s)
        self.assertIn("Blocks.COARSE_DIRT.defaultBlockState()",s)
        self.assertIn("Blocks.ANDESITE.defaultBlockState()",s)
    def test_no_cross_chunk_liquid_or_existing_structure_write(self):
        s=SOURCE.read_text()
        self.assertIn("Math.floorDiv(x,16)!=cx",s)
        self.assertIn("Math.floorDiv(z,16)!=cz",s)
        self.assertIn("!world.getFluidState(target).isEmpty()",s)
        self.assertIn("!world.getFluidState(under).isEmpty()",s)
        self.assertIn("!world.getBlockState(target).isAir()",s)
        self.assertIn("if (material!=null) world.setBlock(target,material,2)",s)

if __name__=="__main__":
    unittest.main()
