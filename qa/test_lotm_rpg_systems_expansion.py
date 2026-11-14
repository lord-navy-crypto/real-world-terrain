"""LOTM RPG botany, discovery and anti-farming source/resource acceptance checks.

These are static contracts, not a replacement for an actual client save-world playtest.
"""
from __future__ import annotations

import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "neoforge/src/main/resources/data/realscale/recipe"
ASSETS = ROOT / "neoforge/src/main/resources/assets/realscale/lang"
JAVA = ROOT / "neoforge/src/main/java/org/realscale/neoforge"
PLANT_RECIPES = {
    "lotm_spirit_ember_midnight_bloom": ("lotm_midnight_bloom", "lotm_spirit_ember", 3),
    "lotm_occult_catalyst_face_rose": ("lotm_face_rose", "lotm_occult_catalyst", 3),
    "lotm_occult_catalyst_soul_bellflower": ("lotm_soul_bellflower", "lotm_occult_catalyst", 3),
    "lotm_spirit_ember_blood_rose": ("lotm_blood_rose", "lotm_spirit_ember", 4),
}
GUIDE_SECTIONS = ("creatures", "flora", "minerals", "alchemy", "progression")


class LotmRpgExpansionTests(unittest.TestCase):
    def test_four_existing_supernatural_flowers_supply_distinct_optional_material_routes(self):
        for name, (flower, output, quantity) in PLANT_RECIPES.items():
            with self.subTest(recipe=name):
                recipe = json.loads((DATA / (name + ".json")).read_text(encoding="utf-8"))
                self.assertEqual("minecraft:crafting_shapeless", recipe["type"])
                self.assertEqual("misc", recipe["category"])
                self.assertIn("realscale:" + flower,
                              {entry["item"] for entry in recipe["ingredients"]})
                self.assertGreaterEqual(len(recipe["ingredients"]), 3)
                self.assertEqual("realscale:" + output, recipe["result"]["id"])
                self.assertEqual(quantity, recipe["result"]["count"])
                self.assertNotIn("s7", json.dumps(recipe).lower())
        # The normal material routes are additive, never deleted/replaced.
        for name in ("lotm_occult_catalyst", "lotm_spirit_ember",
                     "lotm_spirit_focus_lunar", "lotm_spirit_ember_abyssal",
                     "lotm_occult_catalyst_echo"):
            self.assertTrue((DATA / (name + ".json")).exists(), name)

    def test_fieldwork_uses_persistent_world_provenance_for_all_six_active_ores(self):
        acting = (JAVA / "lotm/LotmActing.java").read_text(encoding="utf-8")
        provenance = (JAVA / "lotm/LotmResourceProvenance.java").read_text(encoding="utf-8")
        mod = (JAVA / "RealScaleMod.java").read_text(encoding="utf-8")
        self.assertIn("LotmActing::onBlockPlace", mod)
        self.assertIn("LotmActing::onBlockBreak", mod)
        self.assertIn("SavedData", provenance)
        self.assertIn("computeIfAbsent", provenance)
        self.assertIn("setDirty()", provenance)
        self.assertIn("LotmResourceProvenance.get(level).markPlaced", acting)
        self.assertIn("LotmResourceProvenance.get(level).consumePlacement", acting)
        for ore in ("STAR_ORE", "CURSED_ORE", "GRAVE_ORE",
                    "LUNAR_MICA_ORE", "ECHO_QUARTZ_ORE", "ABYSSAL_SALT_ORE"):
            self.assertIn("LotmContent." + ore + ".get()", acting)

    def test_research_has_novel_clues_per_pathway_and_sequence_and_uses_server_game_clock(self):
        acting = (JAVA / "lotm/LotmActing.java").read_text(encoding="utf-8")
        skills = (JAVA / "lotm/LotmSkills.java").read_text(encoding="utf-8")
        commands = (JAVA / "lotm/LotmCommands.java").read_text(encoding="utf-8")
        self.assertIn("RESEARCH_PREFIX", acting)
        self.assertIn("StringTag.valueOf(sourceId)", acting)
        self.assertIn("if (seen.contains(id)) return 0;", acting)
        self.assertIn("RESEARCH_PREFIX + suffix", acting)
        self.assertIn("overworld().getGameTime()", acting)
        self.assertIn("recordResearchDiscovery(player, entityClue(target), 2)", skills)
        self.assertIn("recordResearchDiscovery(player, blockClue(level, pos), 2)", skills)
        self.assertNotIn("recordResearch(player, Math.min(4, detected))", skills)
        self.assertIn('Commands.literal("journal")', commands)
        for section in GUIDE_SECTIONS:
            self.assertIn('Commands.literal("' + section + '")', commands)

    def test_curios_focus_items_have_exclusive_real_gameplay_effects_and_bilingual_tooltips(self):
        curios = (JAVA / "lotm/LotmCurios.java").read_text(encoding="utf-8")
        balance = (JAVA / "lotm/LotmBalance.java").read_text(encoding="utf-8")
        events = (JAVA / "lotm/LotmEvents.java").read_text(encoding="utf-8")
        skills = (JAVA / "lotm/LotmSkills.java").read_text(encoding="utf-8")
        focus = (JAVA / "lotm/LotmFocusItem.java").read_text(encoding="utf-8")
        pendulum = (JAVA / "lotm/LotmDivinationItem.java").read_text(encoding="utf-8")
        slot = json.loads((ROOT / "neoforge/src/main/resources/data/realscale/curios/slots/occult_focus.json").read_text(encoding="utf-8"))
        item_tag = json.loads((ROOT / "neoforge/src/main/resources/data/curios/tags/item/occult_focus.json").read_text(encoding="utf-8"))
        self.assertEqual(1, slot["size"])
        self.assertEqual({"realscale:lotm_spirit_focus", "realscale:lotm_divination_pendulum"}, set(item_tag["values"]))
        self.assertIn("CuriosApi.getCuriosInventory", curios)
        self.assertIn("getEquippedCurios()", curios)
        self.assertIn("LotmCurios.hasSpiritFocus(player)", events)
        self.assertIn("LotmCurios.hasDivinationPendulum(player)", skills)
        commands = (JAVA / "lotm/LotmCommands.java").read_text(encoding="utf-8")
        self.assertIn("message.realscale.lotm.status.accessory", commands)
        self.assertIn("LotmBalance.instabilitySurcharge(LotmState.instability(player))", commands)
        self.assertIn("spiritRecovery(int configuredAmount, boolean hasSpiritFocus)", balance)
        self.assertIn("divinationRadius(int sequence, boolean hasPendulum)", balance)
        self.assertIn("appendHoverText", focus)
        self.assertIn("appendHoverText", pendulum)
        for locale in ("en_us", "zh_cn"):
            lang = json.loads((ASSETS / (locale + ".json")).read_text(encoding="utf-8"))
            for key in ("spirit_focus.effect", "divination_pendulum.effect", "curio_exclusive_slot"):
                self.assertTrue(lang["tooltip.realscale.lotm." + key])


    def test_direct_characteristic_instability_is_reversible_and_balanced(self):
        state = (JAVA / "lotm/LotmState.java").read_text(encoding="utf-8")
        balance = (JAVA / "lotm/LotmBalance.java").read_text(encoding="utf-8")
        skills = (JAVA / "lotm/LotmSkills.java").read_text(encoding="utf-8")
        sigil = (JAVA / "lotm/LotmStabilitySigilItem.java").read_text(encoding="utf-8")
        content = (JAVA / "lotm/LotmContent.java").read_text(encoding="utf-8")
        recipe = json.loads((DATA / "lotm_stability_sigil.json").read_text(encoding="utf-8"))
        model = json.loads((ROOT / "neoforge/src/main/resources/assets/realscale/models/item/lotm_stability_sigil.json").read_text(encoding="utf-8"))
        generator = (ROOT / "scripts/generate_lotm_visuals.py").read_text(encoding="utf-8")
        self.assertIn("if (direct) player.getPersistentData().putInt(INSTABILITY_KEY, 1)", state)
        self.assertIn("reduceInstability(Player player, int amount)", state)
        self.assertIn("instabilitySurcharge(int instability)", balance)
        self.assertIn("LotmState.instability(player)", skills)
        self.assertIn("LotmState.reduceInstability(player, 1)", sigil)
        self.assertIn("STABILITY_SIGIL", content)
        self.assertEqual("minecraft:crafting_shapeless", recipe["type"])
        self.assertEqual("realscale:lotm_stability_sigil", recipe["result"]["id"])
        self.assertEqual(1, recipe["result"]["count"])
        self.assertEqual("realscale:item/lotm_stability_sigil", model["textures"]["layer0"])
        self.assertIn('"lotm_stability_sigil"', generator)
        for locale in ("en_us", "zh_cn"):
            lang = json.loads((ASSETS / (locale + ".json")).read_text(encoding="utf-8"))
            self.assertTrue(lang["item.realscale.lotm_stability_sigil"])
            self.assertTrue(lang["tooltip.realscale.lotm.stability_sigil.effect"])
            self.assertTrue(lang["message.realscale.lotm.stability_sigil.restored"])


    def test_potion_tooltips_explain_pathway_and_both_advancement_requirements(self):
        potion = (JAVA / "lotm/LotmPotionItem.java").read_text(encoding="utf-8")
        self.assertIn("appendHoverText", potion)
        self.assertIn("POTION_PRACTICE.get()", potion)
        self.assertIn("CHARACTERISTIC_PRACTICE.get()", potion)
        for locale in ("en_us", "zh_cn"):
            lang = json.loads((ASSETS / (locale + ".json")).read_text(encoding="utf-8"))
            for key in ("path", "rank", "characteristic", "direct_risk",
                        "characteristic_requirement", "first_potion", "prepared",
                        "prepared_requirement"):
                self.assertTrue(lang["tooltip.realscale.lotm.potion." + key])


    def test_bilingual_discovery_and_field_guide_content_is_complete(self):
        for locale in ("en_us", "zh_cn"):
            lang = json.loads((ASSETS / (locale + ".json")).read_text(encoding="utf-8"))
            for key in ("journal.header", "journal.entry", "journal.empty", "journal.more"):
                self.assertTrue(lang["message.realscale.lotm." + key])
            for section in GUIDE_SECTIONS:
                self.assertTrue(lang["message.realscale.lotm.guide." + section])
            self.assertIn("/lotm journal", lang["message.realscale.lotm.guide"])


if __name__ == "__main__":
    unittest.main()
