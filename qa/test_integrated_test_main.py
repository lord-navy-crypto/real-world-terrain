"""Safety invariants for the RealScale integrated testing branch."""
import json
import pathlib
import tomllib
import unittest

ROOT=pathlib.Path(__file__).resolve().parent.parent
RES=ROOT/"neoforge/src/main/resources"
PKG=ROOT/"neoforge/src/main/java/org/realscale/neoforge"
MOD=RES/"META-INF/neoforge.mods.toml"

class TestIntegratedMain(unittest.TestCase):
    def test_version_is_test_specific_and_gradle_matches(self):
        mods=tomllib.loads(MOD.read_text())
        self.assertEqual(mods["mods"][0]["version"],"2.0.0-tva-preview.2-test.5")
        self.assertIn("TEST",mods["mods"][0]["displayName"])
        self.assertIn("version='2.0.0-tva-preview.2-test.5'",
                      (ROOT/"neoforge/build.gradle").read_text())

    def test_three_unique_optin_mixin_configs(self):
        mods=tomllib.loads(MOD.read_text())
        configs=[m["config"] for m in mods.get("mixins",[])]
        self.assertEqual(len(configs),3)
        self.assertEqual(set(configs),{
            "realscale.experimental.mixins.json",
            "realscale.aquifer.audit.mixins.json",
            "realscale.client.experiment.mixins.json"})
        for name in configs:
            self.assertTrue((RES/name).is_file())
            self.assertTrue(json.loads((RES/name).read_text())["required"])

    def test_three_plugins_are_default_on_but_explicitly_overridable(self):
        switches={
            "mixin/ExperimentalVanillaMixinPlugin.java":
                 "realscale.experimental.beardifier.ycull",
            "mixin/AquiferReadOnlyAuditMixinPlugin.java":
                 "realscale.experimental.aquifer.audit",
            "mixin/ClientSectionUploadMixinPlugin.java":
                 "realscale.experimental.client.uploadbudget"}
        for path,flag in switches.items():
            code=(PKG/path).read_text()
            self.assertIn(flag,code,path)
            self.assertIn('Boolean.parseBoolean(System.getProperty(',code,path)
            self.assertIn('"true"',code,path)
            self.assertNotIn("Boolean.getBoolean(",code,path)
        for path,flag in {
            "RealScaleChunkUploadClient.java":
                "realscale.experimental.client.uploadbudget",
            "BeardifierDirectParity.java":
                "realscale.experimental.beardifier.verify",
            "RealScaleClientFrameProfiler.java":
                "realscale.client.fps.profile"}.items():
            code=(PKG/path).read_text()
            self.assertIn('"'+flag+'","true"',code.replace("\\n","").replace(" ",""),path)
            self.assertIn("Boolean.parseBoolean(System.getProperty(",code,path)

    def test_prod_flags_can_explicitly_disable_each_candidate(self):
        for path in ("mixin/ExperimentalVanillaMixinPlugin.java",
                     "mixin/AquiferReadOnlyAuditMixinPlugin.java",
                     "mixin/ClientSectionUploadMixinPlugin.java"):
            code=(PKG/path).read_text()
            self.assertIn('System.getProperty(',code)
            self.assertIn('Boolean.parseBoolean(',code)
            self.assertNotIn('Boolean.getBoolean(',code)

    def test_client_mixin_does_not_load_on_server(self):
        m=json.loads((RES/"realscale.client.experiment.mixins.json").read_text())
        self.assertEqual(m.get("client"),["VanillaSectionUploadBudgetMixin"])
        self.assertFalse(m.get("mixins"))
        self.assertIn("blockUntilClear",
                (PKG/"mixin/VanillaSectionUploadBudgetMixin.java").read_text())

    def test_rejected_perlin_and_y64_not_integrated(self):
        self.assertFalse((PKG/"ExactPerlinNoiseMemo.java").exists())
        self.assertFalse((PKG/"mixin/PerlinExactMemoMixin.java").exists())
        beard=(PKG/"BeardifierYExtent.java").read_text()
        self.assertIn("GUARD=256",beard)
        self.assertNotIn("tight_y",beard)

    def test_aquifer_cannot_modify_returns(self):
        code=(PKG/"mixin/AquiferReadOnlyAuditMixin.java").read_text()
        self.assertIn('at=@At("RETURN")',code)
        self.assertNotIn("cancellable=true",code)
        self.assertNotIn("setReturnValue(",code)

if __name__=="__main__":unittest.main()
