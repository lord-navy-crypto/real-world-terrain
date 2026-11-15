"""The Fallen Mother boon is independent from normal pathways and fully discoverable in-game."""
from pathlib import Path
import json
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "neoforge/src/main/java/org/realscale/neoforge/lotm"
CLIENT = JAVA / "client"
RES = ROOT / "neoforge/src/main/resources"
ASSETS = RES / "assets/realscale"
DATA = RES / "data/realscale"


def read(path):
    return path.read_text(encoding="utf-8")


def load(path):
    return json.loads(read(path))


class LotmBoonSystemTests(unittest.TestCase):
    def test_boon_uses_independent_persistent_keys_and_does_not_overwrite_normal_path(self):
        boon = read(JAVA / "LotmBoonState.java")
        state = read(JAVA / "LotmState.java")
        for key in ("realscale_lotm_boon_source", "realscale_lotm_boon_path",
                    "realscale_lotm_boon_sequence", "realscale_lotm_boon_taint",
                    "realscale_lotm_boon_revoked", "realscale_lotm_boon_discoveries"):
            self.assertIn(key, boon)
        self.assertNotIn("LotmPath.NBT_PATH,", boon)
        self.assertNotIn("LotmPath.NBT_SEQUENCE,", boon)
        self.assertIn("LotmBoonState.copy(original, clone)", state)
        self.assertIn("LotmState.adjustSanity(player, amount)", state)
        self.assertIn("outerDeityBoons", read(JAVA / "LotmConfig.java"))

    def test_altar_grant_upgrade_stabilize_and_rebind_are_server_validated(self):
        altar = read(JAVA / "LotmBoonAltarBlock.java")
        boon = read(JAVA / "LotmBoonState.java")
        self.assertIn("player instanceof ServerPlayer", altar)
        self.assertIn("LotmBoonState.useAtAltar(serverPlayer)", altar)
        for result in ("granted", "advanced", "stabilized", "rebound"):
            self.assertIn('return "' + result + '"', boon)
        self.assertIn("LotmBoonBalance.canAdvanceToSequence8", boon)
        self.assertIn("LotmContent.STAR_CRYSTAL.get()", boon)
        self.assertIn("LotmContent.ECHO_QUARTZ.get()", boon)
        self.assertIn("LotmContent.BLOOM_ITEM.get()", boon)
        self.assertIn("LotmContent.LUNAR_MICA.get()", boon)
        self.assertIn("LotmContent.SOUL_BELLFLOWER_ITEM.get()", boon)
        self.assertIn("LotmContent.FACE_ROSE_ITEM.get()", boon)
        self.assertIn("player.getAbilities().instabuild", boon)

    def test_spirit_focus_insight_is_exploration_only_and_server_authoritative(self):
        focus = read(JAVA / "LotmFocusItem.java")
        boon = read(JAVA / "LotmBoonState.java")
        self.assertIn("InteractionHand hand", focus)
        self.assertIn("player instanceof ServerPlayer", focus)
        self.assertIn("LotmBoonState.activateInsight(serverPlayer)", focus)
        self.assertIn("INSIGHT_COOLDOWN_TICKS", boon)
        self.assertIn("findTrace(player, sequence)", boon)
        self.assertIn("DISCOVERIES_KEY", boon)
        self.assertIn("LotmContent.RELIC_CACHE.get()", boon)
        self.assertIn("LotmContent.MIDNIGHT_BLOOM.get()", boon)
        self.assertIn("LotmState.adjustSanity(player, -LotmBoonBalance.insightSanityCost(sequence))", boon)
        self.assertIn("addTaint(player, LotmBoonBalance.insightTaintCost(sequence))", boon)

    def test_altar_is_registered_craftable_visible_and_has_block_assets(self):
        content = read(JAVA / "LotmContent.java")
        tab = read(JAVA / "LotmCreativeTab.java")
        self.assertIn('register("lotm_boon_altar"', content)
        self.assertIn("BOON_ALTAR_ITEM", content)
        self.assertIn("LotmContent.BOON_ALTAR_ITEM.get()", tab)
        recipe = load(DATA / "recipe/lotm_boon_altar.json")
        self.assertEqual("realscale:lotm_boon_altar", recipe["result"]["id"])
        self.assertEqual(3, len(recipe["pattern"]))
        self.assertTrue((ASSETS / "blockstates/lotm_boon_altar.json").is_file())
        self.assertTrue((ASSETS / "models/block/lotm_boon_altar.json").is_file())
        self.assertTrue((ASSETS / "models/item/lotm_boon_altar.json").is_file())
        loot = load(DATA / "loot_table/blocks/lotm_boon_altar.json")
        self.assertEqual("realscale:lotm_boon_altar", loot["pools"][0]["entries"][0]["name"])

    def test_status_menu_and_panel_show_boon_state_even_without_a_normal_path(self):
        menu = read(JAVA / "LotmStatusMenu.java")
        screen = read(CLIENT / "LotmStatusScreen.java")
        self.assertIn("DATA_COUNT = 42", menu)
        for index in range(36, 42):
            self.assertIn(f"case {index} ->", menu)
        self.assertIn("public int boonSourceId()", menu)
        self.assertIn("public int boonSequence()", menu)
        self.assertIn("public int boonTaint()", menu)
        self.assertIn("selectedTab = 6", screen)
        self.assertIn("gui.realscale.lotm_panel.boon.header", screen)
        self.assertIn("selectedTab == 7", screen)
        self.assertIn("selectedTab != 6 && selectedTab != 7", screen)

    def test_bilingual_altar_focus_and_panel_strings_are_complete(self):
        keys = (
            "block.realscale.lotm_boon_altar",
            "item.realscale.lotm_boon_altar",
            "gui.realscale.lotm_panel.tab.boon",
            "gui.realscale.lotm_panel.boon.header",
            "gui.realscale.lotm_panel.boon.none",
            "gui.realscale.lotm_panel.boon.status.active",
            "gui.realscale.lotm_panel.boon.status.revoked",
            "tooltip.realscale.lotm.spirit_focus.boon_insight",
            "message.realscale.lotm.boon.altar.granted",
            "message.realscale.lotm.boon.altar.advanced",
            "message.realscale.lotm.boon.insight.discovered",
            "message.realscale.lotm.boon.insight.strained",
        )
        for locale in ("en_us", "zh_cn"):
            data = load(ASSETS / "lang" / f"{locale}.json")
            for key in keys:
                with self.subTest(locale=locale, key=key):
                    self.assertTrue(data.get(key), f"missing {key}")


if __name__ == "__main__":
    unittest.main()
