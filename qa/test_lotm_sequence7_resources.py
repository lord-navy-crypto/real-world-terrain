"""Guard the entire low-sequence LOTM content graph without launching Minecraft."""
from __future__ import annotations
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "neoforge/src/main/resources/data/realscale"
ASSETS = ROOT / "neoforge/src/main/resources/assets/realscale"
PATHWAYS = ("seer", "mystery_pryer", "sleepless", "corpse_collector")
MATERIALS = {
 ("seer", 8): ("hornacis_goat_horn", "face_rose"),
 ("seer", 7): ("mist_treant_root", "evil_pattern_panther_essence"),
 ("mystery_pryer", 8): ("ironarm_spine", "four_eyed_turtle_heart"),
 ("mystery_pryer", 7): ("stag_beast_antler", "grey_grandmother_eye"),
 ("sleepless", 8): ("crimson_howler_voice", "soul_bellflower"),
 ("sleepless", 7): ("dream_crow_heart", "nightmare_shadow_core"),
 ("corpse_collector", 8): ("death_crow_eye", "blood_rose"),
 ("corpse_collector", 7): ("spectral_cat_paw", "spirit_quartz"),
}
CUSTOM_BLOCKS = ("face_rose", "soul_bellflower", "blood_rose",
                 "spirit_quartz_ore", "relic_cache")

def load(path: Path):
    assert path.is_file(), f"missing resource {path}"
    return json.loads(path.read_text(encoding="utf-8"))

class LotmDataGraphTests(unittest.TestCase):
    def test_all_promotions_have_real_ingredients(self):
        for (path, seq), (a, b) in MATERIALS.items():
            recipe = load(DATA / "recipe" / f"lotm_{path}_s{seq}_potion.json")
            items = {x["item"] for x in recipe["ingredients"]}
            self.assertIn("realscale:lotm_" + a, items)
            self.assertIn("realscale:lotm_" + b, items)
            self.assertIn("realscale:lotm_empty_vial", items)
            self.assertIn("realscale:lotm_occult_catalyst", items)
            self.assertEqual(f"realscale:lotm_{path}_s{seq}_potion", recipe["result"]["id"])

    def test_characteristics_not_craftable_and_all_have_icons(self):
        for path in PATHWAYS:
            for seq in (9, 8, 7):
                name = f"lotm_{path}_s{seq}_characteristic"
                self.assertFalse((DATA / "recipe" / f"{name}.json").exists())
                model = load(ASSETS / "models/item" / f"{name}.json")
                self.assertEqual("realscale:item/" + name, model["textures"]["layer0"])

    def test_new_biome_blocks_have_complete_resource_triples(self):
        for suffix in CUSTOM_BLOCKS:
            name = f"lotm_{suffix}"
            load(ASSETS / "blockstates" / f"{name}.json")
            load(ASSETS / "models/block" / f"{name}.json")
            load(ASSETS / "models/item" / f"{name}.json")
            load(DATA / "loot_table/blocks" / f"{name}.json")
            if suffix != "relic_cache":
                load(DATA / "worldgen/configured_feature" / f"{name}.json")
                load(DATA / "worldgen/placed_feature" / f"{name}.json")
                load(DATA / "neoforge/biome_modifier" / f"{name}.json")
        self.assertEqual("realscale:lotm_relic_site",
            load(DATA / "worldgen/placed_feature/lotm_relic_site.json")["feature"])

    def test_materials_are_registered_and_poison_free_game_abstractions(self):
        source = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/lotm/LotmContent.java").read_text(encoding="utf-8")
        for a, b in MATERIALS.values():
            for suffix in (a, b):
                if suffix in ("face_rose", "soul_bellflower", "blood_rose"):
                    self.assertIn('BLOCKS.register("lotm_' + suffix + '"', source)
                else:
                    self.assertIn('"' + suffix + '"', source)
        self.assertIn('"occult_catalyst"', source)
        self.assertIn('CHARACTERISTICS.put(', source)

    def test_original_art_generator_runs_for_new_items(self):
        import importlib.util
        spec = importlib.util.spec_from_file_location(
            "lotm_visuals", ROOT / "scripts/generate_lotm_visuals.py")
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        for name in ("lotm_relic_cache", "lotm_face_rose",
                     "lotm_seer_s7_characteristic", "lotm_sleepless_s8_potion"):
            blob = module.png_bytes(name)
            self.assertTrue(blob.startswith(b"\\x89PNG\\r\\n\\x1a\\n"))
            self.assertGreater(len(blob), 50)

    def test_locale_and_image_generator_cover_all_new_items(self):
        en = load(ASSETS / "lang/en_us.json")
        zh = load(ASSETS / "lang/zh_cn.json")
        script = (ROOT / "scripts/generate_lotm_visuals.py").read_text(encoding="utf-8")
        for path in PATHWAYS:
            for seq in (9, 8, 7):
                ch = f"lotm_{path}_s{seq}_characteristic"
                for lang in (en, zh):
                    self.assertIn("item.realscale." + ch, lang)
                self.assertIn(ch, script)
        for a, b in MATERIALS.values():
            for suffix in (a, b):
                item_or_block = f"lotm_{suffix}"
                self.assertIn(item_or_block, script)

if __name__ == "__main__":
    unittest.main()
