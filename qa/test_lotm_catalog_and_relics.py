"""Creative catalogue completeness, recipe discoverability and ultra-rare relic characteristics."""
from __future__ import annotations

import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "neoforge/src/main/java/org/realscale/neoforge/lotm"
DATA = ROOT / "neoforge/src/main/resources/data/realscale"
ASSETS = ROOT / "neoforge/src/main/resources/assets/realscale"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def load(path: Path):
    return json.loads(read(path))


class LotmCatalogAndRelicTests(unittest.TestCase):
    def test_every_registered_material_is_visible_in_the_creative_catalogue(self):
        content = read(JAVA / "LotmContent.java")
        tab = read(JAVA / "LotmCreativeTab.java")
        block = content.split("for (String material : new String[] {", 1)[1].split("}) material(material);", 1)[0]
        dynamic = set(re.findall(r'"([a-z0-9_]+)"', block))
        static = set(re.findall(r'MATERIALS\.put\("([a-z0-9_]+)"', content))
        material_ids = dynamic | static
        self.assertGreaterEqual(len(material_ids), 20)
        self.assertIn("LotmContent.registeredMaterials().values().forEach", tab)
        self.assertNotIn("for (String materialId : new String[]", tab)

        en = load(ASSETS / "lang/en_us.json")
        zh = load(ASSETS / "lang/zh_cn.json")
        for material in sorted(material_ids):
            item_id = "lotm_" + material
            with self.subTest(material=item_id):
                self.assertTrue((ASSETS / "models/item" / f"{item_id}.json").is_file())
                self.assertTrue(en.get("item.realscale." + item_id))
                self.assertTrue(zh.get("item.realscale." + item_id))

    def test_all_active_lotm_blocks_are_in_the_creative_catalogue(self):
        tab = read(JAVA / "LotmCreativeTab.java")
        required = (
            "ALCHEMY_APPARATUS_ITEM", "STAR_ORE_ITEM", "CURSED_ORE_ITEM",
            "GRAVE_ORE_ITEM", "LUNAR_MICA_ORE_ITEM", "ECHO_QUARTZ_ORE_ITEM",
            "ABYSSAL_SALT_ORE_ITEM", "BLOOM_ITEM", "FACE_ROSE_ITEM",
            "SOUL_BELLFLOWER_ITEM", "BLOOD_ROSE_ITEM", "SPIRIT_QUARTZ_ORE_ITEM",
            "RELIC_CACHE_ITEM",
        )
        for item in required:
            with self.subTest(item=item):
                self.assertIn(f"LotmContent.{item}.get()", tab)

    def test_player_tools_and_alchemy_apparatus_have_discoverable_recipes(self):
        expected = {
            "lotm_divination_pendulum": "realscale:lotm_divination_pendulum",
            "lotm_spirit_focus": "realscale:lotm_spirit_focus",
            "lotm_stability_sigil": "realscale:lotm_stability_sigil",
            "lotm_empty_vial": "realscale:lotm_empty_vial",
            "lotm_occult_catalyst": "realscale:lotm_occult_catalyst",
            "lotm_alchemy_apparatus": "realscale:lotm_alchemy_apparatus",
        }
        for recipe_id, result_id in expected.items():
            with self.subTest(recipe=recipe_id):
                recipe = load(DATA / "recipe" / f"{recipe_id}.json")
                self.assertEqual(result_id, recipe["result"]["id"])
                self.assertGreater(len(recipe.get("ingredients", recipe.get("key", {}))), 0)
                self.assertTrue((ASSETS / "models/item" / f"{recipe_id}.json").is_file())
        screen = read(JAVA / "client/LotmStatusScreen.java")
        self.assertIn("refreshRecipes()", screen)
        self.assertIn("recipe.getIngredients()", screen)
        self.assertIn("renderRecipeItemTooltip", screen)

    def test_relic_characteristics_are_separate_ultra_rare_rolls(self):
        structure_tables = ("dungeon", "mineshaft", "stronghold", "ancient_city")
        for structure in structure_tables:
            with self.subTest(structure=structure):
                table = load(DATA / "loot_table/lotm/structure" / f"{structure}.json")
                characteristic_pools = []
                for pool in table["pools"]:
                    entries = pool.get("entries", [])
                    is_characteristic_pool = any(
                        str(entry.get("name", "")).endswith("_characteristic")
                        for entry in entries
                    )
                    if is_characteristic_pool:
                        characteristic_pools.append(pool)
                        chance = next((c.get("chance") for c in pool.get("conditions", [])
                                       if c.get("condition") == "minecraft:random_chance"), None)
                        self.assertEqual(0.005, chance)
                    else:
                        self.assertFalse(any("_characteristic" in str(e.get("name", ""))
                                             for e in entries))
                self.assertEqual(1, len(characteristic_pools))
                names = [e["name"] for e in characteristic_pools[0]["entries"]]
                self.assertTrue(names)
                self.assertTrue(all("_s9_characteristic" in n or "_s8_characteristic" in n
                                    for n in names))
                self.assertFalse(any("_s7_" in n for n in names))
                self.assertGreater(len(table["pools"][0].get("entries", [])), 3)


if __name__ == "__main__":
    unittest.main()
