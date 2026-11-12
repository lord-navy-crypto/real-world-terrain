"""Validate original, complete GeckoLib assets for all ten independent LOTM creatures."""
from __future__ import annotations

import hashlib
import importlib.util
import json
import struct
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GENERATOR = ROOT / "scripts/generate_lotm_visuals.py"
CREATURE_IDS = (
    "starlit_squid",
    "nightmare_eye",
    "night_owl",
    "black_spotted_frog",
    "shroudman",
    "hornacis_goat",
    "ironarm_beast",
    "four_eyed_turtle",
    "crimson_howler",
    "death_crow",
)


def load_generator():
    spec = importlib.util.spec_from_file_location("lotm_visuals", GENERATOR)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


class LotmCreatureAssetTests(unittest.TestCase):
    def test_generator_declares_the_complete_active_creature_roster(self):
        module = load_generator()
        self.assertEqual(CREATURE_IDS, tuple(module.CREATURES.keys()))

    def test_generator_emits_unique_64px_textures_models_and_animations(self):
        module = load_generator()
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp)
            module.generate(output)
            asset_root = output / "assets" / "realscale"
            texture_hashes = set()

            for creature_id in CREATURE_IDS:
                with self.subTest(creature=creature_id):
                    texture_path = asset_root / "textures" / "entity" / f"lotm_{creature_id}.png"
                    geometry_path = asset_root / "geo" / f"lotm_{creature_id}.geo.json"
                    animation_path = asset_root / "animations" / f"lotm_{creature_id}.animation.json"

                    self.assertTrue(texture_path.is_file(), f"missing texture for {creature_id}")
                    self.assertTrue(geometry_path.is_file(), f"missing geometry for {creature_id}")
                    self.assertTrue(animation_path.is_file(), f"missing animations for {creature_id}")

                    texture = texture_path.read_bytes()
                    self.assertTrue(texture.startswith(bytes((137, 80, 78, 71, 13, 10, 26, 10))))
                    self.assertEqual((64, 64), struct.unpack(">II", texture[16:24]))
                    digest = hashlib.sha256(texture).hexdigest()
                    self.assertNotIn(digest, texture_hashes, "creature textures must be distinct")
                    texture_hashes.add(digest)

                    geometry = json.loads(geometry_path.read_text(encoding="utf-8"))
                    self.assertIn("minecraft:geometry", geometry)
                    bones = geometry["minecraft:geometry"][0]["bones"]
                    bone_names = {bone["name"] for bone in bones}
                    self.assertIn("root", bone_names)
                    self.assertGreaterEqual(len(bone_names), 5)
                    self.assertEqual(len(bone_names), len(bones), "bone names must be unique")

                    animations = json.loads(animation_path.read_text(encoding="utf-8"))["animations"]
                    for action in ("idle", "walk", "attack", "hurt", "death"):
                        animation_name = f"animation.{creature_id}.{action}"
                        self.assertIn(animation_name, animations)
                        animation_bones = set(animations[animation_name].get("bones", {}))
                        self.assertTrue(animation_bones.issubset(bone_names),
                                        f"{animation_name} references an unknown bone")


    def test_registered_creature_entity_ids_have_bilingual_names(self):
        en = json.loads((ROOT / "neoforge/src/main/resources/assets/realscale/lang/en_us.json").read_text(encoding="utf-8"))
        zh = json.loads((ROOT / "neoforge/src/main/resources/assets/realscale/lang/zh_cn.json").read_text(encoding="utf-8"))
        for creature_id in CREATURE_IDS:
            key = "entity.realscale.lotm_" + creature_id
            self.assertIn(key, en)
            self.assertIn(key, zh)

    def test_client_renderer_resolves_each_species_asset_bundle(self):
        model = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/lotm/client/LotmCreatureModel.java").read_text(encoding="utf-8")
        client = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/lotm/client/LotmClientEvents.java").read_text(encoding="utf-8")
        creature = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/lotm/LotmCreature.java").read_text(encoding="utf-8")
        self.assertIn("geo/lotm_", model)
        self.assertIn("textures/entity/lotm_", model)
        self.assertIn("animations/lotm_", model)
        self.assertIn("registerEntityRenderer", client)
        self.assertIn('String prefix = "animation." + kind.id() + "."', creature)
        self.assertIn('triggerableAnim("attack"', creature)
        self.assertIn('triggerableAnim("hurt"', creature)

    def test_creature_geometry_has_species_specific_identifiers(self):
        module = load_generator()
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp)
            module.generate(output)
            geo_root = output / "assets" / "realscale" / "geo"
            identifiers = set()
            for creature_id in CREATURE_IDS:
                data = json.loads((geo_root / f"lotm_{creature_id}.geo.json").read_text(encoding="utf-8"))
                identifiers.add(data["minecraft:geometry"][0]["description"]["identifier"])
            self.assertEqual(len(CREATURE_IDS), len(identifiers))
            self.assertEqual(
                {f"geometry.realscale.lotm_{creature_id}" for creature_id in CREATURE_IDS},
                identifiers,
            )


    def test_each_active_creature_has_a_habitat_limited_natural_spawn_modifier(self):
        expected = {
            "starlit_squid": (["minecraft:deep_ocean", "minecraft:deep_cold_ocean", "minecraft:deep_lukewarm_ocean", "minecraft:deep_frozen_ocean"], 4, 1, 2),
            "nightmare_eye": (["minecraft:dark_forest", "realscale:oak_to_dark_forest", "realscale:pine_oak_mosaic"], 3, 1, 2),
            "night_owl": (["minecraft:forest", "minecraft:dark_forest", "minecraft:birch_forest", "minecraft:taiga", "minecraft:old_growth_spruce_taiga", "minecraft:old_growth_pine_taiga", "realscale:oak_to_dark_forest", "realscale:pine_oak_mosaic", "realscale:subalpine_woodland", "realscale:woodland_meadow"], 3, 1, 2),
            "black_spotted_frog": (["minecraft:swamp", "minecraft:mangrove_swamp", "realscale:marshy_woodland", "realscale:seasonal_floodforest", "realscale:reed_marsh_edge", "realscale:mountain_wetland"], 4, 1, 3),
            "shroudman": (["minecraft:swamp", "minecraft:mangrove_swamp", "realscale:marshy_woodland", "realscale:seasonal_floodforest", "realscale:reed_marsh_edge"], 2, 1, 2),
            "hornacis_goat": (["minecraft:stony_peaks", "minecraft:jagged_peaks", "minecraft:windswept_hills", "minecraft:meadow", "realscale:rocky_alpine_meadow", "realscale:montane_spruce_meadow", "realscale:subalpine_woodland"], 3, 1, 3),
            "ironarm_beast": (["minecraft:desert", "minecraft:badlands", "minecraft:wooded_badlands"], 2, 1, 2),
            "four_eyed_turtle": (["minecraft:beach", "minecraft:snowy_beach", "minecraft:stony_shore", "realscale:coastal_dune_meadow", "realscale:gravel_shore_fringe", "realscale:coastal_heath"], 3, 1, 2),
            "crimson_howler": (["minecraft:forest", "minecraft:dark_forest", "minecraft:birch_forest", "minecraft:taiga", "minecraft:old_growth_spruce_taiga", "minecraft:old_growth_pine_taiga", "realscale:oak_to_dark_forest", "realscale:pine_oak_mosaic", "realscale:woodland_meadow"], 3, 1, 3),
            "death_crow": (["minecraft:jungle", "minecraft:sparse_jungle", "minecraft:bamboo_jungle", "realscale:tropical_littoral_forest"], 2, 1, 3),
        }
        self.assertEqual(set(CREATURE_IDS), set(expected))
        for creature_id, (biomes, weight, minimum, maximum) in expected.items():
            path = ROOT / "neoforge/src/main/resources/data/realscale/neoforge/biome_modifier" / f"lotm_{creature_id}_spawns.json"
            data = json.loads(path.read_text(encoding="utf-8"))
            with self.subTest(creature=creature_id):
                self.assertEqual("neoforge:add_spawns", data["type"])
                self.assertEqual(biomes, data["biomes"])
                self.assertEqual([{
                    "type": f"realscale:lotm_{creature_id}",
                    "weight": weight,
                    "minCount": minimum,
                    "maxCount": maximum,
                }], data["spawners"])

    def test_custom_relic_site_generation_is_disabled(self):
        path = ROOT / "neoforge/src/main/resources/data/realscale/neoforge/biome_modifier/lotm_relic_site.json"
        self.assertEqual({"type": "neoforge:none"}, json.loads(path.read_text(encoding="utf-8")))


    def test_vanilla_structure_loot_is_additive_and_scoped(self):
        data_root = ROOT / "neoforge/src/main/resources/data"
        global_path = data_root / "neoforge/loot_modifiers/global_loot_modifiers.json"
        global_data = json.loads(global_path.read_text(encoding="utf-8"))
        self.assertFalse(global_data["replace"])
        expected = {
            "lotm_dungeon_loot": ("minecraft:chests/simple_dungeon", "realscale:lotm/structure/dungeon"),
            "lotm_mineshaft_loot": ("minecraft:chests/abandoned_mineshaft", "realscale:lotm/structure/mineshaft"),
            "lotm_stronghold_loot": ("minecraft:chests/stronghold_corridor", "realscale:lotm/structure/stronghold"),
            "lotm_ancient_city_loot": ("minecraft:chests/ancient_city", "realscale:lotm/structure/ancient_city"),
        }
        self.assertEqual({f"realscale:{name}" for name in expected}, set(global_data["entries"]))
        for name, (vanilla_table, custom_table) in expected.items():
            modifier_path = data_root / "realscale/loot_modifiers" / f"{name}.json"
            modifier = json.loads(modifier_path.read_text(encoding="utf-8"))
            conditions = modifier["conditions"]
            table_condition = next(c for c in conditions if c["condition"] == "neoforge:loot_table_id")
            chance_condition = next(c for c in conditions if c["condition"] == "minecraft:random_chance")
            self.assertEqual("neoforge:add_table", modifier["type"])
            self.assertEqual(vanilla_table, table_condition["loot_table_id"])
            self.assertEqual(custom_table, modifier["table"])
            self.assertGreater(chance_condition["chance"], 0.0)
            self.assertLess(chance_condition["chance"], 0.15)

            table_path = data_root / "realscale/loot_table" / (custom_table.split(":", 1)[1] + ".json")
            loot_table = json.loads(table_path.read_text(encoding="utf-8"))
            self.assertEqual("minecraft:chest", loot_table["type"])
            entries = [entry for pool in loot_table["pools"] for entry in pool["entries"]]
            item_ids = {entry["name"] for entry in entries if entry.get("type") == "minecraft:item"}
            self.assertTrue(item_ids)
            self.assertTrue(all(item_id.startswith("realscale:lotm_") for item_id in item_ids))



if __name__ == "__main__":
    unittest.main()
