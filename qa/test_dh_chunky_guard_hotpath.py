"""Regression checks for the DH–Chunky guard's hot path (not an FPS benchmark)."""
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
MIXIN=ROOT/"dh-chunky-guard/src/main/java/org/realscale/compat/dhchunky/mixin/ChunkyLifecycleSetupMixin.java"

class GuardHotpathTests(unittest.TestCase):
    def test_no_system_properties_on_bound_chunk_hotpath(self):
        s=MIXIN.read_text(encoding="utf-8")
        start=s.index("private void realscale$deferChunkyBindingUntilReady")
        probe=s.index("ChunkyBridgeMetrics.probe(",start)
        first=s[start:probe]
        self.assertLess(first.index("if (listenerBound) return;"),
                        first.index("if (!realscale$guardEnabled) return;"))
        self.assertNotIn("System.getProperty(",first)
        self.assertIn("private static final boolean realscale$guardEnabled",s)
        self.assertEqual(s.count('System.getProperty("realscale.dhchunky.guard.enabled"'),0)
        self.assertEqual(s.count('"realscale.dhchunky.guard.enabled", "true"'),1)

    def test_no_change_to_provider_retry_or_binding_proof(self):
        s=MIXIN.read_text(encoding="utf-8")
        self.assertIn('method = "tryRunFirstTimeSetup()V"',s)
        self.assertIn("ci.cancel();",s)
        self.assertIn("if (listenerBound) return;",s)
        self.assertIn("ChunkyBridgeMetrics.deferred();",s)
        self.assertIn("ChunkyBridgeMetrics.ready();",s)
        self.assertIn("ChunkyBridgeMetrics.bound();",s)
        self.assertIn('root instanceof IllegalStateException',s)
        self.assertIn('"Chunky is not loaded."',s)
        self.assertIn("if (!realscale$boundConfirmed && listenerBound)",s)
        self.assertNotIn("Thread.sleep(",s)

    def test_version_and_addon_independence(self):
        root=ROOT/"dh-chunky-guard"
        meta=(root/"src/main/resources/META-INF/neoforge.mods.toml").read_text()
        self.assertIn('version="0.3.1-experimental"',meta)
        self.assertIn('versionRange="[3.3.3,3.3.4)"',meta)
        self.assertIn('versionRange="[1.4.23,1.4.24)"',meta)
        self.assertIn('modId="dhchunkyguard"',meta)
        self.assertNotIn('modId="realscale"',meta)

if __name__=="__main__": unittest.main()
