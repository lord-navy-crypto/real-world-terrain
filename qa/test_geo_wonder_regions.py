"""Verify region-scale geological wonders use the intended guarded pipeline."""
import json
import unittest
from pathlib import Path
R=Path(__file__).resolve().parents[1]
SRC=(R/"neoforge/src/main/java/org/realscale/neoforge/GeoWonderRegionFeature.java").read_text()
MOD=(R/"neoforge/src/main/java/org/realscale/neoforge/RealScaleMod.java").read_text()
DATA=R/"neoforge/src/main/resources/data/realscale"
IDS=["playa_basin","alluvial_fan","cold_desert_pavement","hoodoo_amphitheater","travertine_steps","mangrove_estuary","cooled_lava_field","glacial_moraine_ring","delta_distributaries","karst_doline_rim"]
class GeoWonderRegionsTest(unittest.TestCase):
    def test_all_ten_features_have_complete_registry_wiring(self):
        for id in IDS:
            key="geo_region_"+id
            with self.subTest(key=key):
                self.assertIn('"'+key+'"',MOD)
                cf=json.loads((DATA/"worldgen/configured_feature"/(key+".json")).read_text())
                pf=json.loads((DATA/"worldgen/placed_feature"/(key+".json")).read_text())
                bm=json.loads((DATA/"neoforge/biome_modifier"/(key+".json")).read_text())
                self.assertEqual(cf["type"],"realscale:"+key)
                self.assertEqual(pf["feature"],"realscale:"+key)
                self.assertEqual(bm["features"],"realscale:"+key)
                self.assertTrue(bm["biomes"])
                self.assertIn({"type":"minecraft:biome"},pf["placement"])
                self.assertFalse(any(e["type"]=="minecraft:rarity_filter" for e in pf["placement"]))
    def test_world_and_chunk_guards(self):
        self.assertIn("TVAWorldGuard.hasRealScaleGenerator(context)",SRC)
        self.assertIn('equals("minecraft:overworld")',SRC)
        self.assertIn("int minX=Math.floorDiv(context.origin().getX(),16)*16;",SRC)
        self.assertIn("for(int x=minX;x<minX+16;x++)",SRC)
        self.assertIn("for(int z=minZ;z<minZ+16;z++)",SRC)
        self.assertIn("world.getFluidState",SRC) if "world.getFluidState" in SRC else self.assertIn("w.getFluidState",SRC)
        self.assertIn("if(!naturalFloor(base)",SRC)
        self.assertNotIn("Blocks.WATER.defaultBlockState()",SRC)
        self.assertNotIn("Blocks.LAVA.defaultBlockState()",SRC)
    def test_regional_footprints_are_not_chunk_seeded(self):
        self.assertIn("private Site site(int gx,int gz)",SRC)
        self.assertIn("for(int sx=-1;sx<=1;sx++)",SRC)
        self.assertIn("for(int sz=-1;sz<=1;sz++)",SRC)
        self.assertIn("Math.floorMod(hash,21L)",SRC)
if __name__=="__main__":unittest.main()
