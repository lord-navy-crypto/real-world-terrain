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
STUDIO_CLIENT = (ROOT / "neoforge" / "src" / "main" / "java" / "org" / "realscale" / "neoforge" / "RealScaleStudioClient.java").read_text(encoding="utf-8")
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

    def test_continuous_movement_requires_verified_travel_and_new_windows(self):
        self.assertIn('["xdotool", "keydown", "Control_L"]', SMOKE)
        self.assertIn('["xdotool", "keydown", "d"]', SMOKE)
        self.assertIn("def move_camera_until_horizontal_distance", SMOKE)
        self.assertIn("target_distance: float = 320.0", SMOKE)
        self.assertIn("CLIENT_CAMERA_MOVEMENT_VERIFIED", SMOKE)
        self.assertIn("def wait_for_new_complete_center", SMOKE)
        self.assertIn("moving_centres = settled_centres", SMOKE)
        self.assertIn("settled_centre in old_centres", SMOKE)
        self.assertIn("settled_centre not in settled_centres", SMOKE)
        self.assertIn("CLIENT_MOVEMENT_SETTLED_CENTER", SMOKE)
        self.assertNotIn("settled_centre != new_centre", SMOKE)
        self.assertNotIn("during 30s continuous movement", SMOKE)
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
        self.assertIn("emitTriangle(water, sprites.neutral()", IRIS_RENDERER)
        self.assertIn("ClipmapSurfacePalette.waterColor()", IRIS_RENDERER)
        self.assertNotIn("sprites.water()", IRIS_RENDERER)
        self.assertIn("activeIrisExtendedFogStart", GEOMETRY_CLIENT)

    def test_far_field_render_smoke_measures_texture_aliasing(self):
        self.assertIn("def terrain_high_frequency_ratio(", SMOKE)
        self.assertIn("TERRAIN_HIGH_FREQUENCY_RATIO", SMOKE)
        self.assertIn("IRIS_TERRAIN_TEXTURE_ALIASING_GATE_PASS", SMOKE)
        self.assertIn("max(settled_high_frequency, final_high_frequency) > 0.18", SMOKE)
        self.assertIn('"-blur", "0x2"', SMOKE)

    def test_pause_menu_toggle_uses_real_window_geometry_and_upscaled_ocr(self):
        self.assertIn("def active_game_window_geometry(", SMOKE)
        self.assertIn("getactivewindow", SMOKE)
        self.assertIn('"--onlyvisible"', SMOKE)
        self.assertIn("CLIENT_WINDOW_GEOMETRY_FALLBACK", SMOKE)
        self.assertIn("OCR_USING_FULL_DISPLAY_FALLBACK", SMOKE)
        self.assertIn("def focus_minecraft_window(", SMOKE)
        self.assertIn('"windowfocus", "--sync"', SMOKE)
        self.assertIn("CLIENT_WINDOW_FOCUSED", SMOKE)
        self.assertIn("def ocr_game_window(", SMOKE)
        self.assertIn('"-resize", "200%"', SMOKE)
        self.assertIn("logical_width = window_width // gui_scale", SMOKE)
        self.assertIn("click_y = window_y + (button_y + 10) * gui_scale", SMOKE)

    def test_pause_menu_toggle_waits_for_actual_world_render(self):
        self.assertNotIn("CLIENT_WORLD_RENDER_READY:", GEOMETRY_CLIENT)
        self.assertIn("def ocr_loading_overlay(", SMOKE)
        self.assertIn('"-contrast-stretch", "1%x1%"', SMOKE)
        self.assertIn('["tesseract", str(crop), "stdout", "--psm", "7"]', SMOKE)
        self.assertIn('if "loading terrain" in latest:', SMOKE)
        self.assertIn("consecutive_clear >= 3", SMOKE)
        self.assertIn("def wait_until_world_rendered(", SMOKE)
        self.assertIn("wait_until_world_rendered()", SMOKE)

    def test_real_client_exercises_pause_menu_geometry_clipmap_toggle(self):
        self.assertIn("CLIENT_PAUSE_MENU_UI_INITIALIZED:", STUDIO_CLIENT)
        self.assertIn("CLIENT_PAUSE_MENU_TOGGLE_UPDATED:", STUDIO_CLIENT)
        self.assertIn("def wait_for_pause_screen(lines:", SMOKE)
        self.assertIn("def click_pause_menu_toggle(", SMOKE)
        self.assertIn('["xdotool", "click", "1"]', SMOKE)
        self.assertIn("position_pointer=True", SMOKE)
        self.assertIn("CLIENT_PAUSE_MENU_UI_INITIALIZED:", SMOKE)
        self.assertIn("CLIENT_PAUSE_MENU_TOGGLE_UPDATED:", SMOKE)
        self.assertIn("def test_pause_menu_geometry_clipmap_toggle(", SMOKE)
        self.assertIn("CLIPMAP_PAUSE_MENU_TOGGLE_PASS", SMOKE)
        self.assertIn('"OFF"', SMOKE)
        self.assertIn('"ON"', SMOKE)
        self.assertIn("[RealScale] Clipmap: OFF", SMOKE)
        self.assertIn("[RealScale] Clipmap: ON", SMOKE)
        self.assertEqual(
            SMOKE.count('["xdotool", "mousemove", "--sync", str(click_x), str(click_y)]'), 1
        )
        self.assertEqual(SMOKE.count('click_pause_menu_toggle(click_x, click_y)'), 2)

    def test_eye_level_capture_waits_for_complete_lods_and_proxies(self):
        self.assertIn("capture_natural_surface_view(proc, lines)", SMOKE)
        self.assertIn("iris-clipmap-natural-eye-level", SMOKE)
        self.assertIn("wait_for_complete_surface_assets", SMOKE)
        self.assertIn("NATURAL_VIEW_ASSETS_SETTLED", SMOKE)
        self.assertIn("nativeProxyMeshesBuilt", SMOKE)
        self.assertIn("terrain_color_count", SMOKE)
        self.assertIn("TERRAIN_VIEWPORT_COLOR_COUNT", SMOKE)
        self.assertIn("minimum_seconds=60", SMOKE)

    def test_all_lods_use_compact_neutral_uv_patch_and_procedural_materials(self):
        self.assertNotIn("MAX_TEXTURED_DETAIL_LEVEL", IRIS_RENDERER)
        self.assertNotIn("sprites.forMaterial(", IRIS_RENDERER)
        self.assertNotIn("ClipmapSurfacePalette.materialAt(", IRIS_RENDERER)
        self.assertIn("ClipmapSurfacePalette.terrainColorAt(", IRIS_RENDERER)
        self.assertIn("sprites.neutral()", IRIS_RENDERER)
        self.assertIn("Blocks.WHITE_CONCRETE.defaultBlockState()", IRIS_RENDERER)
        self.assertEqual(2, IRIS_RENDERER.count("ClipmapTriangleUv.computeNeutralCellAlignedInto("))
        self.assertNotIn("computeTerrainUvs", IRIS_RENDERER)
        self.assertNotIn("ClipmapTriangleUv.computeWorldAlignedInto(", IRIS_RENDERER)
        self.assertNotIn("ClipmapTriangleUv.computeCellAlignedInto(", IRIS_RENDERER)
        self.assertNotIn("worldHalfExtent", IRIS_RENDERER)
        self.assertNotIn("triangleUvs[0] = 8.0f", IRIS_RENDERER)
        self.assertIn("emitTriangle(water, sprites.neutral()", IRIS_RENDERER)
        palette = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/ClipmapSurfacePalette.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("complete terrain colors", palette)
        self.assertIn("actual grass, dry-ground, rock, sand and snow material", palette)
        self.assertNotIn("near-neutral multipliers", palette)

    def test_native_terrain_uses_compact_neutral_uv_patch_and_procedural_palette(self):
        self.assertIn("ClipmapRingMesh.surfaceIndices(cells, holeRadius)", IRIS_RENDERER)
        self.assertIn("ClipmapTriangleUv.computeNeutralCellAlignedInto(", IRIS_RENDERER)
        self.assertNotIn("computeTerrainUvs", IRIS_RENDERER)
        self.assertNotIn("ClipmapTriangleUv.computeWorldAlignedInto(", IRIS_RENDERER)
        self.assertIn("ClipmapSurfacePalette.terrainColorAt(", IRIS_RENDERER)
        self.assertIn("sprites.neutral()", IRIS_RENDERER)
        self.assertNotIn("sprites.forMaterial(", IRIS_RENDERER)
        self.assertNotIn("ClipmapSurfacePalette.materialAt(", IRIS_RENDERER)
        self.assertNotIn("sprites.white()", IRIS_RENDERER)
        uv = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/ClipmapTriangleUv.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("NEUTRAL_UV_MIN = 7.5f", uv)
        self.assertIn("NEUTRAL_UV_RANGE = 1.0f", uv)
        self.assertIn("computeCellAlignedPatchInto", uv)
        self.assertIn("sprite.getU(u), sprite.getV(v)", IRIS_RENDERER)

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
        self.assertIn("FAR_WATER_VISIBLE_SAMPLES=", IRIS_RENDERER)
        self.assertIn("FAR_(?:SOLID|WATER)_VISIBLE_SAMPLES=", SMOKE)
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
        self.assertIn(r'pitch=([-+0-9.]+)\s+yaw=([-+0-9.]+)', SMOKE)

        self.assertIn("CLIENT_CAMERA_POSE_VERIFIED", SMOKE)
        self.assertIn("expected_pose = (0.5, 2304.9, 1.5, 35.0, 45.0)", SMOKE)
        self.assertIn(r'CLIENT_CAMERA_POSITION:\s*x=', SMOKE)
        self.assertIn("CLIENT_CAMERA_POSITION:", GEOMETRY_CLIENT)
        self.assertIn("lastSmokeCameraTelemetryNanos", GEOMETRY_CLIENT)

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
        self.assertIn("SMOKE_CAMERA_X = 0.5d", GEOMETRY_CLIENT)
        self.assertIn("SMOKE_CAMERA_Y = 2304.9d", GEOMETRY_CLIENT)
        self.assertIn("SMOKE_CAMERA_Z = 1.5d", GEOMETRY_CLIENT)
        self.assertIn("minecraft.player.setPos(", GEOMETRY_CLIENT)
        self.assertIn("realscale.experimental.geometryclipmap.smokeCamera=true", SMOKE)
        self.assertIn('rcon.send("setworldspawn 0 1200 0")', SMOKE)
        self.assertIn('"gamemode=spectator\\n"', SMOKE)
        self.assertIn('"force-gamemode=true\\n"', SMOKE)
        run_client = SMOKE[SMOKE.index("def run_client"):SMOKE.index("def main")]
        self.assertLess(
            run_client.index("prepare_open_rendering_view(client_latest, lines)"),
            run_client.index("capture_natural_surface_view(proc, lines)"),
            "The camera must be moved to the terrain viewing position before the visual capture gate",
        )

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
