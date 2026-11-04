"""Read-only diagnostics for the embedded DH/Chunky guard."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "neoforge/src/main/java/org/realscale/neoforge/compat/dhchunky"
MIXIN = JAVA / "mixin/ChunkyLifecycleSetupMixin.java"


class BridgeStatusTests(unittest.TestCase):
    def test_read_only_commands_are_registered_by_realscale(self):
        src = (JAVA / "DhChunkyGuardCommands.java").read_text(encoding="utf-8")
        main = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/RealScaleMod.java").read_text(
            encoding="utf-8")
        self.assertIn('Commands.literal("dhchunkyguard")', src)
        self.assertIn('Commands.literal("status")', src)
        self.assertIn('Commands.literal("advice")', src)
        self.assertNotIn('Commands.literal("start")', src)
        self.assertNotIn('Commands.literal("stop")', src)
        self.assertIn("DhChunkyGuardCommands::registerCommands", main)
        self.assertNotIn('@Mod("dhchunkyguard")', src)

    def test_metrics_probe_only_before_listener_bound(self):
        mixin = MIXIN.read_text(encoding="utf-8")
        self.assertLess(mixin.index("if (listenerBound) return;"),
                        mixin.index("ChunkyBridgeMetrics.probe("))
        for marker in ("ChunkyBridgeMetrics.deferred()", "ChunkyBridgeMetrics.ready()",
                       "ChunkyBridgeMetrics.bound()"):
            self.assertIn(marker, mixin)

    def test_status_never_claims_gpu_rendering(self):
        src = (JAVA / "ChunkyBridgeMetrics.java").read_text(encoding="utf-8")
        self.assertIn("GPU_LOD=unverified", src)
        self.assertIn("nativeBindsSinceLaunch=", src)
        self.assertNotIn("Minecraft.getInstance(", src)
        self.assertIn("Iris, Sodium, or shaderpack compatibility", src)

    def test_metrics_are_launch_lifetime_and_noninvasive(self):
        src = (JAVA / "ChunkyBridgeMetrics.java").read_text(encoding="utf-8")
        self.assertNotIn("Thread.sleep(", src)
        self.assertNotIn("ScheduledExecutor", src)
        self.assertNotIn("ChunkyProvider", src)


if __name__ == "__main__":
    unittest.main()
