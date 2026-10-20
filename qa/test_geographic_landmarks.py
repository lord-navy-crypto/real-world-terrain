"""Real-world reference catalog and eight registered biome-qualified microforms."""
import json
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/"neoforge/src/main/resources/data/realscale"
JAVA=ROOT/"neoforge/src/main/java/org/realscale/neoforge"
KINDS={
 "geo_wulingyuan_pillars":("WULINGYUAN_SANDSTONE",4),
 "geo_south_china_karst":("SOUTH_CHINA_KARST",4),
 "geo_aswan_granite":("ASWAN_GRANITE",3),
 "geo_socotra_limestone":("SOCOTRA_LIMESTONE",2),
 "geo_iceland_basalt":("ICELAND_LAVA_FIELD",4),
 "geo_huanglong_travertine":("HUANGLONG_TRAVERTINE",4),
 "geo_guilin_river_karst":("GUILIN_RIVER_KARST",4),
 "geo_danxia_redbeds":("DANXIA_REDBEDS",4),
}
def read(path):return json.loads(path.read_text(encoding="utf-8"))

class GeographicLandmarksTest(unittest.TestCase):
    def test_feature_registration_and_biome_wiring(self):
        src=(JAVA/"RealScaleMod.java").read_text()
        feature=(JAVA/"GeographicLandmarkFeature.java").read_text()
        for name,(kind,_count) in KINDS.items():
            with self.subTest(name=name):
                configured=read(RES/"worldgen/configured_feature"/(name+".json"))
                placed=read(RES/"worldgen/placed_feature"/(name+".json"))
                modifier=read(RES/"neoforge/biome_modifier"/(name+".json"))
                self.assertEqual(configured["type"],"realscale:"+name)
                self.assertEqual(placed["feature"],"realscale:"+name)
                self.assertEqual(placed["placement"][2]["heightmap"],"WORLD_SURFACE_WG")
                self.assertEqual(modifier["features"],"realscale:"+name)
                self.assertEqual(modifier["type"],"neoforge:add_features")
                self.assertEqual(modifier["step"],"vegetal_decoration")
                self.assertTrue(modifier["biomes"])
                self.assertIn('"'+name+'"',src)
                self.assertIn("GeographicLandmarkFeature.Kind."+kind,src)
                self.assertIn("case "+kind+" ->",feature)

    def test_safe_vanilla_and_real_scale_only(self):
        src=(JAVA/"GeographicLandmarkFeature.java").read_text()
        for marker in ("TVAWorldGuard.hasRealScaleGenerator(ctx)",
                       "Level.OVERWORLD","inside(chunk,p)",
                       "getHeight(Heightmap.Types.WORLD_SURFACE_WG",
                       "natural(world.getBlockState(p.below()))",
                       "world.getBlockState(p).isAir()",
                       "world.getFluidState(p).isEmpty()",
                       "shape.size()>38","sourceWaterNearby(",
                       "world.getFluidState(p).isSource()"):
            self.assertIn(marker,src)
        self.assertNotIn("Blocks.WATER.defaultBlockState()",src)
        self.assertNotIn("Blocks.LAVA.defaultBlockState()",src)
        self.assertNotIn("Blocks.CAVE_AIR.defaultBlockState()",src)

    def test_scientific_atlas_matches_registered_landmarks(self):
        atlas=read(ROOT/"docs/REAL_WORLD_LANDSCAPE_ATLAS.json")
        ids={x["minecraft_feature"].split(":")[-1] for x in atlas["landmarks"]}
        self.assertEqual(set(KINDS)-ids,set())
        self.assertEqual(len(atlas["landmarks"]),9)
        self.assertTrue(atlas["ethics"]["real_dem_priority"])
        self.assertFalse(atlas["ethics"]["copy_game_assets"])
        for item in atlas["landmarks"]:
            self.assertTrue(item["region_authority"].startswith("https://"))

    def test_evidence_ranked_genshin_reference_atlas(self):
        crosswalk=read(ROOT/"docs/GENSHIN_REAL_GEOGRAPHY_CROSSWALK.json")
        self.assertGreaterEqual(len(crosswalk["entries"]),10)
        allowed=set(crosswalk["status_definitions"])
        directs=[row for row in crosswalk["entries"]
                 if row["evidence"]=="officially_confirmed_direct"]
        self.assertEqual(len(directs),3)
        for row in crosswalk["entries"]:
            self.assertIn(row["evidence"],allowed)
            self.assertTrue(row["game_source"].startswith("https://"))
            self.assertTrue(row["real_source"].startswith("https://"))
            self.assertFalse(row.get("copy_game_assets",False))
        for needed in ("realscale:geo_huanglong_travertine",
                       "realscale:geo_guilin_river_karst",
                       "realscale:geo_danxia_redbeds"):
            self.assertIn(needed,{row["current_code"] for row in crosswalk["entries"]})

    def test_water_adjacent_huanglong_and_guilin_leave_existing_water_intact(self):
        src=(JAVA/"GeographicLandmarkFeature.java").read_text()
        self.assertIn("kind==Kind.HUANGLONG_TRAVERTINE",src)
        self.assertIn("kind==Kind.GUILIN_RIVER_KARST",src)
        self.assertIn("sourceWaterNearby(world,foot,chunk)",src)
        self.assertIn("!riverAdjacency",src)
        self.assertNotIn("world.setBlock(p,Blocks.WATER",src)

    def test_four_player_menus_not_extended(self):
        normal=ROOT/"neoforge/src/main/resources/data/minecraft/tags/worldgen/world_preset/normal.json"
        extended=normal.with_name("extended.json")
        self.assertEqual(len(read(normal)["values"]),4)
        self.assertEqual(read(normal)["values"],read(extended)["values"])

if __name__=="__main__":unittest.main()
