"""Player menu stays deliberately small; internal legacy preset fixtures remain available."""
import json
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/"neoforge/src/main/resources"
TAGS=BASE/"data/minecraft/tags/worldgen/world_preset"
PRESETS=BASE/"data/realscale/worldgen/world_preset"

PLAYER_PRESETS=(
 "realscale:tva_sacred_biodiversity_end3_spawn3_v3",
 "realscale:tva_measured_global_landforms_three_realms",
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

    def test_no_separate_rare_earth_preset(self):
        self.assertFalse((PRESETS/"tva_rare_earth_ecosystems_three_realms.json").exists())

    def test_all_display_names_present(self):
        for lang in ("en_us","zh_cn"):
            labels=read(BASE/"assets/realscale/lang"/(lang+".json"))
            for name in PLAYER_PRESETS:
                label="generator."+name.replace(":","." )
                self.assertTrue(labels.get(label),label)

if __name__=="__main__":unittest.main()
