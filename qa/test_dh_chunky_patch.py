#!/usr/bin/env python3
"""Regression tests for the optional, embedded DH/Chunky lifecycle guard."""
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
COMPAT = ROOT / "neoforge/src/main/java/org/realscale/neoforge/compat/dhchunky"
SOURCE = COMPAT / "mixin/ChunkyLifecycleSetupMixin.java"
PLUGIN = COMPAT / "mixin/DhChunkyMixinConfigPlugin.java"
CONFIG = ROOT / "neoforge/src/main/resources/realscale.dhchunky.mixins.json"
TOML = ROOT / "neoforge/src/main/resources/META-INF/neoforge.mods.toml"


class DhChunkyGuardTests(unittest.TestCase):
    def test_patch_only_defers_the_confirmed_first_time_binding(self):
        code = SOURCE.read_text(encoding="utf-8")
        self.assertIn('tryRunFirstTimeSetup()V', code)
        self.assertIn("if (listenerBound) return;", code)
        self.assertIn("ci.cancel();", code)
        self.assertIn('"Chunky is not loaded."', code)
        self.assertNotIn("@Overwrite", code)
        self.assertNotIn("catch (Throwable", code)

    def test_mixin_is_optional_and_version_gated(self):
        config = json.loads(CONFIG.read_text(encoding="utf-8"))
        self.assertFalse(config["required"])
        self.assertEqual(config["mixins"], ["ChunkyLifecycleSetupMixin"])
        self.assertIn("DhChunkyMixinConfigPlugin", config["plugin"])
        plugin = PLUGIN.read_text(encoding="utf-8")
        self.assertIn('hasVersion("distanthorizons", "3.3.3")', plugin)
        self.assertIn('hasVersion("chunky", "1.4.23")', plugin)
        self.assertIn("return false;", plugin)

    def test_dh_and_chunky_are_optional_not_hard_dependencies(self):
        toml = TOML.read_text(encoding="utf-8")
        for mod_id in ("distanthorizons", "chunky"):
            blocks = [b for b in toml.split("[[dependencies.realscale]]")
                      if f'modId="{mod_id}"' in b]
            self.assertEqual(len(blocks), 1)
            self.assertIn('type="optional"', blocks[0])
        self.assertIn('config="realscale.dhchunky.mixins.json"', toml)

    def test_compatibility_code_is_inside_the_real_scale_project(self):
        self.assertTrue(SOURCE.is_file())
        self.assertFalse((ROOT / "dh-chunky-guard/build.gradle").exists())
        self.assertFalse((ROOT / "dh-chunky-guard/settings.gradle").exists())
        main = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/RealScaleMod.java").read_text()
        self.assertIn("DhChunkyGuardCommands::registerCommands", main)

    def test_game_code_never_reduces_realscale_height_or_rewrites_worldgen(self):
        code = SOURCE.read_text(encoding="utf-8")
        self.assertNotIn("2096", code)
        self.assertNotIn("Biome", code)
        self.assertNotIn("Noise", code)


if __name__ == "__main__":
    unittest.main()
