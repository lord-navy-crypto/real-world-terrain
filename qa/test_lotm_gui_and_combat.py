"""Focused contract for LOTM inventory UI, access keys, balanced combat and chance loot."""
from pathlib import Path
import json
import unittest

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "neoforge/src/main/java/org/realscale/neoforge"
LANG = ROOT / "neoforge/src/main/resources/assets/realscale/lang"


class LotmGuiAndCombatTests(unittest.TestCase):
    def test_gui_is_accessible_without_typing_chat_commands(self):
        client = (BASE / "lotm/client/LotmClientKeys.java").read_text()
        client_events = (BASE / "lotm/client/LotmClientEvents.java").read_text()
        screen = (BASE / "lotm/client/LotmStatusScreen.java").read_text()
        self.assertIn("GLFW.GLFW_KEY_O", client)
        self.assertIn("InventoryScreen", client)
        self.assertIn('sendCommand("lotm panel")', client)
        self.assertIn("RegisterKeyMappingsEvent", client_events)
        for index in range(6):
            self.assertIn(f"selectedTab = {index}", screen)
        self.assertIn("alchemy_line", screen)
        self.assertIn("journal_header", screen)
        menu = (BASE / "lotm/LotmStatusMenu.java").read_text()
        self.assertIn("public static final int DATA_COUNT = 26", menu)
        self.assertIn('LotmActing.dailyUsed(player, "fieldwork")', menu)

    def test_creative_inventory_lists_playable_resources_not_archived_rank(self):
        creative = (BASE / "lotm/LotmCreativeTab.java").read_text()
        mod = (BASE / "RealScaleMod.java").read_text()
        content = (BASE / "lotm/LotmContent.java").read_text()
        self.assertIn("CreativeModeTab.builder()", creative)
        self.assertIn("LotmCreativeTab.register(bus)", mod)
        self.assertIn("LotmContent.ALCHEMY_APPARATUS_ITEM.get()", creative)
        self.assertIn("LotmContent.potion(path, sequence).get()", creative)
        self.assertIn("sequence >= 8", creative)
        self.assertIn("public static Supplier<? extends Item> potion(", content)

    def test_combat_ai_uses_speed_multipliers_not_movement_attributes(self):
        creature = (BASE / "lotm/LotmCreature.java").read_text()
        self.assertIn("kind.isHostile() ? 1.22D : 1.05D", creature)
        self.assertIn("new WaterAvoidingRandomStrollGoal(this, 1.00D)", creature)
        self.assertNotIn("kind.movementSpeed() * (kind.isHostile()", creature)
        config = (BASE / "lotm/LotmConfig.java").read_text()
        drops = (BASE / "lotm/LotmEvents.java").read_text()
        self.assertIn('.defineInRange("materialDropChancePercent", 40, 0, 100)', config)
        self.assertIn("entity.getRandom().nextInt(100)", drops)

    def test_bilingual_gui_labels(self):
        for locale in ("en_us", "zh_cn"):
            lang = json.loads((LANG / (locale + ".json")).read_text())
            for key in (
                "itemGroup.realscale.lotm", "key.realscale.lotm.panel",
                "gui.realscale.lotm_panel.open",
                "gui.realscale.lotm_panel.tab.alchemy",
                "gui.realscale.lotm_panel.alchemy_line1",
                "gui.realscale.lotm_panel.alchemy_line5",
                "gui.realscale.lotm_panel.tab.journal",
                "gui.realscale.lotm_panel.journal_discoveries",
                "gui.realscale.lotm_panel.journal_fieldwork",
            ):
                self.assertTrue(lang.get(key), (locale, key))
