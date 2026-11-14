"""Verify the player-facing LOTM mastery, skill manual, panel and cosmetic systems."""
from __future__ import annotations

import importlib.util
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "neoforge/src/main/java/org/realscale/neoforge/lotm"
ASSETS = ROOT / "neoforge/src/main/resources/assets/realscale"
RECIPES = ROOT / "neoforge/src/main/resources/data/realscale/recipe"
PATHWAYS = {
    "seer": {9: ("lavas_squid_essence", "star_crystal"), 8: ("hornacis_goat_horn", "face_rose")},
    "mystery_pryer": {9: ("nightmare_eye_shard", "cursed_crystal"), 8: ("ironarm_spine", "four_eyed_turtle_heart")},
    "sleepless": {9: ("night_owl_token", "midnight_bloom"), 8: ("crimson_howler_voice", "soul_bellflower")},
    "corpse_collector": {9: ("black_spotted_frog_token", "shroudman_crystal"), 8: ("death_crow_eye", "blood_rose")},
}


def load_generator():
    spec = importlib.util.spec_from_file_location("lotm_visuals", ROOT / "scripts/generate_lotm_visuals.py")
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


class LotmSkillManualAndPanelTests(unittest.TestCase):
    def test_all_eight_manuals_have_models_locales_and_original_texture_generation(self):
        en = json.loads((ASSETS / "lang/en_us.json").read_text(encoding="utf-8"))
        zh = json.loads((ASSETS / "lang/zh_cn.json").read_text(encoding="utf-8"))
        generator = load_generator()
        seen = set()
        for path in PATHWAYS:
            for slot in (1, 2):
                item_id = f"lotm_{path}_skill_manual_{slot}"
                self.assertTrue((ASSETS / "models/item" / f"{item_id}.json").is_file())
                model = json.loads((ASSETS / "models/item" / f"{item_id}.json").read_text(encoding="utf-8"))
                self.assertEqual(f"realscale:item/{item_id}", model["textures"]["layer0"])
                self.assertTrue(en.get("item.realscale." + item_id))
                self.assertTrue(zh.get("item.realscale." + item_id))
                self.assertIn(item_id, generator.ITEMS)
                for sequence in (9, 8):
                    self.assertTrue(en.get(f"lotm.realscale.skill_manuals.{path}.{sequence}.{slot}"))
                    self.assertTrue(zh.get(f"lotm.realscale.skill_manuals.{path}.{sequence}.{slot}"))
                png = generator.png_bytes(item_id)
                self.assertTrue(png.startswith(bytes((137, 80, 78, 71, 13, 10, 26, 10))))
                self.assertNotIn(png, seen)
                seen.add(png)
        self.assertEqual(8, len(seen))

    def test_all_six_active_minerals_and_eight_ability_icons_have_distinct_original_art(self):
        generator = load_generator()
        minerals = ("star_crystal", "cursed_crystal", "grave_crystal",
                    "lunar_mica", "echo_quartz", "abyssal_salt")
        for mineral in minerals:
            self.assertIn("lotm_" + mineral, generator.MINERAL_TEXTURE_PALETTES)
            self.assertIn("lotm_" + mineral + "_ore", generator.MINERAL_TEXTURE_PALETTES)
        material_ids = (
            "lotm_lavas_squid_essence", "lotm_nightmare_eye_shard",
            "lotm_night_owl_token", "lotm_black_spotted_frog_token",
            "lotm_shroudman_crystal", "lotm_hornacis_goat_horn",
            "lotm_ironarm_spine", "lotm_four_eyed_turtle_heart",
            "lotm_crimson_howler_voice", "lotm_death_crow_eye",
            "lotm_spirit_ember", "lotm_occult_catalyst",
        )
        for name in material_ids:
            blob = generator.png_bytes(name)
            self.assertTrue(blob.startswith(bytes((137, 80, 78, 71, 13, 10, 26, 10))))
        ability_icons = [generator.png_bytes(name) for name in generator.ABILITIES]
        self.assertEqual(8, len(set(ability_icons)))

    def test_sixteen_manual_recipes_use_the_correct_rank_materials_and_distinct_binders(self):
        for path, ranks in PATHWAYS.items():
            for sequence, pair in ranks.items():
                for slot, material in ((1, pair[0]), (2, pair[1])):
                    manual_id = f"lotm_{path}_skill_manual_{slot}"
                    recipe_path = RECIPES / f"{manual_id}_s{sequence}.json"
                    self.assertTrue(recipe_path.is_file(), str(recipe_path))
                    recipe = json.loads(recipe_path.read_text(encoding="utf-8"))
                    self.assertEqual("realscale:lotm_alchemy", recipe["type"])
                    ingredients = {entry["item"] for entry in recipe["ingredients"]}
                    self.assertEqual(4, len(ingredients))
                    self.assertIn("minecraft:book", ingredients)
                    self.assertIn("realscale:lotm_occult_catalyst", ingredients)
                    self.assertIn("realscale:lotm_" + material, ingredients)
                    binder = "lotm_spirit_ember" if slot == 1 else "lotm_stability_sigil"
                    self.assertIn("realscale:" + binder, ingredients)
                    self.assertEqual({"id": "realscale:" + manual_id, "count": 1}, recipe["result"])

    def test_manual_learning_is_server_authoritative_and_never_grants_external_abilities(self):
        item = (JAVA / "LotmSkillBookItem.java").read_text(encoding="utf-8")
        state = (JAVA / "LotmState.java").read_text(encoding="utf-8")
        sync = (JAVA / "LotmPlayerAbilitySync.java").read_text(encoding="utf-8")
        skills = (JAVA / "LotmSkills.java").read_text(encoding="utf-8")
        self.assertIn("LotmState.learnSkillManual(player, pathway, slot)", item)
        self.assertIn("if (!player.getAbilities().instabuild) stack.shrink(1)", item)
        self.assertIn("sequence == 9 ? 1 : LotmConfig.SKILL_MASTERY_CAP.get()", state)
        self.assertIn("skillMasteryKey(path, slot)", state)
        self.assertIn("LotmState.skillMastery(player, expectedPath, slot)", skills)
        self.assertIn("LotmBalance.skillCostWithMastery", skills)
        self.assertIn("AbilityAPI.revoke", sync)
        self.assertIn("PATHWAY_SOURCE", sync)

    def test_mastery_is_copied_on_respawn_and_applied_to_both_skill_checks_and_effects(self):
        state = (JAVA / "LotmState.java").read_text(encoding="utf-8")
        ability = (JAVA / "LotmPathwayAbility.java").read_text(encoding="utf-8")
        skills = (JAVA / "LotmSkills.java").read_text(encoding="utf-8")
        balance = (JAVA / "LotmBalance.java").read_text(encoding="utf-8")
        self.assertIn("target.putInt(skillMasteryKey(path, slot)", state)
        self.assertIn("LotmState.skillMastery(player, pathway, slot)", ability)
        self.assertIn("LotmBalance.skillCostWithMastery", ability)
        self.assertIn("mastery * 2", skills)
        self.assertIn("mastery * 20", skills)
        self.assertIn("public static int clampSkillMastery", balance)

    def test_alchemy_apparatus_accepts_manual_outputs_without_relaxing_fuel_checks(self):
        machine = (JAVA / "LotmAlchemyBlockEntity.java").read_text(encoding="utf-8")
        self.assertIn("stack.getItem() instanceof LotmSkillBookItem", machine)
        self.assertIn("LotmConfig.SKILLS_ENABLED.get() && LotmConfig.SKILL_BOOKS_ENABLED.get()", machine)
        self.assertIn("LotmConfig.ALCHEMY_SKILL_MANUAL_TICKS.get()", machine)
        self.assertIn("LotmContent.SPIRIT_EMBER.get()", machine)

    def test_status_panel_is_registered_openable_and_server_authoritative(self):
        content = (JAVA / "LotmAlchemyContent.java").read_text(encoding="utf-8")
        client = (JAVA / "client/LotmClientEvents.java").read_text(encoding="utf-8")
        commands = (JAVA / "LotmCommands.java").read_text(encoding="utf-8")
        menu = (JAVA / "LotmStatusMenu.java").read_text(encoding="utf-8")
        screen = (JAVA / "client/LotmStatusScreen.java").read_text(encoding="utf-8")
        self.assertIn('MENUS.register("lotm_status"', content)
        self.assertIn("LotmStatusScreen::new", client)
        self.assertIn('Commands.literal("panel")', commands)
        self.assertIn("player.openMenu(new SimpleMenuProvider", commands)
        self.assertIn("addDataSlots(data)", menu)
        self.assertIn("LotmHud.toggle(serverPlayer)", menu)
        self.assertIn("LotmAppearance.toggle(serverPlayer)", menu)
        self.assertIn("handleInventoryButtonClick", screen)
        self.assertIn("skillMasteryCap()", menu)
        self.assertIn("hasDivinationPendulum()", menu)
        self.assertIn("menu.hasDivinationPendulum()", screen)
        self.assertIn("lotm.realscale.skill_manuals.", screen)
        self.assertIn("gui.realscale.lotm_panel.skill.", screen)

    def test_appearance_is_opt_in_path_specific_and_configurable(self):
        appearance = (JAVA / "LotmAppearance.java").read_text(encoding="utf-8")
        events = (JAVA / "LotmEvents.java").read_text(encoding="utf-8")
        config = (JAVA / "LotmConfig.java").read_text(encoding="utf-8")
        self.assertIn("LotmAppearance.tick(player)", events)
        self.assertIn("LotmConfig.APPEARANCE_EFFECTS.get()", appearance)
        self.assertIn("ParticleTypes.ENCHANT", appearance)
        self.assertIn("ParticleTypes.END_ROD", appearance)
        self.assertIn("ParticleTypes.SOUL", appearance)
        self.assertIn("ParticleTypes.SOUL_FIRE_FLAME", appearance)
        self.assertIn('define("appearanceEffects", true)', config)

    def test_new_gameplay_settings_are_bounded_and_reported(self):
        config = (JAVA / "LotmConfig.java").read_text(encoding="utf-8")
        commands = (JAVA / "LotmCommands.java").read_text(encoding="utf-8")
        for setting in ("SKILLS_ENABLED", "SKILL_BOOKS_ENABLED", "APPEARANCE_EFFECTS",
                        "SKILL_MASTERY_CAP", "SKILL_COST_PERCENT", "ALCHEMY_SKILL_MANUAL_TICKS",
                        "HUD_REFRESH_TICKS", "APPEARANCE_INTERVAL_TICKS", "MATERIAL_DROP_CHANCE_PERCENT"):
            self.assertIn(setting, config)
        for key in ("message.realscale.lotm.config.skills",
                    "message.realscale.lotm.config.presentation",
                    "message.realscale.lotm.config.materials"):
            self.assertIn(key, commands)

    def test_skill_tab_has_pathway_specific_names_and_effects_in_both_locales(self):
        en = json.loads((ASSETS / "lang/en_us.json").read_text(encoding="utf-8"))
        zh = json.loads((ASSETS / "lang/zh_cn.json").read_text(encoding="utf-8"))
        for path in PATHWAYS:
            for slot in (1, 2):
                base = f"gui.realscale.lotm_panel.skill.{path}.{slot}"
                self.assertTrue(en.get(base + ".name"), "missing English skill name " + base)
                self.assertTrue(en.get(base + ".description"), "missing English skill description " + base)
                self.assertTrue(zh.get(base + ".name"), "missing Chinese skill name " + base)
                self.assertTrue(zh.get(base + ".description"), "missing Chinese skill description " + base)

    def test_in_game_diagnostic_is_read_only_and_checks_live_registries_and_recipe_manager(self):
        diagnostic = (JAVA / "LotmRpgDiagnostics.java").read_text(encoding="utf-8")
        commands = (JAVA / "LotmCommands.java").read_text(encoding="utf-8")
        self.assertIn('Commands.literal("diagnose")', commands)
        self.assertIn("LotmRpgDiagnostics.run(player)", commands)
        self.assertIn("BuiltInRegistries.BLOCK.getKey", diagnostic)
        self.assertIn("BuiltInRegistries.ITEM.getKey", diagnostic)
        self.assertIn("getAllRecipesFor(LotmAlchemyContent.ALCHEMY_RECIPE_TYPE.get())", diagnostic)
        self.assertIn("instanceof LotmSkillBookItem", diagnostic)
        self.assertIn("CREATURE_MATERIALS", diagnostic)
        self.assertIn("LotmContent.skillBook(path, slot)", diagnostic)
        self.assertNotIn("putInt(", diagnostic)
        self.assertNotIn("shrink(", diagnostic)

    def test_diagnostic_messages_are_localized(self):
        en = json.loads((ASSETS / "lang/en_us.json").read_text(encoding="utf-8"))
        zh = json.loads((ASSETS / "lang/zh_cn.json").read_text(encoding="utf-8"))
        for suffix in ("header", "ores", "minerals", "flora", "creature_materials",
                       "skill_manuals", "alchemy", "manual_recipes", "config", "player",
                       "section", "summary", "scope", "no_path", "player_detail"):
            key = "message.realscale.lotm.diagnose." + suffix
            self.assertTrue(en.get(key), "missing English diagnostic key " + key)
            self.assertTrue(zh.get(key), "missing Chinese diagnostic key " + key)

    def test_both_locales_cover_manual_tooltips_panel_and_config(self):
        en = json.loads((ASSETS / "lang/en_us.json").read_text(encoding="utf-8"))
        zh = json.loads((ASSETS / "lang/zh_cn.json").read_text(encoding="utf-8"))
        keys = (
            "tooltip.realscale.lotm.skill_manual.path",
            "tooltip.realscale.lotm.skill_manual.slot",
            "tooltip.realscale.lotm.skill_manual.effect",
            "tooltip.realscale.lotm.skill_manual.requirement",
            "message.realscale.lotm.skill_manual.learned",
            "message.realscale.lotm.skill_manual.disabled",
            "message.realscale.lotm.skill_manual.path",
            "message.realscale.lotm.skill_manual.sequence",
            "message.realscale.lotm.skill_manual.mastered",
            "message.realscale.lotm.appearance.enabled",
            "message.realscale.lotm.appearance.disabled",
            "message.realscale.lotm.config.skills",
            "message.realscale.lotm.config.presentation",
            "message.realscale.lotm.config.materials",
            "gui.realscale.lotm_panel.title",
            "gui.realscale.lotm_panel.tab.overview",
            "gui.realscale.lotm_panel.tab.skills",
            "gui.realscale.lotm_panel.tab.settings",
            "gui.realscale.lotm_panel.toggle_hud",
            "gui.realscale.lotm_panel.toggle_appearance",
            "gui.realscale.lotm_panel.setting.hud",
            "gui.realscale.lotm_panel.setting.aura",
            "gui.realscale.lotm_panel.setting.mastery_cap",
        )
        for key in keys:
            self.assertTrue(en.get(key), "missing English key " + key)
            self.assertTrue(zh.get(key), "missing Chinese key " + key)


if __name__ == "__main__":
    unittest.main()
