"""UI and server-authoritative progression preflight regression contract."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
LOT = ROOT / "neoforge/src/main/java/org/realscale/neoforge/lotm"

class LotmPotionPreflightTests(unittest.TestCase):
    def test_preflight_and_final_consumption_share_validation(self):
        state = (LOT / "LotmState.java").read_text(encoding="utf-8")
        potion = (LOT / "LotmPotionItem.java").read_text(encoding="utf-8")
        self.assertIn("public static String validateAdvance(", state)
        self.assertIn("LotmProgression.validate(", state)
        self.assertIn("String validation = validateAdvance(player, path, sequence, direct);", state)
        self.assertIn("LotmState.validateAdvance(player, pathway, sequence, characteristic)", potion)
        self.assertLess(potion.index("LotmState.validateAdvance("),
                        potion.index("player.startUsingItem(hand)"))
        self.assertIn('if (!"ready".equals(result))', potion)
        self.assertLess(potion.index('if (!"ready".equals(result))'),
                        potion.index('stack.shrink(1)'))
