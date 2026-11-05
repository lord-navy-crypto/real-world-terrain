"""Manufacturing acceptance gates for the eight opt-in Earth Wonders.
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

class NaturalWondersManufacturingTest(unittest.TestCase):
    def test_all_wonders_have_three_registered_assets(self):
        mod=(JAVA/"RealScaleMod.java").read_text()
        source=(JAVA/"EarthWonderFeature.java").read_text()
        for name,kind in WONDERS.items():
            with self.subTest(name=name):
                self.assertIn('"'+name+'"',mod)
                self.assertIn("Kind."+kind,mod)
                self.assertIn(kind,source)
                key="realscale:"+name
                configured=json.loads((DATA/"worldgen/configured_feature"/(name+".json")).read_text())
                placed=json.loads((DATA/"worldgen/placed_feature"/(name+".json")).read_text())
                modifier=json.loads((DATA/"neoforge/biome_modifier"/(name+".json")).read_text())
                self.assertEqual(configured["type"],key)
                self.assertEqual(placed["feature"],key)
                self.assertEqual(modifier["features"],key)
                self.assertIn({"type":"minecraft:biome"},placed["placement"])
                self.assertGreaterEqual(
                    next(v["chance"] for v in placed["placement"] if v["type"]=="minecraft:rarity_filter"),10)
                self.assertTrue(modifier["biomes"])

    def test_safe_overworld_wonders(self):
        source=(JAVA/"EarthWonderFeature.java").read_text()
        for guard in ("TVAWorldGuard.hasExperimentalWonders",
                      'equals("minecraft:overworld")',
                      "Math.floorDiv(x,16)==cx","Math.floorDiv(z,16)==cz",
                      "world.getFluidState(under).isEmpty()",
                      "emptyColumn(world,p,height)"):
            self.assertIn(guard,source)
        self.assertNotIn("Blocks.LAVA.defaultBlockState()",source)
        self.assertNotIn("Blocks.WATER.defaultBlockState()",source)

if __name__=="__main__": unittest.main()
