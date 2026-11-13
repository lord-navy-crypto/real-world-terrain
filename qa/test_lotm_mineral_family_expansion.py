"""LOTM S9–S8 mineral family expansion: full resource + gameplay graph contract."""
from __future__ import annotations

import importlib.util
import json
import struct
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "neoforge/src/main/resources"
DATA = RESOURCES / "data/realscale"
ASSETS = RESOURCES / "assets/realscale"
CONTENT = ROOT / "neoforge/src/main/java/org/realscale/neoforge/lotm/LotmContent.java"
VISUALS = ROOT / "scripts/generate_lotm_visuals.py"

# S9/S8 materials only. Exact limits are design targets, not measured frequency.
FAMILIES = {
    "lunar_mica": {
        "biomes": ["minecraft:taiga", "minecraft:snowy_taiga",
                   "minecraft:old_growth_pine_taiga", "realscale:subalpine_woodland"],
        "size": 4, "count": 2, "low": -32, "high": 48,
        "recipe": "lotm_spirit_focus_lunar", "output": "lotm_spirit_focus",
        "quantity": 1,
    },
    "echo_quartz": {
        "biomes": ["minecraft:dripstone_caves", "minecraft:stony_peaks",
                   "minecraft:jagged_peaks", "minecraft:windswept_hills",
                   "realscale:rocky_alpine_meadow"],
        "size": 4, "count": 2, "low": -56, "high": 24,
        "recipe": "lotm_occult_catalyst_echo", "output": "lotm_occult_catalyst",
        "quantity": 3,
    },
    "abyssal_salt": {
        "biomes": ["minecraft:deep_ocean", "minecraft:deep_cold_ocean",
                   "minecraft:deep_lukewarm_ocean"],
        "size": 3, "count": 2, "low": -56, "high": -8,
        "recipe": "lotm_spirit_ember_abyssal", "output": "lotm_spirit_ember",
        "quantity": 3,
    },
}


def load_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


