"""Night Church trader is a rare, server-side utility vendor only."""
from pathlib import Path
import json
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "neoforge/src/main/java/org/realscale/neoforge"
LANG = ROOT / "neoforge/src/main/resources/assets/realscale/lang"


def read(path):
    return path.read_text(encoding="utf-8")


class LotmMerchantTradeTests(unittest.TestCase):
    def test_trader_is_server_side_rare_and_configurable(self):
        merchant = read(JAVA / "lotm/LotmMerchantTrades.java")
        config = read(JAVA / "lotm/LotmConfig.java")
        mod = read(JAVA / "RealScaleMod.java")
        self.assertIn("event.getLevel().isClientSide()", merchant)
        self.assertIn("LotmConfig.TRADERS_ENABLED.get()", merchant)
        self.assertIn("VARIANT_DENOMINATOR = 4", merchant)
        self.assertIn("trader.getRandom().nextInt(VARIANT_DENOMINATOR) != 0", merchant)
        self.assertIn("TRADERS_ENABLED", config)
        self.assertIn('define("nightChurchTrader", true)', config)
        self.assertIn("NeoForge.EVENT_BUS.addListener(LotmMerchantTrades::onJoin)", mod)

    def test_only_noncombat_utility_tools_are_offered(self):
        merchant = read(JAVA / "lotm/LotmMerchantTrades.java")
        for required in (
            "LotmContent.DIVINATION_PENDULUM.get()",
            "LotmContent.SPIRIT_FOCUS.get()",
            "LotmContent.STABILITY_SIGIL.get()",
        ):
            self.assertIn(required, merchant)
        for forbidden in (
            "LotmPotionItem", "SEER_POTION", "PRYER_POTION",
            "SLEEPLESS_POTION", "CORPSE_POTION", "EMPTY_VIAL",
            "SPIRIT_EMBER", "OCCULT_CATALYST",
        ):
            self.assertNotIn(forbidden, merchant)
        self.assertEqual(3, merchant.count("addEmeraldTrade(trader,"))

    def test_trader_name_is_bilingual(self):
        for locale in ("en_us", "zh_cn"):
            data = json.loads(read(LANG / (locale + ".json")))
            self.assertTrue(data.get("entity.realscale.lotm.night_church_trader"))


if __name__ == "__main__":
    unittest.main()
