"""Regression gate: performance instrumentation must have zero default gameplay cost.

This does not claim a measured FPS improvement. It verifies consistent opt-in
defaults and ensures no worldgen or renderer algorithm is silently disabled.
"""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "neoforge/src/main/java/org/realscale/neoforge"

def src(relative):
    return (BASE / relative).read_text(encoding="utf-8")

class LowOverheadDefaults(unittest.TestCase):
    def test_frame_profiler_disabled_unless_requested(self):
        java = src("RealScaleClientFrameProfiler.java")
        self.assertIn('"realscale.client.fps.profile","false"',java)
        self.assertIn("if (!ENABLED) return;",java)
        self.assertIn("RenderFrameEvent.Post",java)

    def test_upload_budget_and_telemetry_share_disabled_default(self):
        telemetry = src("RealScaleChunkUploadClient.java")
        plugin = src("mixin/ClientSectionUploadMixinPlugin.java")
        self.assertIn('"realscale.experimental.client.uploadbudget","false"', telemetry)
        self.assertIn('"realscale.experimental.client.uploadbudget","false"', plugin)
        self.assertIn("if(!PROFILE)return;",telemetry)
        self.assertIn('Boolean.getBoolean("realscale.client.chunkqueue.profile")',telemetry)
        self.assertIn("ENABLED ||",telemetry)
        self.assertIn("if(mc.level==null || mc.player==null || mc.screen!=null)return;",telemetry)
        self.assertIn("BUDGET.beginFrame(playing)",telemetry)

    def test_aquifer_sampler_opt_in_with_vanilla_path_unchanged(self):
        plugin = src("mixin/AquiferReadOnlyAuditMixinPlugin.java")
        mixin = src("mixin/AquiferReadOnlyAuditMixin.java")
        self.assertIn('"realscale.experimental.aquifer.audit","false"',plugin)
        self.assertIn('shouldApplyMixin(String target,String mixin)',plugin)
        self.assertIn('@At("RETURN")',mixin)
        self.assertNotIn("cir.setReturnValue(",mixin)
        self.assertNotIn("cir.cancel(",mixin)

    def test_visibility_profiler_and_renderer_override_remain_opt_in(self):
        visibility = src("RealScaleAdaptiveVisibilityClient.java")
        self.assertIn('Boolean.getBoolean("realscale.client.visibility.profile")',visibility)
        plugin = src("mixin/ClientSectionUploadMixinPlugin.java")
        self.assertIn('realscale.experimental.client.uploadbudget',plugin)
        self.assertIn('"false"',plugin)

    def test_worldgen_experiment_kept_separate(self):
        beard = src("mixin/ExperimentalVanillaMixinPlugin.java")
        self.assertIn('"realscale.experimental.beardifier.ycull"',beard)
        self.assertIn('"true"',beard)
        self.assertNotIn("Aquifer",beard)

if __name__ == "__main__":
    unittest.main()
