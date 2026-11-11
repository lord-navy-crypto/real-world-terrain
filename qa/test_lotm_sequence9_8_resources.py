"""Validate the active LOTM Sequence 9/8 graph and enforce the Sequence 7 archive boundary."""
from __future__ import annotations
import importlib.util
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "neoforge/src/main/resources/data/realscale"
ASSETS = ROOT / "neoforge/src/main/resources/assets/realscale"
PATHWAYS = ("seer", "mystery_pryer", "sleepless", "corpse_collector")
MATERIALS = {
    ("seer", 9): ("lavas_squid_essence", "star_crystal"),
    ("mystery_pryer", 9): ("nightmare_eye_shard", "cursed_crystal"),
    ("sleepless", 9): ("night_owl_token", "midnight_bloom"),
    ("corpse_collector", 9): ("black_spotted_frog_token", "shroudman_crystal"),
    ("seer", 8): ("hornacis_goat_horn", "face_rose"),
    ("mystery_pryer", 8): ("ironarm_spine", "four_eyed_turtle_heart"),
    ("sleepless", 8): ("crimson_howler_voice", "soul_bellflower"),
    ("corpse_collector", 8): ("death_crow_eye", "blood_rose"),
}
ACTIVE_BLOCKS = ("star_crystal_ore", "cursed_crystal_ore", "grave_crystal_ore",
                 "midnight_bloom", "face_rose", "soul_bellflower", "blood_rose")
ARCHIVED_SEQUENCE7_VARIANTS = ("mist_treant", "evil_pattern_panther", "stag_beast",
                               "grey_grandmother", "dream_crow", "nightmare_shadow",
                               "spectral_cat")

def load(path: Path):
    assert path.is_file(), f"missing resource {path}"
    return json.loads(path.read_text(encoding="utf-8"))

class LotmDataGraphTests(unittest.TestCase):
    def test_all_sequence9_and8_promotions_have_normal_and_characteristic_recipes(self):
        for (path, seq), (a, b) in MATERIALS.items():
            normal_name = f"lotm_{path}_potion.json" if seq == 9 else f"lotm_{path}_s8_potion.json"
            normal = load(DATA / "recipe" / normal_name)
            normal_items = {x["item"] for x in normal["ingredients"]}
            self.assertIn("realscale:lotm_" + a, normal_items)
            self.assertIn("realscale:lotm_" + b, normal_items)
            self.assertIn("realscale:lotm_empty_vial", normal_items)
            self.assertIn("realscale:lotm_occult_catalyst", normal_items)
            expected = f"realscale:lotm_{path}_potion" if seq == 9 else f"realscale:lotm_{path}_s8_potion"
            self.assertEqual(expected, normal["result"]["id"])
            alternate = load(DATA / "recipe" / f"lotm_{path}_s{seq}_potion_characteristic.json")
            alternate_items = {x["item"] for x in alternate["ingredients"]}
            self.assertIn("realscale:lotm_empty_vial", alternate_items)
            self.assertIn("realscale:lotm_occult_catalyst", alternate_items)
            self.assertIn(f"realscale:lotm_{path}_s{seq}_characteristic", alternate_items)
            self.assertEqual(expected, alternate["result"]["id"])

    def test_sequence7_recipes_are_absent_but_legacy_icons_remain(self):
        for path in PATHWAYS:
            self.assertFalse((DATA / "recipe" / f"lotm_{path}_s7_potion.json").exists())
            self.assertFalse((DATA / "recipe" / f"lotm_{path}_s7_potion_characteristic.json").exists())
            for suffix in ("characteristic", "potion"):
                name = f"lotm_{path}_s7_{suffix}"
                model = load(ASSETS / "models/item" / f"{name}.json")
                self.assertEqual("realscale:item/" + name, model["textures"]["layer0"])

    def test_active_worldgen_blocks_have_complete_resource_triples(self):
        for suffix in ACTIVE_BLOCKS:
            name = f"lotm_{suffix}"
            load(ASSETS / "blockstates" / f"{name}.json")
            load(ASSETS / "models/block" / f"{name}.json")
            load(ASSETS / "models/item" / f"{name}.json")
            load(DATA / "loot_table/blocks" / f"{name}.json")
            load(DATA / "worldgen/configured_feature" / f"{name}.json")
            load(DATA / "worldgen/placed_feature" / f"{name}.json")
            load(DATA / "neoforge/biome_modifier" / f"{name}.json")
        cache = "lotm_relic_cache"
        load(ASSETS / "blockstates" / f"{cache}.json")
        load(ASSETS / "models/block" / f"{cache}.json")
        load(ASSETS / "models/item" / f"{cache}.json")
        load(DATA / "loot_table/blocks" / f"{cache}.json")
        self.assertEqual("realscale:lotm_relic_site",
            load(DATA / "worldgen/placed_feature/lotm_relic_site.json")["feature"])

    def test_sequence7_only_spirit_quartz_worldgen_is_disabled(self):
        modifier = load(DATA / "neoforge/biome_modifier/lotm_spirit_quartz_ore.json")
        self.assertEqual("neoforge:none", modifier["type"])

    def test_active_materials_are_registered(self):
        source = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/lotm/LotmContent.java").read_text(encoding="utf-8")
        for a, b in MATERIALS.values():
            self.assertIn(a, source)
            self.assertIn(b, source)
        self.assertIn('"occult_catalyst"', source)
        self.assertIn("CHARACTERISTICS.put(", source)

    def test_archived_sequence7_variants_are_not_spawn_rules(self):
        source = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/lotm/LotmEcologyRules.java").read_text(encoding="utf-8")
        for variant in ARCHIVED_SEQUENCE7_VARIANTS:
            self.assertIn('"' + variant + '"', source)
        self.assertIn("ARCHIVED_SEQUENCE7_VARIANTS", source)
        for forbidden_rule in ('return "mist_treant"', 'return "evil_pattern_panther"',
                               'return "stag_beast"', 'return "grey_grandmother"',
                               'return "dream_crow"', 'return "nightmare_shadow"',
                               'return "spectral_cat"'):
            self.assertNotIn(forbidden_rule, source)

    def test_original_art_generator_covers_active_and_archived_resources(self):
        spec = importlib.util.spec_from_file_location(
            "lotm_visuals", ROOT / "scripts/generate_lotm_visuals.py")
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        for name in ("lotm_relic_cache", "lotm_face_rose",
                     "lotm_seer_s7_characteristic", "lotm_sleepless_s8_potion"):
            blob = module.png_bytes(name)
            self.assertTrue(blob.startswith(bytes([137, 80, 78, 71, 13, 10, 26, 10])))
            self.assertGreater(len(blob), 50)

    def test_locale_and_visual_generator_cover_active_items_and_archived_ids(self):
        en = load(ASSETS / "lang/en_us.json")
        zh = load(ASSETS / "lang/zh_cn.json")
        script = (ROOT / "scripts/generate_lotm_visuals.py").read_text(encoding="utf-8")
        for path in PATHWAYS:
            for seq in (9, 8, 7):
                characteristic = f"lotm_{path}_s{seq}_characteristic"
                for lang in (en, zh):
                    self.assertIn("item.realscale." + characteristic, lang)
                self.assertIn(characteristic, script)
        for a, b in MATERIALS.values():
            self.assertIn("lotm_" + a, script)
            self.assertIn("lotm_" + b, script)

if __name__ == "__main__":
    unittest.main()
