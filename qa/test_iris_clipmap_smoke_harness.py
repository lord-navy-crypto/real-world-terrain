from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
BUILD_GRADLE = (ROOT / "neoforge" / "build.gradle").read_text(encoding="utf-8")
SMOKE = (ROOT / "qa" / "neoforge" / "iris_clipmap_client_smoke.py").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github" / "workflows" / "clipmap-iris-render-smoke.yml").read_text(encoding="utf-8")
IRIS_MIXIN = (ROOT / "neoforge" / "src" / "main" / "java" / "org" / "realscale" / "neoforge" / "mixin" / "IrisGeometryClipmapMixin.java").read_text(encoding="utf-8")
GEOMETRY_CLIENT = (ROOT / "neoforge" / "src" / "main" / "java" / "org" / "realscale" / "neoforge" / "client" / "clipmap" / "GeometryClipmapClient.java").read_text(encoding="utf-8")
IRIS_RENDERER = (ROOT / "neoforge" / "src" / "main" / "java" / "org" / "realscale" / "neoforge" / "client" / "clipmap" / "IrisNativeClipmapRenderer.java").read_text(encoding="utf-8")
IRIS_COMPAT = (ROOT / "neoforge" / "src" / "main" / "java" / "org" / "realscale" / "neoforge" / "client" / "clipmap" / "IrisRenderCompatibility.java").read_text(encoding="utf-8")


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

    def test_iris_native_draw_uses_portable_terrain_stage_events(self):
        self.assertIn("prepareIrisFrameFog", IRIS_MIXIN)
        self.assertNotIn("ShaderInstance;clear()V", IRIS_MIXIN)
        self.assertIn("RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS", GEOMETRY_CLIENT)
        self.assertIn("RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS", GEOMETRY_CLIENT)
        self.assertIn("RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS", GEOMETRY_CLIENT)
        self.assertIn("event.getModelViewMatrix(), event.getProjectionMatrix()", GEOMETRY_CLIENT)
        self.assertIn("submitted && !iris.shadowPass() && !firstIrisRenderSubmitted", GEOMETRY_CLIENT)

    def test_iris_native_draw_rebinds_iris_shader_and_vertex_format(self):
        self.assertIn("terrainVertexFormat()", IRIS_COMPAT)
        self.assertIn('IrisVertexFormats', IRIS_COMPAT)
        self.assertIn("IrisRenderCompatibility.terrainVertexFormat()", IRIS_RENDERER)
        self.assertIn("renderType.setupRenderState()", IRIS_RENDERER)
        self.assertIn("shader.apply()", IRIS_RENDERER)
        self.assertIn("shader.clear()", IRIS_RENDERER)
        self.assertIn("renderType.clearRenderState()", IRIS_RENDERER)
        self.assertNotIn(".setOverlay(NO_OVERLAY)", IRIS_RENDERER)
        self.assertIn("GL_INVALID_OPERATION in glUniform(program not linked)", SMOKE)

    def test_client_moves_camera_above_spawn_terrain_before_visual_capture(self):
        self.assertIn("def prepare_open_rendering_view", SMOKE)
        self.assertIn("Loaded 2 advancements", SMOKE)
        self.assertIn("CLIENT_CAMERA_VANTAGE_READY", SMOKE)
        self.assertIn('rcon.send("forceload add 0 0")', SMOKE)
        self.assertIn('"fill -2 1199 -2 2 1199 2 minecraft:stone"', SMOKE)
        self.assertLess(
            SMOKE.index('rcon.send("forceload add 0 0")'),
            SMOKE.index('"fill -2 1199 -2 2 1199 2 minecraft:stone"'),
        )
        self.assertIn('rcon.send("setworldspawn 0 1200 0")', SMOKE)
        self.assertIn('rcon.send("forceload remove 0 0")', SMOKE)
        self.assertIn('"gamemode=spectator\\n"', SMOKE)
        self.assertIn('"force-gamemode=true\\n"', SMOKE)
        self.assertIn("spawn_y < 1100", SMOKE)
        self.assertIn('["xdotool", "mousemove_relative", "--sync", "0", "200"]', SMOKE)

    def test_client_dismisses_experimental_world_warning_before_draw_gate(self):
        self.assertIn("def experimental_warning_visible()", SMOKE)
        self.assertIn("def dismiss_experimental_warning()", SMOKE)
        self.assertIn('"tesseract"', SMOKE)
        self.assertIn('"xdotool"', SMOKE)
        self.assertIn("EXPERIMENTAL_SETTINGS_WARNING_DISMISSED", SMOKE)
        self.assertIn("xdotool", WORKFLOW)
        self.assertIn("tesseract-ocr", WORKFLOW)

    def test_workflow_archives_client_and_server_logs(self):
        self.assertIn("neoforge/run/logs/latest.log", WORKFLOW)
        self.assertIn("neoforge/run/iris-clipmap-client/logs/latest.log", WORKFLOW)
        self.assertIn("test_iris_clipmap_smoke_harness.py", WORKFLOW)


if __name__ == "__main__":
    unittest.main()
