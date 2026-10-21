"""Verify region-scale geological wonders use the intended guarded pipeline."""
import json
import unittest
from pathlib import Path
R=Path(__file__).resolve().parents[1]
SRC=(R/"neoforge/src/main/java/org/realscale/neoforge/GeoWonderRegionFeature.java").read_text()
MOD=(R/"neoforge/src/main/java/org/realscale/neoforge/RealScaleMod.java").read_text()
DATA=R/"neoforge/src/main/resources/data/realscale"
IDS=["playa_basin","alluvial_fan","cold_desert_pavement","hoodoo_amphitheater","travertine_steps","mangrove_estuary","cooled_lava_field","glacial_moraine_ring","delta_distributaries","karst_doline_rim","permafrost_polygons"]
class GeoWonderRegionsTest(unittest.TestCase):
    def test_all_regional_features_have_complete_registry_wiring(self):
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
        self.assertIn("TVAWorldGuard.hasExperimentalWonders(context)",SRC)
        self.assertIn('equals("minecraft:overworld")',SRC)
        self.assertIn("int minX=Math.floorDiv(context.origin().getX(),16)*16;",SRC)
        self.assertIn("final int firstX=Math.max(minX,site.x-site.r);",SRC)
        self.assertIn("final int lastX=Math.min(minX+15,site.x+site.r);",SRC)
        self.assertIn("final int firstZ=Math.max(minZ,site.z-site.r);",SRC)
        self.assertIn("final int lastZ=Math.min(minZ+15,site.z+site.r);",SRC)
        self.assertIn("for(int x=firstX;x<=lastX;x++)for(int z=firstZ;z<=lastZ;z++)",SRC)
        self.assertIn("world.getFluidState",SRC) if "world.getFluidState" in SRC else self.assertIn("w.getFluidState",SRC)
        self.assertIn("if(!naturalFloor(base)",SRC)
        self.assertNotIn("Blocks.WATER.defaultBlockState()",SRC)
        self.assertNotIn("Blocks.LAVA.defaultBlockState()",SRC)
    def test_regional_footprints_are_not_chunk_seeded(self):
        self.assertIn("private GeoWonderGeometry.Site site(int gx,int gz)",SRC)
        self.assertIn("for(int sx=-1;sx<=1;sx++)",SRC)
        self.assertIn("for(int sz=-1;sz<=1;sz++)",SRC)
        self.assertIn("cachedSites.get().get(gx,gz)",SRC)
        cache=(R/"neoforge/src/main/java/org/realscale/neoforge/GeoWonderSiteCache.java").read_text()
        self.assertIn("GeoWonderGeometry.surfaceSite(gridX,gridZ,kind)",cache)
        self.assertIn("ThreadLocal.withInitial(",SRC)
if __name__=="__main__":unittest.main()
