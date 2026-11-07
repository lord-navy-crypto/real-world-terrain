"""Real worldgen smoke tests may be slow; never silently weaken their evidence."""
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
FITTED=ROOT/".github/workflows/fitted-terrain-worldgen-smoke.yml"
APES=ROOT/".github/workflows/apes-gallery.yml"

class RealWorldgenCiFairSampling(unittest.TestCase):
    def test_all_seven_fitted_presets_share_real_identical_column_bounds(self):
        source=FITTED.read_text()
        self.assertIn('FITTED_COMMON_CENTER_X: "-784"',source)
        self.assertIn('FITTED_COMMON_CENTER_Z: "-496"',source)
        self.assertIn('center_x = int(os.environ["FITTED_COMMON_CENTER_X"])',source)
        self.assertIn('center_z = int(os.environ["FITTED_COMMON_CENTER_Z"])',source)
        self.assertIn("FITTED_SHARED_SAMPLE_CENTER:",source)
        self.assertNotIn("locate biome minecraft:plains",source)
        # Still fail closed on actual Anvil samples and on lack of differences.
        self.assertIn("audit_fitted_archetype_heightmaps.py",source)
        self.assertIn("compare_fitted_archetype_heightmaps.py",source)
        self.assertIn("--target-chunk-min-x",source)
        self.assertIn("--target-chunk-max-z",source)
    def test_apes_startup_avoids_unnecessary_high_ceiling_spawn_load(self):
        source=APES.read_text()
        self.assertIn("spawn-chunk-radius=0",source)
        self.assertIn("deadline=time.time()+540",source)
        self.assertIn("execute if loaded",source)
        self.assertIn("check_apes_gallery.py",source)
        self.assertIn("assert", (ROOT/"qa/neoforge/check_apes_gallery.py").read_text())

if __name__=="__main__":
    unittest.main()
