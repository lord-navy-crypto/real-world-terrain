"""Manufacturing acceptance gates for eight Earth Wonders and six invasion directions.
Static integration checks; dedicated-server and Anvil worldgen proof remain
separate required gates. These tests do NOT claim the features have spawned.
"""
import json
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/"neoforge/src/main/java/org/realscale/neoforge"
DATA=ROOT/"neoforge/src/main/resources/data/realscale"
WONDERS={
 "earth_hoodoo_spires":"HOODOO_SPIRES",
 "earth_travertine_terraces":"TRAVERTINE_TERRACES",
 "earth_mangrove_tidal_roots":"MANGROVE_TIDAL_ROOTS",
 "earth_cooled_lava_field":"COOLED_LAVA_FIELD",
 "earth_lava_tube_lining":"LAVA_TUBE_LINING",
 "earth_glacial_moraine":"GLACIAL_MORAINE",
 "earth_delta_sediment":"DELTA_SEDIMENT",
 "earth_karst_sinkhole_rim":"KARST_SINKHOLE_RIM",
}
INVASIONS={
 "invasion_patch_nether_overworld":"NETHER_IN_OVERWORLD",
 "invasion_patch_end_overworld":"END_IN_OVERWORLD",
 "invasion_patch_overworld_nether":"OVERWORLD_IN_NETHER",
 "invasion_patch_end_nether":"END_IN_NETHER",
 "invasion_patch_overworld_end":"OVERWORLD_IN_END",
 "invasion_patch_nether_end":"NETHER_IN_END",
}

class ManufacturingRegistrationTest(unittest.TestCase):
    def test_all_features_have_three_registered_assets(self):
        mod=(JAVA/"RealScaleMod.java").read_text()
        java1=(JAVA/"EarthWonderFeature.java").read_text()
        java2=(JAVA/"CrossRealmTerrainPatchFeature.java").read_text()
        for name,kind in {**WONDERS,**INVASIONS}.items():
            with self.subTest(name=name):
                self.assertIn('"'+name+'"',mod)
                self.assertIn("Kind."+kind,mod)
                self.assertIn(kind, java1 if name in WONDERS else java2)
                key="realscale:"+name
                c=json.loads((DATA/"worldgen/configured_feature"/(name+".json")).read_text())
                p=json.loads((DATA/"worldgen/placed_feature"/(name+".json")).read_text())
                m=json.loads((DATA/"neoforge/biome_modifier"/(name+".json")).read_text())
                self.assertEqual(c["type"],key)
                self.assertEqual(p["feature"],key)
                self.assertEqual(m["features"],key)
                self.assertIn({"type":"minecraft:biome"},p["placement"])
                self.assertGreaterEqual(
                    next(v["chance"] for v in p["placement"] if v["type"]=="minecraft:rarity_filter"),10)
                self.assertTrue(m["biomes"])
    def test_safe_overworld_wonders(self):
        s=(JAVA/"EarthWonderFeature.java").read_text()
        for guard in ("TVAWorldGuard.hasRealScaleGenerator",
                      'equals("minecraft:overworld")',
                      "Math.floorDiv(x,16)==cx","Math.floorDiv(z,16)==cz",
                      "world.getFluidState(under).isEmpty()",
                      "emptyColumn(world,p,height)"):
            self.assertIn(guard,s)
        self.assertNotIn("Blocks.LAVA.defaultBlockState()",s)
        self.assertNotIn("Blocks.WATER.defaultBlockState()",s)
    def test_invasion_dimension_and_preset_guards(self):
        s=(JAVA/"CrossRealmTerrainPatchFeature.java").read_text()
        for guard in ("TVAWorldGuard.hasRealScaleGenerator",
                      "instanceof CrossRealmShowcaseBiomeSource",
                      "instanceof CrossRealmBiomeSource",
                      "Math.floorDiv(x,16)!=homeX",
                      "Math.floorDiv(z,16)!=homeZ",
                      "!world.getFluidState(floor).isEmpty()",
                      "sourceMatches(id)"):
            self.assertIn(guard,s)
        for token in ("Blocks.END_STONE","Blocks.NETHERRACK","Blocks.MOSS_BLOCK"):
            self.assertIn(token,s)
if __name__=="__main__":unittest.main()
