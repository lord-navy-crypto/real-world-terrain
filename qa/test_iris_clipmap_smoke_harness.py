from pathlib import Path
import ast
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
BUILD_GRADLE = (ROOT / "neoforge" / "build.gradle").read_text(encoding="utf-8")
SMOKE = (ROOT / "qa" / "neoforge" / "iris_clipmap_client_smoke.py").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github" / "workflows" / "clipmap-iris-render-smoke.yml").read_text(encoding="utf-8")
IRIS_MIXIN = (ROOT / "neoforge" / "src" / "main" / "java" / "org" / "realscale" / "neoforge" / "mixin" / "IrisGeometryClipmapMixin.java").read_text(encoding="utf-8")
IRIS_FAR_MIXIN = (ROOT / "neoforge" / "src" / "main" / "java" / "org" / "realscale" / "neoforge" / "mixin" / "IrisCameraFarUniformMixin.java").read_text(encoding="utf-8")
GEOMETRY_CLIENT = (ROOT / "neoforge" / "src" / "main" / "java" / "org" / "realscale" / "neoforge" / "client" / "clipmap" / "GeometryClipmapClient.java").read_text(encoding="utf-8")
IRIS_RENDERER = (ROOT / "neoforge" / "src" / "main" / "java" / "org" / "realscale" / "neoforge" / "client" / "clipmap" / "IrisNativeClipmapRenderer.java").read_text(encoding="utf-8")
IRIS_COMPAT = (ROOT / "neoforge" / "src" / "main" / "java" / "org" / "realscale" / "neoforge" / "client" / "clipmap" / "IrisRenderCompatibility.java").read_text(encoding="utf-8")


