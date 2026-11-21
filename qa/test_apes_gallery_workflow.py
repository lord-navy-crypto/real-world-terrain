"""Keep the APES saved-world acceptance harness tied to Minecraft, not Gradle lifetime."""
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
WORKFLOW=ROOT/".github/workflows/apes-gallery.yml"


class ApesGalleryWorkflowContracts(unittest.TestCase):
    def test_rcon_runs_even_if_gradle_parent_exits_after_server_boot(self):
        source=WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("if ready.is_set():",source)
        self.assertNotIn("if ready.is_set() and p.poll() is None:",source)
        self.assertIn("APES_GRADLE_PARENT_STATE_AFTER_BOOT",source)
        self.assertIn("rcon_socket=connect_rcon()",source)

    def test_all_satellite_chunks_must_be_fully_loaded_and_saved(self):
        source=WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("positions=(112,128,144,160)",source)
        self.assertIn("forceload add {x} 0",source)
        self.assertIn("execute if loaded {x} 90 0",source)
        self.assertIn("APES_RCON_ALL_SATELLITE_CHUNKS_LOADED",source)
        self.assertIn("save-all flush",source)
        self.assertIn("rcon(rcon_socket,'stop',2502)",source)

    def test_anvil_gate_still_requires_all_sixteen_biomes_and_real_chunks(self):
        checker=(ROOT/"qa/neoforge/check_apes_gallery.py").read_text(encoding="utf-8")
        self.assertIn("assert chunks >= 40",checker)
        self.assertIn("assert {8, 9, 10}.issubset(outpost_full_x)",checker)
        self.assertIn("assert not missing",checker)


if __name__=="__main__":
    unittest.main()
