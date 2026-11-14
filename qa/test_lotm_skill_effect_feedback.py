"""LOTM active abilities and potion success/failure have user-facing feedback."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
LOT = ROOT / "neoforge/src/main/java/org/realscale/neoforge/lotm"

class LotmEffectFeedbackTests(unittest.TestCase):
    def test_potion_feedback_happens_only_after_authoritative_advance(self):
        src = (LOT / "LotmPotionItem.java").read_text(encoding="utf-8")
        self.assertIn("LotmState.validateAdvance(player, pathway, sequence, characteristic)", src)
        self.assertIn('if (!"ready".equals(result))', src)
        self.assertIn("server.sendParticles(characteristic ? ParticleTypes.SOUL : ParticleTypes.ENCHANT", src)
        self.assertIn("SoundEvents.PLAYER_LEVELUP", src)
        self.assertGreater(src.index("server.sendParticles("), src.index('if (!"ready".equals(result))'))

    def test_seer_omen_gives_bounded_visual_direction_cue(self):
        src = (LOT / "LotmSkills.java").read_text(encoding="utf-8")
        self.assertIn("for (int step = 1; step <= 6; step++)", src)
        self.assertIn("Math.min(distance, step * 0.65D) / distance", src)
        self.assertIn("ParticleTypes.END_ROD", src)
        self.assertIn("if (distance > 0.001D)", src)

    def test_low_spirit_skill_cost_is_obvious_on_panel(self):
        src = (LOT / "client/LotmStatusScreen.java").read_text(encoding="utf-8")
        self.assertIn("menu.spirit() < cost ? 0xFFEAA0A0", src)
