#!/usr/bin/env python3
"""Conservative RealScale cave and marine feature wiring/protection regression."""
import json
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/"neoforge/src/main/resources/data/realscale"
JAVA=ROOT/"neoforge/src/main/java/org/realscale/neoforge"
REGISTRY=(JAVA/"RealScaleMod.java").read_text()
FEATURE=(JAVA/"CaveMarineHabitatFeature.java").read_text()

CAVES={
    "cave_fungal_grottos":"CAVE_FUNGAL",
    "cave_calcite_shelves":"CAVE_CALCITE",
    "cave_volcanic_basalt":"CAVE_VOLCANIC",
    "cave_glacial_rock":"CAVE_GLACIAL",
    "cave_spring_moss":"CAVE_SPRING",
    "cave_glow_lichen":"CAVE_GLOW",
    "cave_fossil_matrix":"CAVE_FOSSIL",
}
MARINE={
    "cold_water_reef":"COLD_WATER_REEF",
    "kelp_meadow":"KELP_MEADOW",
    "warm_reef_garden":"WARM_REEF_GARDEN",
}

class CaveMarineHabitatQA(unittest.TestCase):
    def test_biome_modifiers_are_real_and_fully_registered(self):
        for name,kind in {**CAVES,**MARINE}.items():
            with self.subTest(name=name):
                conf=json.loads((DATA/f"worldgen/configured_feature/{name}.json").read_text())
                placed=json.loads((DATA/f"worldgen/placed_feature/{name}.json").read_text())
                rule=json.loads((DATA/f"neoforge/biome_modifier/{name}.json").read_text())
                self.assertEqual(conf,{"type":f"realscale:{name}","config":{}})
                self.assertEqual(placed["feature"],f"realscale:{name}")
                self.assertEqual(rule["type"],"neoforge:add_features")
                self.assertEqual(rule["features"],f"realscale:{name}")
                self.assertTrue(rule["biomes"])
                self.assertIn("new CaveMarineHabitatFeature(CaveMarineHabitatFeature.Kind."+kind+")",
                              REGISTRY)
                self.assertIn('"'+name+'"',REGISTRY)
                self.assertIn("minecraft:biome",[f["type"] for f in placed["placement"]])
                self.assertIn("minecraft:in_square",[f["type"] for f in placed["placement"]])
                if name in CAVES:
                    self.assertEqual(rule["step"],"underground_decoration")
                    self.assertIn("minecraft:height_range",
                                  [f["type"] for f in placed["placement"]])
                    self.assertFalse(any("ocean" in biome or biome.endswith(":river")
                                         for biome in rule["biomes"]))
                else:
                    self.assertEqual(rule["step"],"vegetal_decoration")
                    self.assertIn("minecraft:heightmap",
                                  [f["type"] for f in placed["placement"]])
                    self.assertTrue(all("ocean" in biome for biome in rule["biomes"]))

    def test_deep_and_highland_cave_layers_are_independently_placed(self):
        for name in CAVES:
            with self.subTest(name=name):
                low=json.loads((DATA/f"worldgen/placed_feature/{name}.json").read_text())
                highname=name+"_highland"
                high=json.loads((DATA/f"worldgen/placed_feature/{highname}.json").read_text())
                high_modifier=json.loads((DATA/f"neoforge/biome_modifier/{highname}.json").read_text())
                lowheight=next(p["height"] for p in low["placement"]
                               if p["type"]=="minecraft:height_range")
                highheight=next(p["height"] for p in high["placement"]
                               if p["type"]=="minecraft:height_range")
                self.assertEqual((lowheight["min_inclusive"]["absolute"],
                                  lowheight["max_inclusive"]["absolute"]),(-56,192))
                self.assertEqual((highheight["min_inclusive"]["absolute"],
                                  highheight["max_inclusive"]["absolute"]),(192,1472))
                self.assertEqual(high["feature"],"realscale:"+name)
                self.assertEqual(high_modifier["type"],"neoforge:add_features")
                self.assertEqual(high_modifier["features"],"realscale:"+highname)
                self.assertEqual(high_modifier["step"],"underground_decoration")
                self.assertIn("minecraft:biome",[p["type"] for p in high["placement"]])
                self.assertTrue(high_modifier["biomes"])

    def test_cold_reef_and_warm_reef_never_share_a_biome(self):
        cold=json.loads((DATA/"neoforge/biome_modifier/cold_water_reef.json").read_text())
        warm=json.loads((DATA/"neoforge/biome_modifier/warm_reef_garden.json").read_text())
        self.assertFalse(set(cold["biomes"])&set(warm["biomes"]))
        self.assertIn("minecraft:deep_cold_ocean",cold["biomes"])
        self.assertIn("minecraft:warm_ocean",warm["biomes"])

    def test_world_dimension_chunk_and_ore_guards(self):
        for token in ("TVAWorldGuard.hasRealScaleGenerator(context)",
                      '"minecraft:overworld"', "sameChunk(origin,p)",
                      "sameChunk(origin,column)", "cavePocket",
                      "surface-ROOF_DEPTH", "w.getMinBuildHeight()+4",
                      "GrandOverworldCaveNetwork.MAX_Y",
                      "Heightmap.Types.WORLD_SURFACE_WG",
                      "Heightmap.Types.OCEAN_FLOOR_WG",
                      "seabed>=seaLevel-10",
                      "FluidTags.WATER", "plant.canSurvive(w,water)",
                      "plant.getFluidState().is(FluidTags.WATER)",
                      "naturalRock", "naturalSeabed",
                      "w.getFluidState(bottom).isEmpty()"):
            self.assertIn(token,FEATURE,token)
        # The only underwater substrate decoration is in solid bed.
        self.assertIn("w.setBlock(bottom,rock,2)",FEATURE)
        self.assertNotIn("Blocks.SPONGE",FEATURE)
        self.assertNotIn("Blocks.LAVA",FEATURE)
        self.assertNotIn("Blocks.WATER.defaultBlockState()",FEATURE)
        self.assertNotIn("Blocks.AIR.defaultBlockState()",FEATURE)
        self.assertNotIn("FluidState",FEATURE.split("private static boolean naturalRock")[0])

    def test_has_no_new_cave_carver_or_noise_function(self):
        self.assertNotIn("DensityFunction",FEATURE)
        self.assertNotIn("Aquifer.",FEATURE)
        self.assertNotIn("CAVE_AIR.defaultBlockState()",FEATURE)
        self.assertNotIn("BlockState.setValue",FEATURE)
        self.assertNotIn("new Random(",FEATURE)
        for name in CAVES:
            biome=json.loads((DATA/f"neoforge/biome_modifier/{name}.json").read_text())
            self.assertTrue(biome["biomes"])

if __name__=="__main__":
    unittest.main()
