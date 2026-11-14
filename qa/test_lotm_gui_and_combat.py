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
        self.assertIn("PacketDistributor.sendToServer(", client)
        self.assertNotIn("sendCommand(", client)
        network = (BASE / "lotm/LotmPanelNetwork.java").read_text()
        self.assertIn(".playToServer(", network)
        self.assertIn("LotmStatusMenu.dataFor(player)", network)
        self.assertIn("public record OpenPanelPayload()", network)
        self.assertIn("RegisterKeyMappingsEvent", client_events)
        for index in range(6):
            self.assertIn(f"selectedTab = {index}", screen)
        self.assertIn("gui.realscale.lotm_panel.alchemy.page", screen)
        self.assertIn("getAllRecipesFor(", screen)
        self.assertIn("recipe.getIngredients()", screen)
        self.assertIn("graphics.renderItem(", screen)
        self.assertIn("previousRecipe.visible = selectedTab == 3", screen)
        self.assertIn("journal_header", screen)
        self.assertIn("selectedTab = 6", screen)
        self.assertIn("LotmCreatureKind.values()[creatureIndex]", screen)
        self.assertIn("LotmDropBalance.effectiveChance(", screen)
        self.assertIn("openBestiary.visible = selectedTab == 2", screen)
        menu = (BASE / "lotm/LotmStatusMenu.java").read_text()
        self.assertIn("public static final int DATA_COUNT = 33", menu)
        self.assertIn('LotmActing.dailyUsed(player, "fieldwork")', menu)
        self.assertIn("BUTTON_TOGGLE_PARTICLES", menu)

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

    def test_ten_creature_eggs_are_in_the_creative_tab_and_have_models(self):
        import importlib.util
        import tempfile
        egg_registry = (BASE / "lotm/LotmSpawnEggs.java").read_text()
        creative = (BASE / "lotm/LotmCreativeTab.java").read_text()
        mod = (BASE / "RealScaleMod.java").read_text()
        self.assertIn("DeferredSpawnEggItem(", egg_registry)
        self.assertIn("LotmSpawnEggs.register(bus)", mod)
        self.assertIn("LotmSpawnEggs.egg(kind).get()", creative)
        generator_path = ROOT / "scripts/generate_lotm_visuals.py"
        spec = importlib.util.spec_from_file_location("lotm_gen_test", generator_path)
        generator = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(generator)
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            generator.generate(output)
            for kind in generator.CREATURES:
                model_path = (output / "assets/realscale/models/item"
                              / f"lotm_{kind}_spawn_egg.json")
                self.assertTrue(model_path.is_file(), kind)
                model = json.loads(model_path.read_text(encoding="utf-8"))
                self.assertEqual("minecraft:item/template_spawn_egg", model["parent"])
                for locale in ("en_us", "zh_cn"):
                    lang = json.loads((LANG / (locale + ".json")).read_text())
                    self.assertTrue(lang.get(f"item.realscale.lotm_{kind}_spawn_egg"), (locale, kind))

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
                "gui.realscale.lotm_panel.bestiary.header",
                "gui.realscale.lotm_panel.bestiary.drop",
                "gui.realscale.lotm_panel.bestiary.previous",
                "gui.realscale.lotm_panel.alchemy.previous",
                "gui.realscale.lotm_panel.alchemy.next",
                "gui.realscale.lotm_panel.alchemy.no_recipes",
            ):
                self.assertTrue(lang.get(key), (locale, key))
