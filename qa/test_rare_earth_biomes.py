"""Registry and bounded-decoration regression checks for rare Earth habitats."""
import json, unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/"neoforge/src/main/resources/data/realscale"
SRC=ROOT/"neoforge/src/main/java/org/realscale/neoforge"
IDS=("geothermal_sulfur_basin","socotra_dragonblood_woodland",
"white_dune_lagoon","sundarbans_tidal_mangrove","congo_peat_swamp_forest",
"steppe_saline_lake","acidic_geothermal_mudflat","montane_mist_forest",
"permafrost_fen","limestone_island_scrub",
"namib_fog_desert","serpentine_barrens","socotra_endemic_scrub")
class RareEarthBiomes(unittest.TestCase):
    def test_all_biomes_have_data(self):
        for name in IDS:
            with self.subTest(name=name):
                b=json.loads((DATA/"worldgen/biome"/(name+".json")).read_text())
                self.assertIn("temperature",b)
                self.assertIn("effects",b)
                self.assertIn("features",b)
    def test_feature_registered_and_targeted(self):
        code=(SRC/"RealScaleMod.java").read_text()
        self.assertIn("new RareEarthSurfaceFeature()",code)
        cfg=json.loads((DATA/"worldgen/configured_feature/rare_earth_surface.json").read_text())
        placed=json.loads((DATA/"worldgen/placed_feature/rare_earth_surface.json").read_text())
        modifier=json.loads((DATA/"neoforge/biome_modifier/rare_earth_surface.json").read_text())
        self.assertEqual(cfg["type"],"realscale:rare_earth_surface")
        self.assertEqual(placed["feature"],"realscale:rare_earth_surface")
        self.assertEqual(set(modifier["biomes"]),{"realscale:"+x for x in IDS})
    def test_safe_surface_only(self):
        src=(SRC/"RareEarthSurfaceFeature.java").read_text()
        for rule in ("i<48 && count<20", "Level.OVERWORLD",
                     "WORLD_SURFACE_WG", "natural(existing)",
                     "w.setBlock(ground,next,2)"):
            self.assertIn(rule,src)
        self.assertNotIn("CAVE_AIR.defaultBlockState()",src)
        self.assertNotIn("Blocks.LAVA.defaultBlockState()",src)
        self.assertNotIn("Blocks.WATER.defaultBlockState()",src)
if __name__=="__main__": unittest.main()
