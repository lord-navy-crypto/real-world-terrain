"""Sanity remains independent from characteristic instability and is server-authoritative."""
from pathlib import Path
import json
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "neoforge/src/main/java/org/realscale/neoforge/lotm"
CLIENT = JAVA / "client"
LANG = ROOT / "neoforge/src/main/resources/assets/realscale/lang"


def read(path):
    return path.read_text(encoding="utf-8")


class LotmSanitySystemTests(unittest.TestCase):
    def test_sanity_is_a_separate_persisted_stat_and_survives_respawn(self):
        state = read(JAVA / "LotmState.java")
        self.assertIn('SANITY_KEY = "realscale_lotm_sanity"', state)
        self.assertIn("tag.putInt(SANITY_KEY, LotmBalance.MAX_SANITY)", state)
        self.assertIn("public static int sanity(Player player)", state)
        self.assertIn("LotmBalance.changeSanity(sanity(player), amount)", state)
        self.assertIn("target.putInt(SANITY_KEY, sanity(original))", state)
        self.assertIn("INSTABILITY_KEY", state)
        self.assertIn("SANITY_KEY", state)

    def test_sanity_affects_skills_and_divination_on_server_and_client(self):
        skills = read(JAVA / "LotmSkills.java")
        ability = read(JAVA / "LotmPathwayAbility.java")
        menu = read(JAVA / "LotmStatusMenu.java")
        screen = read(CLIENT / "LotmStatusScreen.java")
        self.assertIn("LotmState.sanity(player)", skills)
        self.assertIn("LotmState.sanity(player)", ability)
        self.assertIn("sanity()", menu)
        self.assertIn("menu.sanity()", screen)
        self.assertIn("divinationRadiusForSanity", skills)
        self.assertIn("sanitySkillSurcharge", read(JAVA / "LotmBalance.java"))

    def test_rest_and_stability_sigil_restore_sanity_while_deep_dark_can_reduce_it(self):
        events = read(JAVA / "LotmEvents.java")
        sigil = read(JAVA / "LotmStabilitySigilItem.java")
        balance = read(JAVA / "LotmBalance.java")
        self.assertIn("player.isSleeping()", events)
        self.assertIn("LotmState.adjustSanity(player, 2)", events)
        self.assertIn("Biomes.DEEP_DARK", events)
        self.assertIn("deepDarkSanityLoss", events)
        self.assertIn("LotmState.adjustSanity(player, 12)", sigil)
        self.assertIn("deepDarkSanityLoss(int sequence, int instability)", balance)

    def test_panel_and_action_bar_show_sanity_from_server_state(self):
        menu = read(JAVA / "LotmStatusMenu.java")
        screen = read(CLIENT / "LotmStatusScreen.java")
        hud = read(JAVA / "LotmHud.java")
        self.assertIn("DATA_COUNT = 36", menu)
        self.assertIn("case 35 -> LotmState.sanity(player)", menu)
        self.assertIn("public int sanity()", menu)
        self.assertIn('"gui.realscale.lotm_panel.sanity"', screen)
        self.assertIn("LotmState.sanity(player)", hud)

    def test_bilingual_sanity_and_backlash_messages_are_complete(self):
        keys = (
            "gui.realscale.lotm_panel.sanity",
            "message.realscale.lotm.sanity.deep_dark",
            "tooltip.realscale.lotm.stability_sigil.sanity",
            "message.realscale.lotm.stability_sigil.restored",
        )
        for locale in ("en_us", "zh_cn"):
            messages = json.loads(read(LANG / (locale + ".json")))
            for key in keys:
                with self.subTest(locale=locale, key=key):
                    self.assertTrue(messages.get(key), f"missing {key}")
        self.assertEqual(8, json.loads(read(LANG / "en_us.json"))["message.realscale.lotm.hud.line"].count("%s"))
        self.assertEqual(8, json.loads(read(LANG / "zh_cn.json"))["message.realscale.lotm.hud.line"].count("%s"))


if __name__ == "__main__":
    unittest.main()
