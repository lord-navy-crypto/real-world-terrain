"""Require cave network features to be full chunk-local two-high passages."""
import json
import unittest
from pathlib import Path
R=Path(__file__).resolve().parents[1]
JAVA=(R/"neoforge/src/main/java/org/realscale/neoforge/CaveWonderNetworkFeature.java").read_text()
MOD=(R/"neoforge/src/main/java/org/realscale/neoforge/RealScaleMod.java").read_text()
DATA=R/"neoforge/src/main/resources/data/realscale"
class CaveWonderAcceptance(unittest.TestCase):
    def test_both_natural_cave_networks_registered(self):
        for id in ("geo_cave_lava_tube","geo_cave_karst_passage"):
            self.assertIn('"'+id+'"',MOD)
            c=json.loads((DATA/"worldgen/configured_feature"/(id+".json")).read_text())
            p=json.loads((DATA/"worldgen/placed_feature"/(id+".json")).read_text())
            m=json.loads((DATA/"neoforge/biome_modifier"/(id+".json")).read_text())
            self.assertEqual(c["type"],"realscale:"+id)
            self.assertEqual(p["feature"],"realscale:"+id)
            self.assertEqual(m["features"],"realscale:"+id)
            self.assertEqual(m["step"],"underground_decoration")
    def test_roof_fluid_world_and_chunk_guards(self):
        for needle in ("Math.floorDiv(context.origin().getX(),16)*16",
                       "for(int x=minX;x<minX+16;x++)",
                       "for(int z=minZ;z<minZ+16;z++)",
                       "TVAWorldGuard.hasRealScaleGenerator(context)",
                       "Blocks.CAVE_AIR.defaultBlockState()",
                       "if(!dry(w,foot)||!dry(w,head))continue;",
                       "if(!acceptable(w.getBlockState(roof)))continue;"):
            self.assertIn(needle,JAVA)
if __name__=="__main__":unittest.main()