class LotmMineralExpansionTests(unittest.TestCase):
    def test_new_families_have_registered_blocks_items_and_pickaxe_mining(self):
        source = CONTENT.read_text(encoding="utf-8")
        tag = load_json(RESOURCES / "data/minecraft/tags/block/mineable/pickaxe.json")
        self.assertFalse(tag["replace"])
        for name in FAMILIES:
            with self.subTest(name=name):
                block = f"lotm_{name}_ore"
                item = f"lotm_{name}"
                self.assertIn(f'BLOCKS.register("{block}"', source)
                self.assertIn(f'ITEMS.registerSimpleBlockItem("{block}"', source)
                self.assertIn(f'ITEMS.registerSimpleItem("{item}"', source)
                self.assertIn(f"realscale:{block}", tag["values"])
                start = source.index(f'BLOCKS.register("{block}"')
                self.assertIn(".requiresCorrectToolForDrops()", source[start:start + 240])

    def test_worldgen_is_biome_scoped_and_geologically_distinct(self):
        for name, profile in FAMILIES.items():
            with self.subTest(name=name):
                configured = load_json(DATA / "worldgen/configured_feature" / f"lotm_{name}_ore.json")
                placed = load_json(DATA / "worldgen/placed_feature" / f"lotm_{name}_ore.json")
                modifier = load_json(DATA / "neoforge/biome_modifier" / f"lotm_{name}_ore.json")
                self.assertEqual("minecraft:ore", configured["type"])
                self.assertEqual(profile["size"], configured["config"]["size"])
                targets = configured["config"]["targets"]
                self.assertEqual(
                    {"minecraft:stone_ore_replaceables", "minecraft:deepslate_ore_replaceables"},
                    {t["target"]["tag"] for t in targets})
                self.assertEqual(
                    {f"realscale:lotm_{name}_ore"},
                    {t["state"]["Name"] for t in targets})
                self.assertEqual(f"realscale:lotm_{name}_ore", placed["feature"])
                placements = {p["type"]: p for p in placed["placement"]}
                self.assertEqual(profile["count"], placements["minecraft:count"]["count"])
                self.assertIn("minecraft:in_square", placements)
                self.assertIn("minecraft:biome", placements)
                height = placements["minecraft:height_range"]["height"]
                self.assertEqual("minecraft:uniform", height["type"])
                self.assertEqual(profile["low"], height["min_inclusive"]["absolute"])
                self.assertEqual(profile["high"], height["max_inclusive"]["absolute"])
                self.assertEqual("neoforge:add_features", modifier["type"])
                self.assertEqual(profile["biomes"], modifier["biomes"])
                self.assertEqual(f"realscale:lotm_{name}_ore", modifier["features"])
                self.assertEqual("underground_ores", modifier["step"])

    def test_mining_drops_follow_silk_touch_fortune_and_explosion_contract(self):
        for name in FAMILIES:
            with self.subTest(name=name):
                loot = load_json(DATA / "loot_table/blocks" / f"lotm_{name}_ore.json")
                self.assertEqual("minecraft:block", loot["type"])
                entry = loot["pools"][0]["entries"][0]
                self.assertEqual("minecraft:alternatives", entry["type"])
                silk, raw = entry["children"]
                self.assertEqual(f"realscale:lotm_{name}_ore", silk["name"])
                conditions = silk["conditions"]
                self.assertEqual("minecraft:match_tool", conditions[0]["condition"])
                ench = conditions[0]["predicate"]["predicates"]["minecraft:enchantments"]
                self.assertEqual("minecraft:silk_touch", ench[0]["enchantments"])
                self.assertEqual(f"realscale:lotm_{name}", raw["name"])
                fortune, explosion = raw["functions"]
                self.assertEqual("minecraft:apply_bonus", fortune["function"])
                self.assertEqual("minecraft:fortune", fortune["enchantment"])
                self.assertEqual("minecraft:ore_drops", fortune["formula"])
                self.assertEqual("minecraft:explosion_decay", explosion["function"])

    def test_materials_have_bilingual_names_models_and_original_texture_generation(self):
        en = load_json(ASSETS / "lang/en_us.json")
        zh = load_json(ASSETS / "lang/zh_cn.json")
        spec = importlib.util.spec_from_file_location("lotm_visuals", VISUALS)
        module = importlib.util.module_from_spec(spec)
        assert spec.loader is not None
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as tmp:
            generated = Path(tmp)
            module.generate(generated)
            signatures = set()
            for name in FAMILIES:
                for suffix, kind, key in (
                    (f"lotm_{name}", "item", "item"),
                    (f"lotm_{name}_ore", "block", "block"),
                ):
                    with self.subTest(item=suffix):
                        self.assertIn(f"{key}.realscale.{suffix}", en)
                        self.assertIn(f"{key}.realscale.{suffix}", zh)
                        self.assertIn(suffix, module.ITEMS if kind == "item" else module.BLOCKS)
                        png = generated / f"assets/realscale/textures/{kind}/{suffix}.png"
                        texture = png.read_bytes()
                        self.assertTrue(texture.startswith(bytes((137, 80, 78, 71, 13, 10, 26, 10))))
                        self.assertEqual((16, 16), struct.unpack(">II", texture[16:24]))
                        self.assertNotIn(texture, signatures, "each ore/item texture should be unique")
                        signatures.add(texture)

                ore = f"lotm_{name}_ore"
                blockstate = load_json(ASSETS / "blockstates" / f"{ore}.json")
                self.assertEqual(f"realscale:block/{ore}",
                                 blockstate["variants"][""]["model"])
                blockmodel = load_json(ASSETS / "models/block" / f"{ore}.json")
                self.assertEqual(f"realscale:block/{ore}", blockmodel["textures"]["all"])
                blockitem = load_json(ASSETS / "models/item" / f"{ore}.json")
                self.assertEqual(f"realscale:block/{ore}", blockitem["parent"])
                materialitem = load_json(ASSETS / "models/item" / f"lotm_{name}.json")
                self.assertEqual(f"realscale:item/lotm_{name}", materialitem["textures"]["layer0"])

    def test_each_material_is_consumed_by_its_additive_recipe(self):
        expected_ingredients = {
            "lunar_mica": {"realscale:lotm_lunar_mica", "minecraft:amethyst_shard"},
            "echo_quartz": {"realscale:lotm_echo_quartz", "minecraft:lapis_lazuli", "minecraft:paper"},
            "abyssal_salt": {"realscale:lotm_abyssal_salt", "minecraft:glowstone_dust", "minecraft:redstone"},
        }
        for name, profile in FAMILIES.items():
            with self.subTest(name=name):
                data = load_json(DATA / "recipe" / f"{profile['recipe']}.json")
                self.assertEqual(f"realscale:{profile['output']}", data["result"]["id"])
                self.assertEqual(profile["quantity"], data["result"]["count"])
                if data["type"] == "minecraft:crafting_shapeless":
                    actual = {ing["item"] for ing in data["ingredients"]}
                else:
                    self.assertEqual("minecraft:crafting_shaped", data["type"])
                    actual = {ing["item"] for ing in data["key"].values()}
                    self.assertEqual([" A ", "ALA", " A "], data["pattern"])
                self.assertEqual(expected_ingredients[name], actual)

    def test_existing_recipes_and_sealed_s7_are_preserved(self):
        existing = ["lotm_spirit_focus", "lotm_occult_catalyst", "lotm_spirit_ember"]
        for name in existing:
            self.assertTrue((DATA / "recipe" / f"{name}.json").is_file(), name)
        archive = load_json(DATA / "neoforge/biome_modifier/lotm_spirit_quartz_ore.json")
        self.assertEqual({"type": "neoforge:none"}, archive)
        self.assertFalse((DATA / "recipe/lotm_seer_s7_potion.json").exists())


if __name__ == "__main__":
    unittest.main()
