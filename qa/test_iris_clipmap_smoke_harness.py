from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
BUILD_GRADLE = (ROOT / "neoforge" / "build.gradle").read_text(encoding="utf-8")
SMOKE = (ROOT / "qa" / "neoforge" / "iris_clipmap_client_smoke.py").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github" / "workflows" / "clipmap-iris-render-smoke.yml").read_text(encoding="utf-8")


class IrisClipmapSmokeHarnessTest(unittest.TestCase):
    def test_moddev_run_owns_quick_play_arguments(self):
        match = re.search(r"irisClipmapSmoke\s*\{(.*?)\n\s*\}", BUILD_GRADLE, re.S)
        self.assertIsNotNone(match, "Dedicated irisClipmapSmoke run configuration is missing")
        run = match.group(1)
        self.assertIn("client()", run)
        self.assertIn("gameDirectory = project.file('run/iris-clipmap-client')", run)
        self.assertIn("programArgument '--quickPlaySingleplayer'", run)
        self.assertIn("programArgument 'iris-clipmap-smoke'", run)

    def test_client_invokes_named_run_without_overriding_devlaunch_arguments(self):
        self.assertIn('"runIrisClipmapSmoke"', SMOKE)
        self.assertNotIn("--args=--quickPlaySingleplayer", SMOKE)

    def test_server_and_client_use_separate_game_directories(self):
        self.assertIn('SERVER = RUN', SMOKE)
        self.assertIn('CLIENT_RUN = RUN / "iris-clipmap-client"', SMOKE)
        self.assertIn('CLIENT_SAVES = CLIENT_RUN / "saves"', SMOKE)
        self.assertIn("gameDirectory = project.file('run/iris-clipmap-client')", BUILD_GRADLE)

    def test_render_gate_requires_six_levels_and_nonblank_screenshot(self):
        self.assertIn("nativeMeshesBuilt=", SMOKE)
        self.assertIn(r"levels=6", SMOKE)
        self.assertIn('["identify", "-format"', SMOKE)
        self.assertIn("validate_screenshot(first)", SMOKE)
        self.assertIn("validate_screenshot(stable)", SMOKE)

    def test_workflow_archives_client_and_server_logs(self):
        self.assertIn("neoforge/run/logs/latest.log", WORKFLOW)
        self.assertIn("neoforge/run/iris-clipmap-client/logs/latest.log", WORKFLOW)
        self.assertIn("test_iris_clipmap_smoke_harness.py", WORKFLOW)


if __name__ == "__main__":
    unittest.main()
