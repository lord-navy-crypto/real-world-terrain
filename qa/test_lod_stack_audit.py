"""Test graphics/LOD log triage avoids unsupported rendering claims."""
import sys,unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent/"neoforge"))
from audit_lod_stack import analyze

class LodStackAuditTests(unittest.TestCase):
    def test_shaderpack_support_not_inferred(self):
        d=analyze('Found mod file "iris-neoforge-1.8.14-beta.1+mc1.21.1.jar"')
        self.assertTrue(d["mods_detected"]["iris"])
        self.assertEqual(d["shader_pack_support"],"unverified")
        self.assertEqual(d["gpu_lod_visible"],"unverified")
    def test_gl_error_is_not_worldgen_error(self):
        d=analyze("GL_INVALID_OPERATION")
        self.assertTrue(d["graphics_error_seen"])
        self.assertFalse(d["potential_vertical_error"])
    def test_no_personal_paths_in_output(self):
        private="/Users/test-user/Downloads/.minecraft/saves/private"
        self.assertNotIn(private,str(analyze(private)))
    def test_removed_guard_markers_are_not_interpreted(self):
        d=analyze("REALSCALE_DH_CHUNKY_GUARD_MOD_LOADED")
        self.assertNotIn("guard_loaded",d)
        self.assertNotIn("chunky",d["mods_detected"])

if __name__=="__main__":unittest.main()
