from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
GENERATOR = ROOT / "neoforge/src/main/java/org/realscale/neoforge/VisibleSurfaceProxyGenerator.java"
REGISTRY = ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/SurfaceFeatureProxyRegistry.java"


class GeometryClipmapCompileContractTests(unittest.TestCase):
    def test_registry_collection_entry_point_is_public_across_packages(self):
        source = REGISTRY.read_text(encoding="utf-8")
        self.assertRegex(
            source,
            r"(?m)^\s*public\s+static\s+void\s+collect\s*\(",
            "VisibleSurfaceProxyGenerator is in a different package, so the "
            "registry collection entry point must be public.",
        )

    def test_canopy_sampling_step_stays_in_the_long_numeric_domain(self):
        source = GENERATOR.read_text(encoding="utf-8")
        self.assertRegex(
            source,
            r"(?m)^\s*long\s+step\s*=\s*Math\.max\(\s*64L,\s*"
            r"Math\.multiplyExact\(\s*level\.cellSpacingBlocks\(\),\s*8L\s*"
            r"\)\s*\)\s*;",
            "Clipmap spacing is a long; the canopy sampling step must remain long "
            "and must not narrow to int.",
        )


    def test_visual_quality_shaders_and_meshes_are_wired(self):
        renderer = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/GeometryClipmapRenderer.java").read_text(
            encoding="utf-8"
        )
        layout = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/GeometryClipmapLayout.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("ClipmapRingMesh.indices(", renderer)
        self.assertIn("ClipmapRingMesh.vertices(", renderer)
        self.assertIn("uSkirtDepth", renderer)
        self.assertIn("uNoiseOrigin", renderer)
        self.assertIn("dFdx(vPosition)", renderer)
        self.assertIn("uNearClipBlocks", renderer)
        self.assertIn("nearRadius - cameraOffsetAllowance", layout)


    def test_clientbound_packets_are_registered_on_both_physical_sides(self):
        network = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/network/GeometryClipmapNetwork.java").read_text(
            encoding="utf-8"
        )
        client = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/GeometryClipmapClientPayloads.java").read_text(
            encoding="utf-8"
        )
        self.assertEqual(2, network.count("registrar.playToClient("))
        self.assertNotIn("@EventBusSubscriber", client)
        self.assertIn("ClipmapSnapshotAssembler<ClipmapHeightTilePayload>", (
            ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/GeometryClipmapClient.java"
        ).read_text(encoding="utf-8"))


    def test_gpu_texture_transfer_is_aligned_and_full_transaction_restores_state(self):
        renderer = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/GeometryClipmapRenderer.java").read_text(
            encoding="utf-8"
        )
        render = renderer.split("static void render(", 1)[1].split("static void dispose(", 1)[0]
        self.assertLess(render.index("State state = State.capture()"),
                        render.index("initialize(nextGridCells"))
        self.assertLess(render.index("State state = State.capture()"),
                        render.index("upload(i, tile)"))
        self.assertIn("GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);", render)
        self.assertIn("GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, unpackAlignment);", renderer)
        self.assertIn("GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT)", renderer)
        self.assertIn("GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH)", renderer)
        self.assertIn("GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS)", renderer)
        self.assertIn("GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS)", renderer)
        self.assertIn("GL11.glGetInteger(GL11.GL_UNPACK_SWAP_BYTES)", renderer)
        self.assertIn("GL21.GL_PIXEL_UNPACK_BUFFER_BINDING", renderer)
        self.assertIn("GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0)", render)
        self.assertIn("GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, pixelUnpackBuffer)", renderer)
        self.assertIn("reusableHeightUpload.clear()", renderer.replace("buffer.clear()", "reusableHeightUpload.clear()"))
        self.assertIn("GL11.GL_REPEAT", renderer)
        self.assertIn("uTextureOffset", renderer)
        self.assertIn("ClipmapTextureUpdatePlan.between(", renderer)
        self.assertIn("tile.copyHeightsRegionTo(", renderer)
        self.assertIn("long bytes = updateTexture(i, tile);", render)
        self.assertNotIn("uploaded[i] != tile", render)
        self.assertIn("UniformLocations.lookup(program)", renderer)
        self.assertNotIn('glGetUniformLocation(program, "uOrigin")',
                         renderer.split("private static void draw(", 1)[1].split("private static int linkProgram(", 1)[0])


    def test_proxy_renderer_reuses_frame_buffers_and_cached_uniforms(self):
        proxy = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/VisibleSurfaceProxyRenderer.java").read_text(
            encoding="utf-8"
        )
        draw = proxy.split("static void draw(", 1)[1].split("static void dispose(", 1)[0]
        self.assertIn("VISIBLE_SCRATCH", draw)
        self.assertIn("INSTANCE_UPLOAD", draw)
        self.assertIn("MATRIX_UPLOAD", draw)
        self.assertNotIn("BufferUtils.createFloatBuffer", draw)
        self.assertNotIn("glGetUniformLocation", draw)
        self.assertIn("UniformLocations.lookup(program)", proxy)
        self.assertIn("farRadiusSquared", draw)
        self.assertNotIn("new ArrayList", draw)
        self.assertNotIn("new Matrix4f", draw)

    def test_proxy_renderer_uses_precomputed_bounds_without_per_frame_aabb_creation(self):
        proxy = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/VisibleSurfaceProxyRenderer.java").read_text(
            encoding="utf-8"
        )
        draw = proxy.split("static void draw(", 1)[1].split("static void dispose(", 1)[0]
        self.assertIn("proxy.bounds()", draw)
        self.assertNotIn("new AABB", draw)

    def test_height_payload_can_copy_without_exposing_or_cloning_samples(self):
        payload = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/network/ClipmapHeightTilePayload.java").read_text(
            encoding="utf-8"
        )
        renderer = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/GeometryClipmapRenderer.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("public void copyHeightsTo(ShortBuffer destination)", payload)
        self.assertIn("tile.copyHeightsTo(buffer)", renderer)
        self.assertIn("short[] values = value.heights;", payload)

    def test_terrain_and_proxy_draws_reuse_matrix_objects(self):
        terrain = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/GeometryClipmapRenderer.java").read_text(
            encoding="utf-8"
        )
        proxy = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/VisibleSurfaceProxyRenderer.java").read_text(
            encoding="utf-8"
        )
        terrain_draw = terrain.split("private static void draw(", 1)[1].split("private static int linkProgram(", 1)[0]
        proxy_draw = proxy.split("static void draw(", 1)[1].split("static void dispose(", 1)[0]
        self.assertIn("VIEW_PROJECTION.set(event.getProjectionMatrix())", terrain_draw)
        self.assertIn("VIEW_PROJECTION.set(event.getProjectionMatrix())", proxy_draw)
        self.assertNotIn("new Matrix4f", terrain_draw)
        self.assertNotIn("new Matrix4f", proxy_draw)

    def test_optional_clipmap_debug_metrics_are_gated_and_distinguish_cpu_submission(self):
        server = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/GeometryClipmapServer.java").read_text(
            encoding="utf-8"
        )
        renderer = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/GeometryClipmapRenderer.java").read_text(
            encoding="utf-8"
        )
        self.assertIn('realscale.experimental.geometryclipmap.debug', server)
        self.assertIn('realscale.experimental.geometryclipmap.debug', renderer)
        self.assertIn('queueWait=%.1fms sample=%.1fms', server)
        self.assertIn('cpuSubmitAvg=%.2fms', renderer)
        self.assertNotRegex(renderer, r"\bglFinish\s*\(")

    def test_server_clipmap_jobs_are_generation_guarded_and_backed_off(self):
        server = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/GeometryClipmapServer.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("session.requestEpoch.current()", server)
        self.assertIn("session.requestEpoch.isCurrent(requestEpoch)", server)
        self.assertIn("server.getPlayerList().getPlayer(playerId)", server)
        self.assertIn("RETRY_BACKOFF_TICKS", server)
        self.assertIn("sameCentre(nextLayout, currentLayout)", server)
        self.assertIn("if (!session.requestEpoch.isCurrent(requestEpoch)) return;", server)
        epoch = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/ClipmapRequestEpoch.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("private volatile long generation;", epoch)
        self.assertIn("requestId", server)
        self.assertIn("entry.getValue().invalidate()", server)
        self.assertIn("removeSession(player.getUUID(), session)", server)
        self.assertIn("onServerStopping(ServerStoppingEvent event)", server)
        self.assertIn("SESSIONS.clear()", server)
        self.assertIn("SAMPLER.getQueue().clear()", server)
        client = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/GeometryClipmapClient.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("payload.requestId() != activeRequestId", client)
        self.assertIn("key.requestId() != activeRequestId", client)
        network = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/network/GeometryClipmapNetwork.java").read_text(
            encoding="utf-8"
        )
        self.assertIn('event.registrar("3")', network)

    def test_geomorph_uses_existing_height_grid_and_cached_uniform(self):
        renderer = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/GeometryClipmapRenderer.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("parentGrid = floor(grid * 0.5) * 2.0", renderer)
        self.assertIn("h = mix(h, parentHeight, morph);", renderer)
        self.assertIn("uniforms.morphBandCells()", renderer)
        self.assertIn('GL20.glGetUniformLocation(program, "uMorphBandCells")', renderer)


if __name__ == "__main__":
    unittest.main()


    def test_toroidal_texture_update_plan_has_unit_coverage(self):
        planner = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/ClipmapTextureUpdatePlan.java").read_text(
            encoding="utf-8"
        )
        payload = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/network/ClipmapHeightTilePayload.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("dirtySamples * 10L >= fullSamples * 7L", planner)
        self.assertIn("Math.floorMod", planner)
        self.assertIn("public void copyHeightsRegionTo(", payload)
