"""Prevent accidental activation of the RealScale TEST-only vanilla GPU upload throttle."""
import unittest
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]

class RealScaleDhClientTests(unittest.TestCase):
    def test_experimental_client_upload_budget_is_opt_in(self):
        plugin=(ROOT/"neoforge/src/main/java/org/realscale/neoforge/mixin/ClientSectionUploadMixinPlugin.java").read_text()
        self.assertIn('System.getProperty("realscale.experimental.client.uploadbudget","false")',plugin)
        self.assertIn('REALSCALE_CLIENT_UPLOAD_MIXIN_DECISION',plugin)
    def test_2096_world_height_kept_for_all_lod_work(self):
        raw=(ROOT/"neoforge/src/main/resources/data/realscale/dimension_type/realism_max_overworld.json").read_text()
        world=json.loads(raw)
        self.assertEqual((world["min_y"],world["height"],world["logical_height"]),(-64,2096,2096))
    def test_patch_does_not_touch_terrain_generator_or_shader_pipeline(self):
        source=(ROOT/"dh-chunky-guard/src/main/java/org/realscale/compat/dhchunky/mixin/ChunkyLifecycleSetupMixin.java").read_text()
        self.assertIn('REALSCALE_DH_CHUNKY_GUARD_BOUND',source)
        self.assertNotIn('ShaderInstance',source)
        self.assertNotIn('DensityFunction',source)
if __name__=="__main__":unittest.main()