class IrisClipmapSmokeHarnessTest(unittest.TestCase):
    def test_client_smoke_script_is_valid_python(self):
        ast.parse(SMOKE, filename="iris_clipmap_client_smoke.py")

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
        self.assertIn("heightRanges=", IRIS_RENDERER)
        self.assertIn(r"levels=6", SMOKE)
        self.assertIn('["identify", "-format"', SMOKE)
        self.assertIn("validate_screenshot(first)", SMOKE)
        self.assertIn("validate_screenshot(stable)", SMOKE)

    def test_continuous_movement_requires_new_windows_and_pixels(self):
        self.assertIn('["xdotool", "keydown", "w"]', SMOKE)
        self.assertIn("moving_centres", SMOKE)
        self.assertIn("len(centres) >= 1 and positive", SMOKE)
        self.assertIn("IRIS_CLIPMAP_MOVEMENT_REFRESH_PASS", SMOKE)

    def test_settlement_and_color_gate_require_final_client_viewport(self):
        self.assertIn("wait_for_settled_window", SMOKE)
        self.assertIn("CLIENT_PROXY_RECEIVED", SMOKE)
        self.assertIn("nativeProxyMeshesBuilt", SMOKE)
        self.assertIn("minimum_seconds=90", SMOKE)
        self.assertIn("iris-clipmap-after-motion-final", SMOKE)
        self.assertIn("854x240+0+0", SMOKE)
        self.assertIn("Persistent post-motion terrain contrast collapse", SMOKE)
        self.assertIn("IRIS_TERRAIN_CONTRAST_GATE_PASS", SMOKE)

    def test_native_proxy_meshes_are_independent_and_seams_use_valid_fine_samples(self):
        self.assertIn("proxySolidBuffer", IRIS_RENDERER)
        self.assertIn("rebuildProxies(safeProxies, centerX, centerZ)", IRIS_RENDERER)
        self.assertNotIn("proxies != cachedProxies || cachedTiles", IRIS_RENDERER)
        self.assertIn("parentX = (x / 2) * 2", IRIS_RENDERER)
        self.assertIn("ClipmapMorphMath.parentHeight(x, z, h00, h10, h01, h11)", IRIS_RENDERER)
        self.assertNotIn("parentTile.heights()", IRIS_RENDERER)
        self.assertIn("sprites.water()", IRIS_RENDERER)
        self.assertIn("activeIrisExtendedFogStart", GEOMETRY_CLIENT)

    def test_eye_level_capture_waits_for_complete_lods_and_proxies(self):
        self.assertIn("capture_natural_surface_view(proc, lines)", SMOKE)
        self.assertIn("iris-clipmap-natural-eye-level", SMOKE)
        self.assertIn("wait_for_complete_surface_assets", SMOKE)
        self.assertIn("NATURAL_VIEW_ASSETS_SETTLED", SMOKE)
        self.assertIn("nativeProxyMeshesBuilt", SMOKE)
        self.assertIn("terrain_color_count", SMOKE)
        self.assertIn("TERRAIN_VIEWPORT_COLOR_COUNT", SMOKE)
        self.assertIn("minimum_seconds=60", SMOKE)

    def test_native_terrain_restores_real_textures_and_doubles_grid_density(self):
        self.assertIn("ClipmapRingMesh.surfaceIndices(cells, holeRadius)", IRIS_RENDERER)
        self.assertIn("ClipmapTriangleUv.computeCellAlignedInto", IRIS_RENDERER)
        self.assertIn("topFace(shaper, grassState)", IRIS_RENDERER)
        self.assertIn("sprites.forMaterial(", IRIS_RENDERER)
        self.assertIn("ClipmapSurfacePalette.texturedTintAt(", IRIS_RENDERER)
        self.assertNotIn("sprites.white()", IRIS_RENDERER)
        layout = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/GeometryClipmapLayout.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("DEFAULT_GRID_CELLS = 256", layout)
        self.assertIn("DEFAULT_BASE_CELL_SPACING_BLOCKS = 4", layout)

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
        self.assertIn("shader.setDefaultUniforms(VertexFormat.Mode.TRIANGLES,", IRIS_RENDERER)
        self.assertEqual(5, IRIS_RENDERER.count("VertexFormat.Mode.TRIANGLES, irisTerrainFormat"))
        self.assertNotIn("VertexFormat.Mode.QUADS, irisTerrainFormat", IRIS_RENDERER)
        self.assertLess(IRIS_RENDERER.index("shader.setDefaultUniforms("),
                        IRIS_RENDERER.index("shader.apply()"))
        self.assertIn("drawProjection = activeIrisProjectionTarget;", GEOMETRY_CLIENT)
        self.assertIn("GL15.GL_SAMPLES_PASSED", IRIS_RENDERER)
        self.assertIn("FAR_SOLID_VISIBLE_SAMPLES=", IRIS_RENDERER)
        self.assertIn("IRIS_CAMERA_FAR_MIXIN_HIT", IRIS_FAR_MIXIN)
        self.assertIn("IRIS_CAMERA_FAR_EXTENDED", IRIS_FAR_MIXIN)
        self.assertIn("IRIS_FOG_RANGE original=", GEOMETRY_CLIENT)
        self.assertIn("IRIS_FOG_RANGE original=", SMOKE)
        self.assertIn("cameraY=", IRIS_RENDERER)
        self.assertIn("2200.0 <= float(m.group(2)) <= 2500.0", SMOKE)
        self.assertIn("drawWithVisibilityProbe(farSolidBuffer", IRIS_RENDERER)
        self.assertIn("level == 0 ? solidBuilder : farSolidBuilder", IRIS_RENDERER)
        self.assertIn("irisFarDistanceBlocks(int vanillaFar)", GEOMETRY_CLIENT)
        self.assertIn("not camera_prepared or not visible_samples_ready()", SMOKE)
        self.assertIn("shader.apply()", IRIS_RENDERER)
        self.assertIn("shader.clear()", IRIS_RENDERER)
        self.assertIn("renderType.clearRenderState()", IRIS_RENDERER)
        self.assertNotIn(".setOverlay(NO_OVERLAY)", IRIS_RENDERER)
        self.assertIn("GL_INVALID_OPERATION in glUniform(program not linked)", SMOKE)

    def test_camera_telemetry_regexes_parse_actual_markers(self):
        self.assertIn(r'logged in with entity id \d+', SMOKE)
        self.assertIn(r'CLIENT_CAMERA_VANTAGE_READY:\s*x=', SMOKE)

    def test_client_moves_camera_above_spawn_terrain_before_visual_capture(self):
        self.assertIn("def prepare_open_rendering_view", SMOKE)
        self.assertNotIn("def read_debug_camera_y()", SMOKE)
        self.assertIn("Loaded 2 advancements", SMOKE)
        self.assertIn("CLIENT_CAMERA_VANTAGE_READY", SMOKE)
        self.assertIn("CLIENT_CAMERA_VERIFIED", SMOKE)
        self.assertIn("ascent_deadline", SMOKE)
        self.assertIn("CLIENT_CAMERA_TARGET_REACHED", SMOKE)
        self.assertNotIn("time.sleep(25)", SMOKE)
        self.assertIn('["xdotool", "click", "--repeat", "24", "--delay", "20", "4"]', SMOKE)
        self.assertIn('["xdotool", "keydown", "space"]', SMOKE)
        self.assertIn('["xdotool", "keydown", "Control_L"]', SMOKE)
        self.assertIn("SMOKE_CAMERA ? 2300.0 : 1100.0", GEOMETRY_CLIENT)
        self.assertIn("realscale.experimental.geometryclipmap.smokeCamera", GEOMETRY_CLIENT)
        self.assertRegex(GEOMETRY_CLIENT, r"minecraft\.player\.setXRot\([0-9.]+f\)")
        self.assertIn("realscale.experimental.geometryclipmap.smokeCamera=true", SMOKE)
        self.assertIn('rcon.send("setworldspawn 0 1200 0")', SMOKE)
        self.assertIn('"gamemode=spectator\\n"', SMOKE)
        self.assertIn('"force-gamemode=true\\n"', SMOKE)

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
