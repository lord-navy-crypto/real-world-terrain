"""Player menu stays deliberately small; internal legacy preset fixtures remain available."""
import json
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/"neoforge/src/main/resources"
TAGS=BASE/"data/minecraft/tags/worldgen/world_preset"
PRESETS=BASE/"data/realscale/worldgen/world_preset"

PLAYER_PRESETS=(
 "realscale:tva_natural_hypsometry_v1_three_realms",
 "realscale:tva_measured_usgs_fitted_v1_three_realms",
 "realscale:tva_alpine_three_realms",
 "realscale:tva_canyon_three_realms",
)

def read(path):return json.loads(path.read_text(encoding="utf-8"))

class PlayerWorldPresets(unittest.TestCase):
    def test_only_four_playable_modes(self):
        for tag in ("normal","extended"):
            data=read(TAGS/(tag+".json"))
            self.assertEqual(tuple(data["values"]), PLAYER_PRESETS)
            self.assertFalse(data["replace"])
            self.assertEqual(len(data["values"]),len(set(data["values"])))

    def test_each_playable_mode_has_real_ecology(self):
        for name in PLAYER_PRESETS:
            with self.subTest(name=name):
                data=read(PRESETS/(name.split(":")[1]+".json"))
                dims=data["dimensions"]
                self.assertEqual(set(dims),{"minecraft:overworld",
                                            "minecraft:the_nether",
                                            "minecraft:the_end"})
                source=dims["minecraft:overworld"]["generator"]["biome_source"]
                self.assertEqual(source["type"],"realscale:sky_ocean")
                self.assertEqual(source["source"]["type"],
                                 "realscale:rare_earth_biomes_v1")
                self.assertTrue(dims["minecraft:overworld"]["generator"]["settings"].startswith("realscale:"))

    def test_geological_wonders_remain_playable(self):
        # Former wonders-only mode is now incorporated in Natural Exploration.
        data=read(PRESETS/"tva_natural_hypsometry_v1_three_realms.json")
        source=data["dimensions"]["minecraft:overworld"]["generator"]["biome_source"]
        self.assertEqual(source["type"],"realscale:sky_ocean")
        self.assertEqual(source["source"]["type"],"realscale:rare_earth_biomes_v1")
        self.assertEqual(source["source"]["source"]["type"],"realscale:geo_wonders_opt_in")
        self.assertEqual(source["source"]["source"]["source"]["type"],"realscale:cross_realm_free_spawn_v1")
        self.assertEqual(source["source"]["source"]["source"]["target"],"overworld")

    def test_four_modes_restore_all_old_worldgen_layers(self):
        expected_noise={
            "tva_natural_hypsometry_v1_three_realms":"realscale:tva_natural_hypsometry_v1",
            "tva_measured_usgs_fitted_v1_three_realms":"realscale:usgs_fitted_continuation_v1",
            "tva_alpine_three_realms":"realscale:tva_alpine_smoothed",
            "tva_canyon_three_realms":"realscale:tva_canyon_smoothed",
        }
        for raw,noise in expected_noise.items():
            with self.subTest(preset=raw):
                dims=read(PRESETS/(raw+".json"))["dimensions"]
                self.assertEqual(dims["minecraft:overworld"]["generator"]["settings"],noise)
                source=dims["minecraft:overworld"]["generator"]["biome_source"]
                types=[]
                node=source
                while isinstance(node,dict) and "type" in node:
                    types.append(node["type"])
                    node=node.get("source")
                self.assertEqual(types,[
                    "realscale:sky_ocean",
                    "realscale:rare_earth_biomes_v1",
                    "realscale:geo_wonders_opt_in",
                    "realscale:cross_realm_free_spawn_v1",
                    "realscale:natural_ecotones_diverse_v3",
                    "minecraft:multi_noise"
                ])
                self.assertEqual(dims["minecraft:the_nether"]["generator"]["biome_source"]["type"],
                                 "realscale:cross_realm_invasion")
                end=dims["minecraft:the_end"]["generator"]["biome_source"]
                self.assertEqual(end["type"],"realscale:end_accents")
                self.assertEqual(end["source"]["type"],"realscale:cross_realm_invasion")
                self.assertEqual(end["source"]["source"]["type"],"minecraft:the_end")

    def test_original_scenic_landscape_features(self):
        java=(ROOT/"neoforge/src/main/java/org/realscale/neoforge/ScenicLandscapeFeature.java").read_text()
        registration=(ROOT/"neoforge/src/main/java/org/realscale/neoforge/RealScaleMod.java").read_text()
        entries={
            "scenic_rainforest_terrace":"WORLD_SURFACE_WG",
            "scenic_misty_coast":"WORLD_SURFACE_WG",
            "scenic_basalt_upland":"WORLD_SURFACE_WG",
            "scenic_underwater_arch":"OCEAN_FLOOR_WG",
        }
        for name,heightmap in entries.items():
            with self.subTest(name=name):
                cfg=read(BASE/"data/realscale/worldgen/configured_feature"/(name+".json"))
                placed=read(BASE/"data/realscale/worldgen/placed_feature"/(name+".json"))
                modifier=read(BASE/"data/realscale/neoforge/biome_modifier"/(name+".json"))
                self.assertEqual(cfg["type"],"realscale:"+name)
                self.assertEqual(placed["feature"],"realscale:"+name)
                self.assertEqual(placed["placement"][2]["heightmap"],heightmap)
                self.assertEqual(modifier["features"],"realscale:"+name)
                self.assertEqual(modifier["step"],"vegetal_decoration")
                self.assertIn('"'+name+'"',registration)
                self.assertTrue(modifier["biomes"])
        for guard in ("TVAWorldGuard.hasRealScaleGenerator","Level.OVERWORLD",
                      "owns(chunk,p)","existing.is(Blocks.WATER)",
                      "blocks.size()>45","w.setBlock(block.getKey(),block.getValue(),2)"):
            self.assertIn(guard,java)
        self.assertNotIn("CAVE_AIR.defaultBlockState()",java)
        self.assertNotIn("LAVA.defaultBlockState()",java)

    def test_no_separate_rare_earth_preset(self):
        self.assertFalse((PRESETS/"tva_rare_earth_ecosystems_three_realms.json").exists())

    def test_all_display_names_present(self):
        for lang in ("en_us","zh_cn"):
            labels=read(BASE/"assets/realscale/lang"/(lang+".json"))
            for name in PLAYER_PRESETS:
                label="generator."+name.replace(":","." )
                self.assertTrue(labels.get(label),label)

if __name__=="__main__":unittest.main()
