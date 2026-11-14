"""Contract tests for the expanded LOTM occult management console."""
from pathlib import Path
import json
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "neoforge/src/main/java/org/realscale/neoforge"
LOTM = JAVA / "lotm"
ASSETS = ROOT / "neoforge/src/main/resources/assets/realscale/lang"


class LotmConsoleRefinementTests(unittest.TestCase):
    def test_dashboard_syncs_real_progression_inventory_and_preference_state(self):
        menu = (LOTM / "LotmStatusMenu.java").read_text(encoding="utf-8")
        content = (LOTM / "LotmContent.java").read_text(encoding="utf-8")
        screen = (LOTM / "client/LotmStatusScreen.java").read_text(encoding="utf-8")
        self.assertIn("DATA_COUNT = 32", menu)
        self.assertIn("LotmContent.countMaterials(player)", menu)
        self.assertIn("public static int countMaterials(Player player)", content)
        self.assertIn("inventory.getContainerSize()", content)
        self.assertIn("preparedPotionRequirement()", menu)
        self.assertIn("characteristicRequirement()", menu)
        self.assertIn("dashboard.next_prepared", screen)
        self.assertIn("dashboard.next_characteristic", screen)
        self.assertIn("dashboard.materials", screen)
        self.assertIn("menu.skillCost(slot)", screen)

    def test_skill_tree_uses_authoritative_cost_formula_and_explains_unlock_states(self):
        menu = (LOTM / "LotmStatusMenu.java").read_text(encoding="utf-8")
        screen = (LOTM / "client/LotmStatusScreen.java").read_text(encoding="utf-8")
        ability = (LOTM / "LotmPathwayAbility.java").read_text(encoding="utf-8")
        self.assertIn("LotmBalance.skillCost(path.id(), slot, sequence()", menu)
        self.assertIn("LotmBalance.skillCostWithMastery(base, skillMastery(slot), skillCostPercent())", menu)
        self.assertIn("LotmBalance.skillCostWithMastery(baseCost", ability)
        self.assertIn("renderSkillTooltip(graphics", screen)
        self.assertIn("skill.tooltip.state.disabled", screen)
        self.assertIn("skill.tooltip.cooldown", screen)
        self.assertIn("skill.tooltip.mastery", screen)
        self.assertIn("skill_tree.node.complete", screen)

    def test_structured_journal_tracks_discovery_incomplete_and_verified_states(self):
        acting = (LOTM / "LotmActing.java").read_text(encoding="utf-8")
        network = (LOTM / "LotmPanelNetwork.java").read_text(encoding="utf-8")
        self.assertIn("EVENT_JOURNAL_PREFIX", acting)
        self.assertIn("MAX_EVENT_JOURNAL_ENTRIES = 128", acting)
        self.assertIn('case "discovered" -> 1', acting)
        self.assertIn('case "incomplete" -> 2', acting)
        self.assertIn('case "verified" -> 3', acting)
        self.assertIn("recordJournalEvent(Player player, String category, String status, String sourceId)", acting)
        self.assertIn("journalEntries(Player player)", acting)
        self.assertIn("statusRank(status) <= statusRank(old[3])", acting)
        self.assertIn("LotmActing.journalEntries(player)", network)
        self.assertIn("event|" , acting)
        self.assertIn("LotmPanelNetwork.syncResearchLog(serverPlayer)", acting)

    def test_journal_records_actual_potion_alchemy_material_and_skill_events(self):
        potion = (LOTM / "LotmPotionItem.java").read_text(encoding="utf-8")
        alchemy = (LOTM / "LotmAlchemyMenu.java").read_text(encoding="utf-8")
        skills = (LOTM / "LotmSkills.java").read_text(encoding="utf-8")
        manual = (LOTM / "LotmSkillBookItem.java").read_text(encoding="utf-8")
        acting = (LOTM / "LotmActing.java").read_text(encoding="utf-8")
        self.assertIn('"potion", "incomplete"', potion)
        self.assertIn('"potion", "verified"', potion)
        self.assertIn('"alchemy", "verified"', alchemy)
        self.assertIn('"potion", "discovered"', alchemy)
        self.assertIn('"skill", "discovered"', alchemy)
        self.assertIn('recordSkillUse(player, 2 + (9 - sequence), slot)', skills)
        self.assertIn('"skill", "verified"', manual)
        self.assertIn('"material", "verified"', acting)

    def test_player_feedback_preferences_are_persistent_and_respawn_safe(self):
        preferences = (LOTM / "LotmPreferences.java").read_text(encoding="utf-8")
        state = (LOTM / "LotmState.java").read_text(encoding="utf-8")
        menu = (LOTM / "LotmStatusMenu.java").read_text(encoding="utf-8")
        potion = (LOTM / "LotmPotionItem.java").read_text(encoding="utf-8")
        skills = (LOTM / "LotmSkills.java").read_text(encoding="utf-8")
        self.assertIn("journalNotificationsEnabled(Player player)", preferences)
        self.assertIn("toggleSound(Player player)", preferences)
        self.assertIn("toggleSkillFeedback(Player player)", preferences)
        self.assertIn("LotmPreferences.copy(original, clone)", state)
        self.assertIn("BUTTON_TOGGLE_JOURNAL_NOTIFICATIONS", menu)
        self.assertIn("LotmPreferences.soundEnabled(player)", potion)
        self.assertIn("LotmPreferences.skillFeedbackEnabled(player)", skills)

    def test_client_panel_preferences_are_registered_and_applied(self):
        config = (LOTM / "client/LotmClientConfig.java").read_text(encoding="utf-8")
        mod = (JAVA / "RealScaleMod.java").read_text(encoding="utf-8")
        screen = (LOTM / "client/LotmStatusScreen.java").read_text(encoding="utf-8")
        self.assertIn("PANEL_SCALE_PERCENT", config)
        self.assertIn("PANEL_OPACITY_PERCENT", config)
        self.assertIn("COMPACT_LAYOUT", config)
        self.assertIn("SHOW_SKILL_DETAILS", config)
        self.assertIn("SHOW_JOURNAL_TIMESTAMPS", config)
        self.assertIn("JOURNAL_NEWEST_FIRST", config)
        self.assertIn("ModConfig.Type.CLIENT", mod)
        self.assertIn("panelMouseX(mouseX)", screen)
        self.assertIn("panelColor(0xFF10111A)", screen)
        self.assertIn("journalPageSize()", screen)

    def test_journal_has_search_category_filters_sorting_and_timestamps(self):
        screen = (LOTM / "client/LotmStatusScreen.java").read_text(encoding="utf-8")
        self.assertIn("new EditBox", screen)
        self.assertIn("journalSearch.getValue()", screen)
        self.assertIn("(journalFilter + 1) % 11", screen)
        self.assertIn("JOURNAL_NEWEST_FIRST", screen)
        self.assertIn("journalTimestamp", screen)
        self.assertIn("journalTimeLabel", screen)
        self.assertIn("journal.status.short.", screen)
        self.assertIn("journal.category.", screen)

    def test_both_locales_cover_new_console_states_and_controls(self):
        required = (
            "dashboard.next_prepared", "dashboard.next_characteristic", "dashboard.materials",
            "skill.tooltip.state.disabled", "skill.tooltip.state.low_spirit",
            "skill.tooltip.cost", "skill.tooltip.cooldown", "skill.tooltip.mastery",
            "journal.search", "journal.search_hint", "journal.sort.newest", "journal.sort.oldest",
            "journal.timestamp", "journal.status.discovered", "journal.status.verified",
            "journal.status.incomplete", "journal.category.potion", "journal.category.material",
            "journal.category.alchemy", "journal.category.skill", "journal.category.pathway",
            "settings.page.personal", "settings.page.interface", "settings.scale",
            "settings.opacity", "settings.layout", "settings.timestamps", "settings.sort",
            "settings.skill_details", "setting.sound_toggle", "setting.skill_feedback_toggle",
            "setting.journal_notifications_toggle",
        )
        for locale in ("en_us", "zh_cn"):
            messages = json.loads((ASSETS / (locale + ".json")).read_text(encoding="utf-8"))
            for suffix in required:
                self.assertTrue(messages["gui.realscale.lotm_panel." + suffix]
                                if suffix.startswith(("dashboard.", "skill.", "journal.", "settings.", "setting."))
                                else messages.get(suffix))


if __name__ == "__main__":
    unittest.main()
