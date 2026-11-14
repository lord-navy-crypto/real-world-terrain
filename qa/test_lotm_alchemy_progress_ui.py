"""LOTM recipe browser and machine status clarity without new dependency frameworks."""
from pathlib import Path
import json
import unittest

ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / "neoforge/src/main/java/org/realscale/neoforge/lotm/client"
LANG = ROOT / "neoforge/src/main/resources/assets/realscale/lang"

class LotmAlchemyProgressUiTests(unittest.TestCase):
    def test_machine_displays_remaining_time_and_full_status(self):
        screen = (CLIENT / "LotmAlchemyScreen.java").read_text(encoding="utf-8")
        self.assertIn("remainingSeconds = (max - progress + 19) / 20", screen)
        self.assertIn("100L * progress / max", screen)
        self.assertIn('gui.realscale.lotm_alchemy.progress', screen)
        self.assertIn("font.plainSubstrByWidth(status.getString(), 160)", screen)
        self.assertIn("case 2, 3 ->", screen)

    def test_recipe_browser_uses_real_item_tooltips(self):
        screen = (CLIENT / "LotmStatusScreen.java").read_text(encoding="utf-8")
        self.assertIn("renderRecipeItemTooltip(graphics, mouseX, mouseY)", screen)
        self.assertIn("recipe.getIngredients()", screen)
        self.assertIn("choices[choice].getHoverName()", screen)
        self.assertIn("ingredient_options", screen)
        self.assertIn("Math.min(choices.length, 8)", screen)
        self.assertIn("graphics.renderTooltip(font, result, mouseX, mouseY)", screen)
        self.assertIn("LotmContent.SPIRIT_EMBER.get()", screen)

    def test_localization_for_progress(self):
        for locale in ("en_us", "zh_cn"):
            messages = json.loads((LANG / (locale + ".json")).read_text(encoding="utf-8"))
            self.assertEqual(2, messages["gui.realscale.lotm_alchemy.progress"].count("%s"))
