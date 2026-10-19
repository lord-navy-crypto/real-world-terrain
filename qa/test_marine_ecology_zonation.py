"""RealScale cold + tropical ocean ecological stratification on a SINGLE world."""
import json
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/"neoforge/src/main/java/org/realscale/neoforge"
DATA=ROOT/"neoforge/src/main/resources/data/realscale"
WG=DATA/"worldgen"
MOD=DATA/"neoforge/biome_modifier"
def read(path):return json.loads(path.read_text(encoding="utf-8"))

class MarineZonationQA(unittest.TestCase):
    def test_one_shared_biome_modifier_for_all_ocean_temperatures(self):
        modifier=read(MOD/"marine_ecology_layers.json")
        self.assertEqual(modifier["type"],"neoforge:add_features")
        self.assertEqual(modifier["features"],"realscale:marine_ecology_layers")
        self.assertEqual(modifier["step"],"vegetal_decoration")
        biomes=modifier["biomes"]
        self.assertEqual(len(biomes),9)
        self.assertEqual(len(set(biomes)),len(biomes))
        for name in ("warm_ocean","lukewarm_ocean","ocean","cold_ocean",
                     "frozen_ocean","deep_frozen_ocean","deep_cold_ocean",
                     "deep_ocean","deep_lukewarm_ocean"):
            self.assertIn("minecraft:"+name,biomes)
        cf=read(WG/"configured_feature/marine_ecology_layers.json")
        pf=read(WG/"placed_feature/marine_ecology_layers.json")
        self.assertEqual(cf["type"],"realscale:marine_ecology_layers")
        self.assertEqual(pf["feature"],"realscale:marine_ecology_layers")
        self.assertEqual([v["type"] for v in pf["placement"]],
            ["minecraft:count","minecraft:in_square","minecraft:heightmap","minecraft:biome"])
        self.assertEqual(pf["placement"][2]["heightmap"],"OCEAN_FLOOR_WG")
        self.assertLessEqual(pf["placement"][0]["count"],3)

    def test_feature_is_guarded_to_only_combined_usgs_ocean_world(self):
        code=(JAVA/"MarineEcologyFeature.java").read_text()
        worldguard=(JAVA/"RealScaleMod.java").read_text()
        self.assertIn('"usgs_global_hybrid_marine"',code)
        self.assertIn("noise.generatorSettings().unwrapKey()",code)
        self.assertIn("k.location().equals(ENABLED)",code)
        self.assertIn("MarineEcologyFeature()",worldguard)
        self.assertIn('"marine_ecology_layers"',worldguard)
        self.assertIn("MarineEcologyZonationMath.classify(",code)
        self.assertIn("MarineEcologyZonationMath.patch(",code)
        self.assertIn("w.getHeight(Heightmap.Types.OCEAN_FLOOR_WG",code)
        self.assertIn("w.getBiome(",code)
        self.assertIn("!original.is(Blocks.WATER)",code)
        self.assertIn("!natural(support)",code)
        self.assertIn("w.setBlock(p,target,2)",code)
        self.assertIn("pos.getY()+height>=sea-1",code)
        self.assertIn("w.setBlock(pos.above(j),plant,2)",code)
        # Each attempt restricted to the OWN originating chunk to avoid
        # cross-chunk writes and order-dependent decoration.
        self.assertIn("chunk.getMinBlockX()+random.nextInt(16)",code)
        self.assertIn("chunk.getMinBlockZ()+random.nextInt(16)",code)
        math=(JAVA/"MarineEcologyZonationMath.java").read_text()
        for kind in ("TROPICAL_REEF","TROPICAL_SEAGRASS","TEMPERATE_KELP",
                     "TEMPERATE_SEAGRASS","COLD_SPONGE_GARDEN",
                     "COLD_CORAL_GARDEN","POLAR_BENTHIC","DEEP_SILTY_FLOOR"):
            self.assertIn(kind,math)
        self.assertIn("depth>=64",math)
        self.assertIn("depth>=30",math)

    def test_no_new_world_mode_or_mutation_to_measured_noise_graph(self):
        preset=read(WG/"world_preset/tva_measured_global_landforms_three_realms.json")
        settings=preset["dimensions"]["minecraft:overworld"]["generator"]["settings"]
        self.assertEqual(settings,"realscale:usgs_global_hybrid_marine")
        historic=read(WG/"world_preset/tva_measured_biodiversity_end3_spawn3_v3.json")
        self.assertEqual(historic["dimensions"]["minecraft:overworld"]["generator"]["settings"],
                         "realscale:usgs_big_sur_v2")
        options=read(ROOT/"neoforge/src/main/resources/data/minecraft/tags/worldgen/world_preset/normal.json")["values"]
        self.assertEqual(len(options),13)
        self.assertEqual(len(set(options)),13)
        self.assertEqual(options[-1],"realscale:tva_measured_global_landforms_three_realms")
        self.assertEqual(read(WG/"noise_settings/usgs_global_hybrid_marine.json")["sea_level"],63)
        self.assertTrue((WG/"noise_settings/usgs_global_hybrid.json").is_file())
        self.assertTrue((WG/"noise_settings/usgs_big_sur_v2.json").is_file())
        self.assertTrue((WG/"world_preset/tva_sacred_timeline_three_realms.json").is_file())

if __name__=="__main__":unittest.main()
