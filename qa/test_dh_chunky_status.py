"""v0.3 noninvasive in-game bridge diagnostics tests."""
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/"dh-chunky-guard/src/main/java/org/realscale/compat/dhchunky"
class BridgeStatusTests(unittest.TestCase):
    def test_read_only_commands(self):
        src=(JAVA/"DhChunkyGuardMod.java").read_text()
        self.assertIn('Commands.literal("dhchunkyguard")',src)
        self.assertIn('Commands.literal("status")',src)
        self.assertIn('Commands.literal("advice")',src)
        self.assertIn('NeoForge.EVENT_BUS.addListener',src)
        self.assertNotIn('Commands.literal("force")',src)
    def test_metrics_probes_only_before_listener_bound(self):
        mixin=(JAVA/"mixin/ChunkyLifecycleSetupMixin.java").read_text()
        self.assertLess(mixin.index('if (listenerBound) return;'),
                        mixin.index('ChunkyBridgeMetrics.probe('))
        for x in ['ChunkyBridgeMetrics.deferred()','ChunkyBridgeMetrics.ready()',
                  'ChunkyBridgeMetrics.bound()']:
            self.assertIn(x,mixin)
    def test_status_cannot_claim_gpu_rendering(self):
        src=(JAVA/"ChunkyBridgeMetrics.java").read_text()
        self.assertIn('GPU_LOD=unverified',src)
        self.assertIn('nativeBindsSinceLaunch=',src)
        self.assertNotIn('Minecraft.getInstance(',src)
    def test_client_status_commands_do_not_mutate_scheduler(self):
        source=(JAVA/"DhChunkyGuardMod.java").read_text()
        self.assertIn('Commands.literal("status")',source)
        self.assertIn('Commands.literal("advice")',source)
        self.assertNotIn('Commands.literal("start")',source)
        self.assertNotIn('Commands.literal("stop")',source)
    def test_exact_known_versions(self):
        toml=(ROOT/"dh-chunky-guard/src/main/resources/META-INF/neoforge.mods.toml").read_text()
        self.assertIn('version="0.3.1-experimental"',toml)
        self.assertIn('versionRange="[3.3.3,3.3.4)"',toml)
        self.assertIn('versionRange="[1.4.23,1.4.24)"',toml)
if __name__=="__main__":
    unittest.main()
