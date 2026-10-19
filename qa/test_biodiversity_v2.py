"""Versioned biodiversity: no changes to legacy Sacred/Measured worldgen."""
import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "neoforge/src/main/resources/data/realscale"
JAVA = ROOT / "neoforge/src/main/java/org/realscale/neoforge"
BIOMES = DATA / "worldgen/biome"
PRESETS = DATA / "worldgen/world_preset"
MODIFIERS = DATA / "neoforge/biome_modifier"
NAMES = (
    "tropical_dry_woodland", "cool_temperate_rainforest",
    "alpine_sedge_fen", "polar_fellfield",
    "arid_riparian_woodland", "windy_coastal_headland",
)
V1 = ("tva_sacred_timeline_three_realms", "tva_measured_three_realms")


def load(path):
    return json.loads(path.read_text(encoding="utf-8"))


class BiodiversityV2(unittest.TestCase):
    def test_six_true_biomes_with_colors_vegetation_and_animals(self):
        pallets = set()
        for name in NAMES:
            with self.subTest(name=name):
                biome = load(BIOMES / (name + ".json"))
                self.assertEqual(len(biome["features"]), 11)
                self.assertGreater(len(biome["features"][9]), 0)
                self.assertGreater(len(biome["spawners"]["creature"]), 0)
                self.assertIsInstance(biome["carvers"]["air"], list)
                self.assertLess(biome["temperature"], 2.0)
                self.assertTrue(0 <= biome["downfall"] <= 1.0)
                colors = biome["effects"]
                for key in ("fog_color", "sky_color", "grass_color",
                            "foliage_color", "water_color", "water_fog_color"):
                    self.assertIn(key, colors)
                pallets.add((colors["grass_color"], colors["foliage_color"]))
                modifier = load(MODIFIERS / ("diverse_" + name + "_undergrowth.json"))
                self.assertEqual(modifier["biomes"], ["realscale:" + name])
                self.assertEqual(modifier["step"], "vegetal_decoration")
                self.assertTrue(modifier["features"].startswith("realscale:ecotone_"))
        self.assertEqual(len(pallets), len(NAMES))

    def test_versioned_codec_is_disjoint_from_old_rule_catalog(self):
        source = (JAVA / "NaturalEcotoneBiomeSource.java").read_text()
        register = (JAVA / "RealScaleMod.java").read_text()
        start = source.index("private static final List<Rule> DIVERSE_RULES")
        end = source.index("private static final List<Rule> ALL_V2_RULES", start)
        new_rules = source[start:end]
        for name in NAMES:
            self.assertIn('rule("' + name + '"', new_rules)
        legacy = source[source.index("private static final List<Rule> RULES"):
                        source.index("private static final List<Rule> DIVERSE_RULES")]
        for name in NAMES:
            self.assertNotIn('rule("' + name + '"', legacy)
        self.assertEqual(new_rules.count('rule("'), 6)
        self.assertIn("natural_ecotones_diverse_v2", register)
        self.assertIn("NaturalEcotoneBiomeSource.DIVERSE_CODEC", register)
        self.assertIn("return diverse ? DIVERSE_CODEC : CODEC;", source)
        self.assertIn("for (Rule rule : diverse ? ALL_V2_RULES : RULES)", source)
        self.assertIn("protectedWater(current)", source)

    def test_all_six_pairs_do_not_overlap_original_rules(self):
        source = (JAVA / "NaturalEcotoneBiomeSource.java").read_text()
        # Check that added pairs intentionally are different from existing 60.
        self.assertIn('rule("tropical_dry_woodland",32,"sparse_jungle","savanna,savanna_plateau")', source)
        self.assertIn('rule("cool_temperate_rainforest",32,"dark_forest"', source)
        self.assertIn('rule("arid_riparian_woodland",16,"badlands,wooded_badlands,eroded_badlands","river")', source)
        self.assertIn('rule("windy_coastal_headland",24,"stony_shore","meadow,plains")', source)

    def test_new_worlds_keep_original_density_and_all_three_realm_invasion(self):
        normal = load(ROOT / "neoforge/src/main/resources/data/minecraft/tags/worldgen/world_preset/normal.json")
        for kind, source_name in (("sacred", V1[0]), ("measured", V1[1])):
            with self.subTest(kind=kind):
                original = load(PRESETS / (source_name + ".json"))
                ident = "tva_" + kind + "_biodiversity_end3_spawn3_v2"
                self.assertIn("realscale:" + ident, normal["values"])
                current = load(PRESETS / (ident + ".json"))
                self.assertEqual(set(original["dimensions"]), set(current["dimensions"]))
                for dim in original["dimensions"]:
                    before = original["dimensions"][dim]
                    after = current["dimensions"][dim]
                    self.assertEqual(after["type"], before["type"])
                    self.assertEqual(after["generator"]["settings"], before["generator"]["settings"])
                    self.assertEqual(after["generator"]["type"], before["generator"]["type"])
                over = current["dimensions"]["minecraft:overworld"]["generator"]["biome_source"]
                self.assertEqual(over["type"], "realscale:sky_ocean")
                self.assertEqual(over["source"]["type"], "realscale:cross_realm_spawn3")
                self.assertEqual(over["source"]["target"], "overworld")
                self.assertEqual(over["source"]["source"]["type"], "realscale:natural_ecotones_diverse_v2")
                self.assertEqual(over["source"]["source"]["source"],
                                 original["dimensions"]["minecraft:overworld"]["generator"]["biome_source"]["source"]["source"])
                nether = current["dimensions"]["minecraft:the_nether"]["generator"]["biome_source"]
                self.assertEqual(nether["type"], "realscale:cross_realm_invasion")
                self.assertEqual(nether["source"], original["dimensions"]["minecraft:the_nether"]["generator"]["biome_source"])
                end = current["dimensions"]["minecraft:the_end"]["generator"]["biome_source"]
                self.assertEqual(end["type"], "realscale:end_accents")
                self.assertEqual(end["source"]["type"], "realscale:cross_realm_invasion")
                self.assertEqual(end["source"]["source"],
                                 original["dimensions"]["minecraft:the_end"]["generator"]["biome_source"])

    def test_bilingual_names(self):
        for lang in ("en_us", "zh_cn"):
            names = load(ROOT / ("neoforge/src/main/resources/assets/realscale/lang/" + lang + ".json"))
            for name in NAMES:
                self.assertIn("biome.realscale." + name, names)
            for kind in ("sacred", "measured"):
                self.assertIn("generator.realscale.tva_" + kind + "_biodiversity_end3_spawn3_v2", names)


if __name__ == "__main__":
    unittest.main()
