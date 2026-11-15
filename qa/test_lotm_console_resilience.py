"""Resilience and live-state contracts for the LOTM panel and research journal."""
from pathlib import Path
import json
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "neoforge/src/main/java/org/realscale/neoforge/lotm"
SCREEN = SOURCE / "client/LotmStatusScreen.java"
LOCALES = ROOT / "neoforge/src/main/resources/assets/realscale/lang"


def read(path):
    return path.read_text(encoding="utf-8")


class LotmConsoleResilienceTests(unittest.TestCase):
    def test_live_skill_cooldown_is_server_sourced_and_read_only(self):
        cooldown = read(SOURCE / "LotmSkillCooldowns.java")
        menu = read(SOURCE / "LotmStatusMenu.java")
        skills = read(SOURCE / "LotmSkills.java")
        state = read(SOURCE / "LotmState.java")
        screen = read(SCREEN)
        self.assertIn("skillCooldownTicks(slot, sequence)", cooldown)
        self.assertIn("recordSuccessfulUse(ServerPlayer player", cooldown)
        self.assertIn("boundedRemaining(end, serverTick(player)", cooldown)
        self.assertIn("left > Math.min(Short.MAX_VALUE, maximumTicks) ? 0", cooldown)
        self.assertIn("recordSuccessfulUse(player, expectedPath, slot, sequence)", skills)
        self.assertIn("DATA_COUNT = 36", menu)
        self.assertIn("case 33 -> LotmSkillCooldowns.remaining(player, path, 1)", menu)
        self.assertIn("case 34 -> LotmSkillCooldowns.remaining(player, path, 2)", menu)
        self.assertIn("case 35 -> LotmState.sanity(player)", menu)
        self.assertIn("remainingSkillCooldown(int slot)", menu)
        self.assertIn("LotmSkillCooldowns.copy(original, clone)", state)
        self.assertIn("menu.remainingSkillCooldown(slot)", screen)
        self.assertIn("skill.tooltip.cooldown_remaining", screen)

    def test_alchemy_records_normal_pickup_and_shift_click_from_server_only(self):
        menu = read(SOURCE / "LotmAlchemyMenu.java")
        self.assertIn("@Override public void onTake(Player player, ItemStack taken)", menu)
        self.assertIn("recordCraftedOutput(player, taken)", menu)
        self.assertIn("if (index == 5) recordCraftedOutput(player, copy)", menu)
        self.assertIn("private static void recordCraftedOutput(Player player, ItemStack taken)", menu)
        self.assertIn("player instanceof ServerPlayer serverPlayer", menu)
        self.assertIn("taken.isEmpty()", menu)
        self.assertIn('"alchemy", "verified"', menu)
        self.assertIn('"potion", "discovered"', menu)
        self.assertIn('"skill", "discovered"', menu)

    def test_cooldown_rollback_bounds_have_junit_coverage(self):
        java_test = read(ROOT / "neoforge/src/test/java/org/realscale/neoforge/lotm/LotmSkillCooldownsTest.java")
        self.assertIn("staleAndMalformedCooldownsFailClosed()", java_test)
        self.assertIn("acceptedSkillsShowRemainingTimeWithoutNegativeValues()", java_test)
        self.assertIn("sequenceSpecificMaximumIsRespected()", java_test)

    def test_settings_have_safe_fallback_and_explicit_reset(self):
        config = read(SOURCE / "client/LotmClientConfig.java")
        screen = read(SCREEN)
        self.assertIn("Math.max(minimum, Math.min(maximum, value.get()))", config)
        self.assertIn("catch (RuntimeException ignored)", config)
        self.assertIn("public static boolean save()", config)
        self.assertIn("public static boolean restoreDefaults()", config)
        self.assertIn("PANEL_OPACITY_PERCENT.set(92)", config)
        self.assertIn("settingsSaveFailed = !LotmClientConfig.restoreDefaults()", screen)
        self.assertIn("settings.save_failed", screen)
        self.assertIn("settings.reset_defaults", screen)
        self.assertIn("LotmClientConfig.scalePercent()", screen)
        self.assertIn("LotmClientConfig.opacityPercent()", screen)

    def test_panel_safely_scales_with_window_and_click_coordinates(self):
        screen = read(SCREEN)
        self.assertIn("private double effectivePanelScale()", screen)
        self.assertIn("width * 0.96D / imageWidth", screen)
        self.assertIn("height * 0.96D / imageHeight", screen)
        self.assertIn("panelMouseX(mouseX, activeMouseScale)", screen)
        self.assertIn("panelMouseY(mouseY, activeMouseScale)", screen)
        self.assertIn("graphics.pose().scale((float) scale", screen)

    def test_journal_has_status_filter_and_optimized_legacy_merge(self):
        screen = read(SCREEN)
        acting = read(SOURCE / "LotmActing.java")
        self.assertIn("journalStatusFilter = (journalStatusFilter + 1) % 4", screen)
        self.assertIn("journalStatusKey()", screen)
        self.assertIn("selectedStatus.equals(journalStatus(source))", screen)
        self.assertIn("Set<String> eventKeys = new HashSet<>()", acting)
        self.assertIn("eventKeys.contains(category +", acting)
        self.assertIn("MAX_EVENT_JOURNAL_ENTRIES = 128", acting)
        self.assertIn("statusRank(status) <= existingRank", acting)

    def test_exploration_and_inventory_materials_become_real_journal_events(self):
        acting = read(SOURCE / "LotmActing.java")
        content = read(SOURCE / "LotmContent.java")
        self.assertIn('recordJournalEvent(player, "biome", "discovered", "biome:" + biome)', acting)
        self.assertIn("if (player.tickCount % 200 == 0) recordCarriedMaterials(player)", acting)
        self.assertIn("LotmContent.registeredMaterials().values()", acting)
        self.assertIn('recordJournalEvent(player, "material", "discovered"', acting)
        self.assertIn("public static Map<String, Supplier<Item>> registeredMaterials()", content)
        self.assertIn("LotmResourceProvenance.get(level).consumePlacement", acting)

    def test_biomes_remain_discovered_after_daily_digestion_cap(self):
        acting = read(SOURCE / "LotmActing.java")
        self.assertIn("BIOME_DISCOVERY_PREFIX", acting)
        self.assertIn("String discoveryKey = BIOME_DISCOVERY_PREFIX + path.id()", acting)
        self.assertIn("tag.put(discoveryKey, discovered)", acting)
        self.assertIn('recordJournalEvent(player, "biome", "discovered", "biome:" + biome)', acting)
        self.assertIn("if (reward > 0)", acting)
        self.assertIn("target.put(biomeDiscoveryKey, source.get(biomeDiscoveryKey).copy())", acting)

    def test_journal_filter_cache_invalidates_on_server_snapshot_and_search_changes(self):
        screen = read(SCREEN)
        self.assertIn("all == cachedJournalSource", screen)
        self.assertIn("journalFilter == cachedJournalKind", screen)
        self.assertIn("journalStatusFilter == cachedJournalStatus", screen)
        self.assertIn("query.equals(cachedJournalQuery)", screen)
        self.assertIn("cachedJournalEntries = stream.sorted(order).toList()", screen)
        self.assertIn("cachedJournalSource = all", screen)

    def test_bilingual_resilience_and_feedback_labels(self):
        keys = [
            "gui.realscale.lotm_panel.skill.tooltip.state.cooling",
            "gui.realscale.lotm_panel.skill.tooltip.cooldown_remaining",
            "gui.realscale.lotm_panel.skill.tooltip.next_sequence",
            "gui.realscale.lotm_panel.skill_tree.cooldown_remaining",
            "gui.realscale.lotm_panel.dashboard.instability_surcharge",
            "gui.realscale.lotm_panel.journal.status_filter.all",
            "gui.realscale.lotm_panel.journal.status_filter.discovered",
            "gui.realscale.lotm_panel.journal.status_filter.incomplete",
            "gui.realscale.lotm_panel.journal.status_filter.verified",
            "gui.realscale.lotm_panel.settings.reset_defaults",
            "gui.realscale.lotm_panel.settings.save_failed",
        ]
        for locale in ("en_us", "zh_cn"):
            translations = json.loads(read(LOCALES / (locale + ".json")))
            for key in keys:
                self.assertIn(key, translations)
                self.assertTrue(translations[key])


if __name__ == "__main__":
    unittest.main()
