#!/usr/bin/env python3
"""6 actual underground biome ids and ore-safe source checks, not only decorations."""
import json
import unittest
from pathlib import Path

R=Path(__file__).resolve().parents[1]
DATA=R/"neoforge/src/main/resources/data/realscale"
JAVA=R/"neoforge/src/main/java/org/realscale/neoforge"
CAVES=(
    "damp_limestone_caverns","deep_fungal_caverns","dry_karst_caves",
    "underground_springs","volcanic_caverns","glacial_subsurface",
)

def load(path):return json.loads(path.read_text())

class RealCaveBiomeTests(unittest.TestCase):
    def test_six_original_cave_biomes_are_runtime_generatable(self):
        registry=(JAVA/"RealScaleMod.java").read_text()
        self.assertIn('"cave_depth_habitats"',registry)
        self.assertIn("CaveDepthBiomeSource.CODEC",registry)
        math=(JAVA/"CaveBiomeRegionMath.java").read_text()
        for biome in CAVES:
            self.assertTrue((DATA/f"worldgen/biome/{biome}.json").is_file(),biome)
            self.assertIn('"'+biome+'"',(JAVA/"CaveDepthBiomeSource.java").read_text())
        self.assertIn('original.equals("deep_dark")',math)
        self.assertIn('original.endsWith("ocean")',math)
        self.assertIn('original.contains("river")',math)
        self.assertIn("Math.floorDiv(quartX,12)",math)
        self.assertIn("Math.floorDiv(quartZ,12)",math)
        self.assertIn("Math.floorDiv(quartY,8)",math)

    def test_custom_cave_biomes_retain_vanilla_ore_carver_and_spawns(self):
        for name in CAVES:
            with self.subTest(name=name):
                biome=load(DATA/f"worldgen/biome/{name}.json")
                ore_stage=biome["features"][6]
                self.assertGreaterEqual(len(ore_stage),28)
                self.assertIn("minecraft:ore_diamond",ore_stage)
                self.assertIn("minecraft:ore_iron_middle",ore_stage)
                self.assertIn("minecraft:ore_copper",ore_stage)
                self.assertIn("minecraft:amethyst_geode",biome["features"][2])
                self.assertIn("minecraft:monster_room",biome["features"][3])
                self.assertIn("minecraft:spring_water",biome["features"][8])
                self.assertIn("minecraft:cave",biome["carvers"]["air"])
                self.assertIn("minecraft:canyon",biome["carvers"]["air"])
                self.assertTrue(biome["spawners"]["monster"])
                self.assertTrue(biome["spawners"]["ambient"])
                self.assertIn("grass_color",biome["effects"])
                self.assertIn("water_fog_color",biome["effects"])
                self.assertEqual(len(biome["features"]),11)

    def test_only_special_test_preset_remaps_cave_biomes(self):
        presets=DATA/"worldgen/world_preset"
        experiment=load(presets/"tva_cave_ocean_ecology_test_three_realms.json")
        sacred=load(presets/"tva_sacred_timeline_three_realms.json")
        e=experiment["dimensions"]["minecraft:overworld"]["generator"]
        s=sacred["dimensions"]["minecraft:overworld"]["generator"]
        self.assertEqual(e["settings"],s["settings"])
        self.assertEqual(e["type"],"minecraft:noise")
        self.assertEqual(e["biome_source"]["type"],"realscale:sky_ocean")
        self.assertEqual(e["biome_source"]["source"]["type"],"realscale:natural_ecotones")
        inside=e["biome_source"]["source"]["source"]
        self.assertEqual(inside["type"],"realscale:cave_depth_habitats")
        self.assertEqual(inside["source"],s["biome_source"]["source"]["source"])
        self.assertEqual(experiment["dimensions"]["minecraft:the_nether"],
                         sacred["dimensions"]["minecraft:the_nether"])
        self.assertEqual(experiment["dimensions"]["minecraft:the_end"],
                         sacred["dimensions"]["minecraft:the_end"])
        for standard in ("tva_sacred_timeline_three_realms","tva_measured_three_realms",
                         "tva_alpine_three_realms","tva_canyon_three_realms"):
            p=load(presets/(standard+".json"))
            self.assertNotIn("cave_depth_habitats",json.dumps(p))
        menu=load(R/"neoforge/src/main/resources/data/minecraft/tags/worldgen/world_preset/normal.json")
        self.assertEqual(menu["values"][-1],
                         "realscale:tva_cave_ocean_ecology_test_three_realms")
        self.assertEqual(len(menu["values"]),6)

    def test_warm_reef_has_waterlogged_coral_and_temperate_seagrass(self):
        java=(JAVA/"CaveMarineHabitatFeature.java").read_text()
        for coral in ("TUBE_CORAL_FAN","BRAIN_CORAL_FAN","BUBBLE_CORAL_FAN",
                      "FIRE_CORAL_FAN","HORN_CORAL_FAN"):
            self.assertIn("Blocks."+coral,java)
        self.assertIn("Blocks.SEAGRASS",java)
        self.assertIn("BlockStateProperties.WATERLOGGED",java)
        self.assertIn("plant.getFluidState().is(FluidTags.WATER)",java)
        self.assertIn("naturalSeabed(substrate)",java)
        self.assertNotIn("Blocks.SPONGE",java)

if __name__=="__main__":
    unittest.main()
