"""Regression checks for the embedded guard's normal chunk-event hot path."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MIXIN = ROOT / "neoforge/src/main/java/org/realscale/neoforge/compat/dhchunky/mixin/ChunkyLifecycleSetupMixin.java"


class GuardHotpathTests(unittest.TestCase):
    def test_bound_chunk_hotpath_returns_before_properties_reflection_or_metrics(self):
        source = MIXIN.read_text(encoding="utf-8")
        start = source.index("private void realscale$deferChunkyBindingUntilReady")
        probe = source.index("ChunkyBridgeMetrics.probe(", start)
        hotpath = source[start:probe]
        self.assertLess(hotpath.index("if (listenerBound) return;"),
                        hotpath.index("if (!realscale$guardEnabled) return;"))
        self.assertNotIn("System.getProperty(", hotpath)
        self.assertNotIn("getMethod(", hotpath)
        self.assertIn("private static final boolean realscale$guardEnabled", source)
        self.assertEqual(source.count('"realscale.dhchunky.guard.enabled", "true"'), 1)

    def test_provider_retry_and_binding_proof_remain_narrow(self):
        source = MIXIN.read_text(encoding="utf-8")
        self.assertIn('method = "tryRunFirstTimeSetup()V"', source)
        self.assertIn("ci.cancel();", source)
        self.assertIn("if (listenerBound) return;", source)
        self.assertIn("ChunkyBridgeMetrics.deferred();", source)
        self.assertIn("ChunkyBridgeMetrics.ready();", source)
        self.assertIn("ChunkyBridgeMetrics.bound();", source)
        self.assertIn("root instanceof IllegalStateException", source)
        self.assertIn('"Chunky is not loaded."', source)
        self.assertIn("if (!realscale$boundConfirmed && listenerBound)", source)
        self.assertNotIn("Thread.sleep(", source)

    def test_guard_is_optional_and_does_not_create_a_standalone_mod(self):
        root = ROOT / "neoforge/src/main/resources"
        config = (root / "realscale.dhchunky.mixins.json").read_text(encoding="utf-8")
        meta = (root / "META-INF/neoforge.mods.toml").read_text(encoding="utf-8")
        self.assertIn('"required": false', config)
        self.assertIn('type="optional"', meta)
        self.assertIn('modId="dhchunkyguard"', meta)
        legacy = [b for b in meta.split("[[dependencies.realscale]]")
                  if 'modId="dhchunkyguard"' in b]
        self.assertEqual(len(legacy), 1)
        self.assertIn('type="incompatible"', legacy[0])
        self.assertNotIn('[[mods]]\\nmodId="dhchunkyguard"', meta)
        self.assertFalse((ROOT / "dh-chunky-guard/build.gradle").exists())


if __name__ == "__main__":
    unittest.main()
