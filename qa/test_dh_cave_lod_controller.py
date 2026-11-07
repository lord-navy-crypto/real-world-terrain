from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "neoforge/src/main/java/org/realscale/neoforge"


class DhCaveLodControllerTests(unittest.TestCase):
    def test_controller_is_opt_in_and_uses_public_dh_config_api(self):
        source = (JAVA / "DistantHorizonsCaveLodController.java").read_text(encoding="utf-8")
        self.assertIn('realscale.experimental.client.underground_lod_culling', source)
        self.assertIn('ModList.get().isLoaded("distanthorizons")', source)
        self.assertIn('DhApi$Delayed', source)
        self.assertIn('IDhApiConfig', source)
        self.assertIn('IDhApiGraphicsConfig', source)
        self.assertIn('caveCullingEnabled', source)
        self.assertIn('getApiValue', source)
        self.assertIn('getCanBeOverrodeByApi', source)
        self.assertIn('clearValue', source)
        self.assertIn('restore()', source)
        # Do not rewrite the user's cave threshold or any terrain data.
        self.assertNotIn('caveCullingHeight', source)
        self.assertIn('vanilla section meshes unchanged', source)

    def test_only_confident_open_surface_culls_distant_cave_lods(self):
        policy = (JAVA / "AdaptiveVisibilityBudget.java").read_text(encoding="utf-8")
        client = (JAVA / "RealScaleAdaptiveVisibilityClient.java").read_text(encoding="utf-8")
        self.assertIn(
            'return mode == Mode.OPEN_SURFACE;',
            policy,
        )
        self.assertIn('shouldCullDistantHorizonCaves(mode)', client)
        self.assertIn('DistantHorizonsCaveLodController.observe(mode)', client)
        self.assertIn('DistantHorizonsCaveLodController.observe(', client)
        self.assertIn('Mode.TRANSITION', client)

    def test_open_ravines_and_unloaded_neighbors_fail_open(self):
        client = (JAVA / "RealScaleAdaptiveVisibilityClient.java").read_text(encoding="utf-8")
        self.assertIn('RAVINE_HEIGHT_PROBES', client)
        self.assertIn('hasDeepOpenCut(level,eye)', client)
        self.assertIn('if(!loaded(level,sample))return true;', client)
        self.assertIn('openingOrRavine', client)
        self.assertIn('never request/force a missing chunk', client)

    def test_controller_restores_user_setting_on_world_exit(self):
        client = (JAVA / "RealScaleAdaptiveVisibilityClient.java").read_text(encoding="utf-8")
        controller = (JAVA / "DistantHorizonsCaveLodController.java").read_text(encoding="utf-8")
        self.assertIn('if(lastLevel!=null)DistantHorizonsCaveLodController.restore();', client)
        self.assertIn('DistantHorizonsCaveLodController.restore();', client)
        self.assertIn('if(Objects.equals(currentApiValue,appliedValue))', controller)
        self.assertIn('clearValue.invoke(caveCullingValue)', controller)


if __name__ == "__main__":
    unittest.main()
