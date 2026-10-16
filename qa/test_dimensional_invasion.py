"""Static resource/guard regression for opt-in three-realm invasion features."""
import json
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/"neoforge/src/main/resources/data/realscale"
SOURCE=ROOT/"neoforge/src/main/java/org/realscale/neoforge"

class InvasionRegistrationTest(unittest.TestCase):
    def test_each_realm_source_has_datapack_wiring(self):
        registry=(SOURCE/"RealScaleMod.java").read_text()
        for source in ("nether","end","overworld"):
            with self.subTest(invader=source):
                key="invasion_"+source
                configured=json.loads((DATA/"worldgen/configured_feature"/(key+".json")).read_text())
                placed=json.loads((DATA/"worldgen/placed_feature"/(key+".json")).read_text())
                biome=json.loads((DATA/"neoforge/biome_modifier"/(key+".json")).read_text())
                self.assertEqual(configured["type"],"realscale:"+key)
                self.assertEqual(placed["feature"],"realscale:"+key)
                self.assertEqual(biome["features"],"realscale:"+key)
                self.assertEqual(biome["step"],"vegetal_decoration")
                self.assertGreaterEqual(placed["placement"][0]["chance"],100)
                self.assertIn('"'+key+'"',registry)
                self.assertIn("Invader."+source.upper(),registry)

    def test_realm_guard_and_atomic_same_chunk_placement(self):
        source=(SOURCE/"DimensionalInvasionFeature.java").read_text()
        self.assertIn("TVAWorldGuard.hasRealScaleGenerator(context)",source)
        self.assertIn('!"minecraft".equals(realm.getNamespace())',source)
        self.assertIn('!"overworld".equals(home)',source)
        self.assertIn('!"the_nether".equals(home)',source)
        self.assertIn('!"the_end".equals(home)',source)
        self.assertIn("(p.getX()>>4)!=cx",source)
        self.assertIn("(p.getZ()>>4)!=cz",source)
        self.assertIn("level.getBlockState(p).isAir()",source)
        self.assertIn("level.setBlock(p,",source)
        self.assertNotIn("Blocks.LAVA.defaultBlockState()",source)
        self.assertNotIn("Blocks.BEDROCK.defaultBlockState()",source)

if __name__=="__main__":
    unittest.main()
