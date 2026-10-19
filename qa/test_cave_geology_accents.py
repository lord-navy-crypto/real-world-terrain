"""Static safety and wiring tests for non-carving cave accents."""
import json
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/"neoforge/src/main/java/org/realscale/neoforge"
DATA=ROOT/"neoforge/src/main/resources/data/realscale"
class CaveGeologyQA(unittest.TestCase):
    def test_wiring(self):
        code=(SRC/"RealScaleMod.java").read_text()
        self.assertIn("new CaveGeologyAccentFeature()",code)
        a=json.loads((DATA/"worldgen/configured_feature/cave_geology_accents.json").read_text())
        b=json.loads((DATA/"worldgen/placed_feature/cave_geology_accents.json").read_text())
        m=json.loads((DATA/"neoforge/biome_modifier/cave_geology_accents.json").read_text())
        self.assertEqual(a["type"],"realscale:cave_geology_accents")
        self.assertEqual(b["feature"],"realscale:cave_geology_accents")
        self.assertEqual(m["step"],"underground_decoration")
        self.assertIn("minecraft:lush_caves",m["biomes"])
        self.assertIn("minecraft:dripstone_caves",m["biomes"])
        self.assertIn("minecraft:deep_dark",m["biomes"])
    def test_karst_ceiling_mineral_accents(self):
        c=(SRC/"CaveGeologyAccentFeature.java").read_text()
        self.assertIn("boolean ceiling=karst",c)
        self.assertIn("air.above(2):air.below()",c)
        self.assertIn("if(ceiling && !dry(w,rock.below()))continue",c)
        self.assertIn("Blocks.CALCITE.defaultBlockState()",c)

    def test_wet_karst_and_deep_rock_variation(self):
        c=(SRC/"CaveGeologyAccentFeature.java").read_text()
        self.assertIn('boolean moist=biome.equals("realscale:damp_limestone_caverns")',c)
        self.assertIn("moist && context.random().nextInt(4)==0",c)
        self.assertIn("Blocks.CLAY.defaultBlockState()",c)
        self.assertIn("Blocks.SMOOTH_BASALT.defaultBlockState()",c)
        self.assertIn("if(!replaceable(previous))continue",c)

    def test_no_carving_ore_or_cross_chunk_edits(self):
        c=(SRC/"CaveGeologyAccentFeature.java").read_text()
        for marker in ("TVAWorldGuard.hasRealScaleGenerator", "minecraft:overworld",
                       "attempt<8", "changed>=3", "chunk.getMinBlockX()",
                       "chunk.getMinBlockZ()", "replaceable(previous)",
                       "y>surface-16", "w.setBlock(rock,accent,2)"):
            self.assertIn(marker,c)
        for dangerous in ("CAVE_AIR.defaultBlockState()", "ORE.defaultBlockState()",
                          "addFreshEntity", "context.chunkGenerator().getNoise"):
            self.assertNotIn(dangerous,c)
if __name__=="__main__": unittest.main()
