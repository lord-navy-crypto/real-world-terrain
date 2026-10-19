"""Low-cost realm ecology integration and safety checks."""
import json, unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/"neoforge/src/main/java/org/realscale/neoforge"
DATA=ROOT/"neoforge/src/main/resources/data/realscale"
class RealmQA(unittest.TestCase):
    def test_wiring(self):
        source=(JAVA/"RealScaleMod.java").read_text()
        for realm in ("nether","end"):
            key=realm+"_ecology_accents"
            self.assertIn('"'+key+'"',source)
            cfg=json.loads((DATA/"worldgen/configured_feature"/(key+".json")).read_text())
            placed=json.loads((DATA/"worldgen/placed_feature"/(key+".json")).read_text())
            mod=json.loads((DATA/"neoforge/biome_modifier"/(key+".json")).read_text())
            self.assertEqual(cfg["type"],"realscale:"+key)
            self.assertEqual(placed["feature"],"realscale:"+key)
            self.assertEqual(mod["features"],"realscale:"+key)
            self.assertEqual(mod["step"],"vegetal_decoration")
    def test_netheritic_and_end_habitat_variation(self):
        c=(JAVA/"RealmEcologyAccentFeature.java").read_text()
        n=json.loads((DATA/"neoforge/biome_modifier/nether_ecology_accents.json").read_text())
        self.assertIn("minecraft:basalt_deltas",n["biomes"])
        for item in ('"minecraft:basalt_deltas"', "Blocks.SMOOTH_BASALT",
                     "Blocks.BLACKSTONE", "Blocks.CRIMSON_ROOTS",
                     "Blocks.WARPED_ROOTS", "Blocks.CHORUS_PLANT",
                     "Blocks.CHORUS_FLOWER", "Blocks.AMETHYST_BLOCK",
                     "Blocks.PURPUR_BLOCK", "WORLD_SURFACE_WG"):
            self.assertIn(item,c)
        self.assertNotIn("NETHER_GOLD_ORE",c)
        self.assertIn("Math.floorDiv(x,64)",c)

    def test_no_carving_and_chunk_bounds(self):
        source=(JAVA/"RealmEcologyAccentFeature.java").read_text()
        for v in ("TVAWorldGuard.hasRealScaleGenerator", "placed<28", "i<64",
                  "chunk.getMinBlockX()", "chunk.getMinBlockZ()",
                  "accent.canSurvive(w,p)", "Level.NETHER", "Level.END"):
            self.assertIn(v,source)
        for forbidden in ("CAVE_AIR.defaultBlockState()", "addFreshEntity", "Blocks.LAVA.defaultBlockState()"):
            self.assertNotIn(forbidden,source)
if __name__=="__main__":unittest.main()
