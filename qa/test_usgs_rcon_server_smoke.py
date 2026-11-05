#!/usr/bin/env python3
"""Guard the USGS server workflow against Gradle-stdin command false negatives."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class UsGsRconServerSmokeTests(unittest.TestCase):
    def test_server_workflow_enables_authenticated_rcon_and_uses_rcon_script(self):
        workflow = (ROOT / ".github/workflows/server.yml").read_text(encoding="utf-8")
        self.assertIn("enable-rcon=true", workflow)
        self.assertIn("rcon.port=25575", workflow)
        self.assertIn("rcon.password=realscale_ci_probe_only", workflow)
        self.assertIn("qa/neoforge/rcon_usgs_center_smoke.py", workflow)
        self.assertNotIn("p.stdin.write('forceload add", workflow)

    def test_rcon_script_requires_actual_chunk_generation_and_saved_preset(self):
        script = (ROOT / "qa/neoforge/rcon_usgs_center_smoke.py").read_text(encoding="utf-8")
        self.assertIn("forceload add -64 -64 63 63", script)
        self.assertIn("if loaded", script)
        self.assertIn("save-all flush", script)
        self.assertIn("realscale:usgs_big_sur", script)


if __name__ == "__main__":
    unittest.main()
