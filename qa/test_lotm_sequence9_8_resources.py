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
            self.assertEqual("realscale:lotm_alchemy", normal["type"])
            normal_items = {x["item"] for x in normal["ingredients"]}
            self.assertIn("realscale:lotm_" + a, normal_items)
            self.assertIn("realscale:lotm_" + b, normal_items)
            self.assertIn("realscale:lotm_empty_vial", normal_items)
            self.assertIn("realscale:lotm_occult_catalyst", normal_items)
            expected = f"realscale:lotm_{path}_potion" if seq == 9 else f"realscale:lotm_{path}_s8_potion"
            self.assertEqual(expected, normal["result"]["id"])
            alternate = load(DATA / "recipe" / f"lotm_{path}_s{seq}_potion_characteristic.json")
            self.assertEqual("realscale:lotm_alchemy", alternate["type"])
            alternate_items = {x["item"] for x in alternate["ingredients"]}
            self.assertIn("realscale:lotm_empty_vial", alternate_items)
            self.assertIn("realscale:lotm_occult_catalyst", alternate_items)
            self.assertIn(f"realscale:lotm_{path}_s{seq}_characteristic", alternate_items)
            self.assertEqual(expected, alternate["result"]["id"])

    def test_geckolib_animation_and_player_abilities_contracts_are_wired(self):
        metadata = (ROOT / "neoforge/src/main/resources/META-INF/neoforge.mods.toml").read_text(encoding="utf-8")
        self.assertIn('modId="geckolib"', metadata)
        self.assertIn('versionRange="[4.9.3,)"', metadata)
        self.assertRegex(metadata, r'modId="curios"\s+type="required"')
        self.assertRegex(metadata, r'modId="player_abilities"\s+type="required"')
        geo = load(ASSETS / "geo/lotm_alchemy_apparatus.geo.json")
        animations = load(ASSETS / "animations/lotm_alchemy_apparatus.animation.json")
        self.assertIn("minecraft:geometry", geo)
        self.assertIn("animation.realscale.lotm_alchemy_apparatus.idle", animations["animations"])
        self.assertIn("animation.realscale.lotm_alchemy_apparatus.brew", animations["animations"])
        recipe_code = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/lotm/LotmAlchemyBlockEntity.java").read_text(encoding="utf-8")
        self.assertIn("GeoBlockEntity", recipe_code)
        self.assertIn("ALCHEMY_RECIPE_TYPE", recipe_code)
        potion = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/lotm/LotmPotionItem.java").read_text(encoding="utf-8")
        self.assertIn("UseAnim.DRINK", potion)
        self.assertIn("DRINK_TICKS = 32", potion)
        registry = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/lotm/LotmPlayerAbilityRegistry.java").read_text(encoding="utf-8")
        sync = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/lotm/LotmPlayerAbilitySync.java").read_text(encoding="utf-8")
        ability = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/lotm/LotmPathwayAbility.java").read_text(encoding="utf-8")
        self.assertEqual(8, registry.count('helper.register(id("lotm_'))
        self.assertIn("AbilityRegistry.ABILITY_REGISTRY_KEY", registry)
        self.assertIn("extends ActiveAbility", ability)
        self.assertIn("AbilityUseType.INSTANT", ability)
        self.assertIn("getMaxLevel()", ability)
        self.assertIn("AbilityAPI.grant", sync)
        self.assertIn("AbilityAPI.revoke", sync)
        self.assertIn("getGrantsBySource", sync)
        self.assertIn("lotm_pathway", sync)
        self.assertIn("sanitizeUnauthorizedGrants", sync)
        mod = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/RealScaleMod.java").read_text(encoding="utf-8")
        self.assertIn("LotmPlayerAbilityRegistry::register", mod)
        self.assertNotIn("LotmSkillPayload::register", mod)
        self.assertFalse((ROOT / "neoforge/src/main/java/org/realscale/neoforge/lotm/LotmSkillPayload.java").exists())
        self.assertFalse((ROOT / "neoforge/src/main/java/org/realscale/neoforge/lotm/client/LotmKeyMappings.java").exists())
        self.assertFalse((ROOT / "neoforge/src/main/java/org/realscale/neoforge/lotm/client/LotmClientGameEvents.java").exists())

    def test_runtime_dependency_packaging_and_curios_required_slot(self):
        metadata = (ROOT / "neoforge/src/main/resources/META-INF/neoforge.mods.toml").read_text(encoding="utf-8")
        self.assertRegex(metadata, r'modId="geckolib"\s+type="required"')
        self.assertRegex(metadata, r'modId="curios"\s+type="required"')
        self.assertRegex(metadata, r'modId="player_abilities"\s+type="required"')
        build = (ROOT / "neoforge/build.gradle").read_text(encoding="utf-8")
        self.assertIn("maven.modrinth:vvuO3ImH:yohfFbgD", build)
        self.assertIn("curse.maven:player-abilities-1600309:8825458", build)
        self.assertIn("copyLotmSmokeMods", build)
        runtime = (ROOT / "qa/run_dh_chunky_runtime_smoke.py").read_text(encoding="utf-8")
        packaged = (ROOT / "qa/run_packaged_jar_dh_chunky_smoke.py").read_text(encoding="utf-8")
        self.assertIn('"Grwn5rUB"', runtime)
        self.assertIn('"8BmcQJ2H"', runtime)
        self.assertIn('"vvuO3ImH"', runtime)
        self.assertIn('"yohfFbgD"', runtime)
        self.assertIn("install_player_abilities_from_gradle", runtime)
        self.assertIn("source_mods.glob", packaged)
        slot = load(ROOT / "neoforge/src/main/resources/data/realscale/curios/slots/occult_focus.json")
        entity = load(ROOT / "neoforge/src/main/resources/data/realscale/curios/entities/player.json")
        tag = load(ROOT / "neoforge/src/main/resources/data/curios/tags/item/occult_focus.json")
        self.assertEqual(1, slot["size"])
        self.assertIn("occult_focus", entity["slots"])
        self.assertIn("realscale:lotm_spirit_focus", tag["values"])
        self.assertIn("realscale:lotm_divination_pendulum", tag["values"])
        self.assertFalse((ROOT / "neoforge/src/main/resources/data/curios/tags/items/occult_focus.json").exists())

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


    def test_active_ore_blocks_require_pickaxes(self):
        tag = load(ROOT / "neoforge/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json")
        self.assertFalse(tag.get("replace", True))
        self.assertTrue({
            "realscale:lotm_star_crystal_ore",
            "realscale:lotm_cursed_crystal_ore",
            "realscale:lotm_grave_crystal_ore",
        }.issubset(set(tag["values"])), "Original three S9/S8 ores must stay mineable")

        source = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/lotm/LotmContent.java").read_text(encoding="utf-8")
        for block_id in ("lotm_star_crystal_ore", "lotm_cursed_crystal_ore", "lotm_grave_crystal_ore"):
            registration = source.split(f'BLOCKS.register("{block_id}"', 1)[1].split("\n    public static final", 1)[0]
            with self.subTest(block=block_id):
                self.assertIn("requiresCorrectToolForDrops()", registration)

    def test_active_ore_loot_has_silk_touch_fortune_and_explosion_rules(self):
        ores = {
            "star_crystal_ore": "star_crystal",
            "cursed_crystal_ore": "cursed_crystal",
            "grave_crystal_ore": "grave_crystal",
        }
        for ore, crystal in ores.items():
            loot = load(DATA / "loot_table/blocks" / f"lotm_{ore}.json")
            self.assertEqual("minecraft:block", loot["type"])
            alternatives = loot["pools"][0]["entries"][0]
            self.assertEqual("minecraft:alternatives", alternatives["type"])
            silk_touch, normal = alternatives["children"]
            self.assertEqual("realscale:lotm_" + ore, silk_touch["name"])
            silk_condition = silk_touch["conditions"][0]
            self.assertEqual("minecraft:match_tool", silk_condition["condition"])
            enchantments = silk_condition["predicate"]["predicates"]["minecraft:enchantments"]
            self.assertTrue(any(
                entry["enchantments"] == "minecraft:silk_touch" and entry["levels"]["min"] >= 1
                for entry in enchantments
            ))

            self.assertEqual("realscale:lotm_" + crystal, normal["name"])
            functions = normal["functions"]
            fortune = next(fn for fn in functions if fn["function"] == "minecraft:apply_bonus")
            self.assertEqual("minecraft:fortune", fortune["enchantment"])
            self.assertEqual("minecraft:ore_drops", fortune["formula"])
            self.assertIn("minecraft:explosion_decay", {fn["function"] for fn in functions})

    def test_active_ore_worldgen_profiles_remain_biome_scoped_and_unchanged(self):
        expected = {
            "star_crystal_ore": (5, 3, -48, 24),
            "cursed_crystal_ore": (5, 2, -48, 48),
            "grave_crystal_ore": (5, 2, -48, 40),
        }
        for ore, (size, count, min_y, max_y) in expected.items():
            configured = load(DATA / "worldgen/configured_feature" / f"lotm_{ore}.json")
            placed = load(DATA / "worldgen/placed_feature" / f"lotm_{ore}.json")
            modifier = load(DATA / "neoforge/biome_modifier" / f"lotm_{ore}.json")
            self.assertEqual("minecraft:ore", configured["type"])
            self.assertEqual(size, configured["config"]["size"])
            targets = {target["target"]["tag"] for target in configured["config"]["targets"]}
            self.assertEqual({"minecraft:stone_ore_replaceables", "minecraft:deepslate_ore_replaceables"}, targets)
            count_modifier = next(entry for entry in placed["placement"] if entry["type"] == "minecraft:count")
            self.assertEqual(count, count_modifier["count"])
            height = next(entry for entry in placed["placement"] if entry["type"] == "minecraft:height_range")["height"]
            self.assertEqual(min_y, height["min_inclusive"]["absolute"])
            self.assertEqual(max_y, height["max_inclusive"]["absolute"])
            self.assertEqual("neoforge:add_features", modifier["type"])
            self.assertEqual("realscale:lotm_" + ore, modifier["features"])
            self.assertEqual("underground_ores", modifier["step"])

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

    def test_focus_and_acting_code_respect_the_active_sequence_boundary(self):
        focus = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/lotm/LotmFocusItem.java").read_text(encoding="utf-8")
        skills = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/lotm/LotmSkills.java").read_text(encoding="utf-8")
        events = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/lotm/LotmEvents.java").read_text(encoding="utf-8")
        acting = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/lotm/LotmActing.java").read_text(encoding="utf-8")
        self.assertNotIn("SPIRIT_QUARTZ_ORE", focus)
        self.assertIn("sequence < 8 || sequence > 9", skills)
        self.assertIn("sequence < 8 || sequence > 9", events)
        self.assertIn("LotmActing.copy(original, clone)", (ROOT / "neoforge/src/main/java/org/realscale/neoforge/lotm/LotmState.java").read_text(encoding="utf-8"))
        self.assertIn("Do not permanently mark a biome while the daily cap is full", acting)

    def test_original_art_generator_covers_active_and_archived_resources(self):
        spec = importlib.util.spec_from_file_location(
            "lotm_visuals", ROOT / "scripts/generate_lotm_visuals.py")
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        for name in ("lotm_relic_cache", "lotm_face_rose",
                     "lotm_seer_s7_characteristic", "lotm_sleepless_s8_potion",
                     "lotm_seer_primary", "lotm_mystery_pryer_secondary",
                     "lotm_sleepless_primary", "lotm_corpse_collector_secondary"):
            blob = module.png_bytes(name)
            self.assertTrue(blob.startswith(bytes([137, 80, 78, 71, 13, 10, 26, 10])))
            self.assertGreater(len(blob), 50)
        self.assertEqual(8, len(module.ABILITIES))

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
