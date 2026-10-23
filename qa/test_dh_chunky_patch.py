#!/usr/bin/env python3
"""Guard against broad, unsafe 'catch every worldgen crash' hotfixes."""
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
PATCH = ROOT / "dh-chunky-guard"
SOURCE = PATCH / "src/main/java/org/realscale/compat/dhchunky/mixin/ChunkyLifecycleSetupMixin.java"

class DhChunkyGuardTests(unittest.TestCase):
    def test_patch_only_acts_before_dh_first_time_listener_binding(self):
        code = SOURCE.read_text()
        self.assertIn("tryRunFirstTimeSetup()V", code)
        self.assertIn("if (listenerBound) return",code)
        self.assertIn("ci.cancel()",code)
        self.assertIn('"Chunky is not loaded."',code)
        self.assertNotIn("@Overwrite",code)
        self.assertNotIn("catch (Throwable",code)

    def test_mixin_is_exact_and_not_global(self):
        config=json.loads((PATCH/"src/main/resources/dhchunkyguard.mixins.json").read_text())
        self.assertEqual(config["mixins"],["ChunkyLifecycleSetupMixin"])
        self.assertTrue(config["required"])
        self.assertEqual(config["injectors"]["defaultRequire"],1)

    def test_version_contracts_only_target_known_pair(self):
        toml=(PATCH/"src/main/resources/META-INF/neoforge.mods.toml").read_text()
        self.assertIn('versionRange="[3.3.3,3.3.4)"',toml)
        self.assertIn('versionRange="[1.4.23,1.4.24)"',toml)
        self.assertIn('side="BOTH"',toml)

    def test_game_code_never_reduces_realscale_height(self):
        self.assertNotIn("2096",SOURCE.read_text())
        self.assertNotIn("Biome",SOURCE.read_text())
        self.assertNotIn("Noise",SOURCE.read_text())

if __name__=="__main__":
    unittest.main()
