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
        self.assertIn("vSmoothNormal", renderer)
        self.assertIn("vNormalBlend", renderer)
        self.assertIn("surfaceHeightAt", renderer)
        self.assertIn("uInnerHalfCells", renderer)
        self.assertIn("filteredValueNoise", renderer)
        self.assertIn("dFdx(p)", renderer)
        self.assertIn("dFdy(p)", renderer)
        self.assertIn("smoothstep(0.20, 0.65, footprint)", renderer)
        self.assertIn("mix(0.97, 1.03, regional)", renderer)
        self.assertNotIn("vMicro", renderer)
        self.assertIn("uNearClipBlocks", renderer)
        self.assertIn("nearRadius - cameraOffsetAllowance", layout)


    def test_clientbound_packets_are_registered_on_both_physical_sides(self):
        network = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/network/GeometryClipmapNetwork.java").read_text(
            encoding="utf-8"
        )
        client = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/GeometryClipmapClientPayloads.java").read_text(
            encoding="utf-8"
        )
        self.assertEqual(3, network.count("registrar.playToClient("))
        self.assertNotIn("@EventBusSubscriber", client)
        self.assertIn("ClipmapSnapshotAssembler<ClipmapHeightTilePayload>", (
            ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/GeometryClipmapClient.java"
        ).read_text(encoding="utf-8"))


    def test_gpu_texture_transfer_is_aligned_and_full_transaction_restores_state(self):
        renderer = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/GeometryClipmapRenderer.java").read_text(
            encoding="utf-8"
        )
        render = renderer.split("static boolean render(", 1)[1].split("static void dispose(", 1)[0]
        self.assertLess(render.index("State state = State.capture()"),
                        render.index("initialize(nextGridCells"))
        self.assertLess(render.index("State state = State.capture()"),
                        render.index("updateTexture(i, tile)"))
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
        self.assertIn("INSTANCE_UPLOAD", proxy)
        self.assertIn("MATRIX_UPLOAD", draw)
        self.assertIn("uploadedProxies != proxies", draw)
        self.assertIn("uploadInstances(proxies, centerX, centerZ)", draw)
        self.assertIn("GL15.GL_STATIC_DRAW", proxy)
        self.assertIn("uCameraOffset", proxy)
        self.assertIn("uNearRadiusSquared", proxy)
        self.assertIn("uFarRadiusSquared", proxy)
        self.assertNotIn("BufferUtils.createFloatBuffer", draw)
        self.assertNotIn("glGetUniformLocation", draw)
        self.assertIn("UniformLocations.lookup(program)", proxy)
        self.assertNotIn("new ArrayList", proxy)
        self.assertNotIn("new Matrix4f", draw)
        self.assertNotIn("VISIBLE_SCRATCH", proxy)
        self.assertNotIn("event.getFrustum()", proxy)
        self.assertIn("centreX", (
            ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/GeometryClipmapRenderer.java"
        ).read_text(encoding="utf-8"))

    def test_proxy_renderer_uses_gpu_frustum_and_distance_clipping(self):
        proxy = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/VisibleSurfaceProxyRenderer.java").read_text(
            encoding="utf-8"
        )
        feature = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/VisibleSurfaceProxy.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("uNearRadiusSquared", proxy)
        self.assertIn("uFarRadiusSquared", proxy)
        self.assertIn("centerDistanceSquared = dot(centerRelative.xz, centerRelative.xz)", proxy)
        self.assertIn("uViewProjection * vec4(relative, 1.0)", proxy)
        self.assertNotIn("proxy.bounds()", proxy)
        self.assertNotIn("new AABB", feature)

    def test_height_payload_can_copy_without_exposing_or_cloning_samples(self):
        payload = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/network/ClipmapHeightTilePayload.java").read_text(
            encoding="utf-8"
        )
        renderer = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/GeometryClipmapRenderer.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("public void copyHeightsTo(ShortBuffer destination)", payload)
        self.assertIn("public void copyHeightsRegionTo(", payload)
        self.assertIn("tile.copyHeightsRegionTo(", renderer)
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
        self.assertIn("EXTENDED_PROJECTION.set(event.getProjectionMatrix())", terrain_draw)
        self.assertIn("VIEW_PROJECTION.set(projection).mul(modelView)", terrain_draw)
        self.assertIn("Matrix4f extendedProjection", proxy_draw)
        self.assertIn("VIEW_PROJECTION.set(extendedProjection)", proxy_draw)
        self.assertNotIn("VIEW_PROJECTION.set(event.getProjectionMatrix())", proxy_draw)
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
        self.assertIn('vertices/s; cancelled=', server)
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
        self.assertNotIn("sameCentre(nextLayout, currentLayout)", server)
        self.assertIn("PacketDistributor.sendToPlayer(live, payload)", server)
        self.assertIn("PROXY_SAMPLER.execute(task)", server)
        self.assertIn("cancelProxyTasksForHeightSampling()", server)
        self.assertIn("PROXY_TASK_CANCELLED reason=height-window", server)
        self.assertIn("active for %.1fs", server)
        self.assertIn("ringTaskRunning", server)
        self.assertIn("pendingRingCallbacks", server)
        self.assertIn("samplerHasStopped", server)
        self.assertIn("SAMPLER.execute(this)", server)
        self.assertIn("if (!session.requestEpoch.isCurrent(requestEpoch)) return;", server)
        self.assertIn("() -> session.requestEpoch.isCurrent(requestEpoch)", server)
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
        self.assertIn("PROXY_BUFFER.onHeightSnapshot(key)", client)
        self.assertIn("PROXY_BUFFER.accept(", client)
        self.assertIn("firstSnapshotReady=%.1fms", client)
        self.assertIn("firstEligibleRenderSubmission=%.1fms", client)
        renderer_source = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/GeometryClipmapRenderer.java").read_text(encoding="utf-8")
        proxy_source = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/VisibleSurfaceProxyRenderer.java").read_text(encoding="utf-8")
        iris = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/IrisRenderCompatibility.java").read_text(encoding="utf-8")
        self.assertIn("isShaderPackInUse", iris)
        self.assertIn("isRenderingShadowPass", iris)
        self.assertIn("allowsRawOpenGlRenderer()", client)
        fog = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/ClipmapFogColor.java").read_text(encoding="utf-8")
        self.assertIn("RenderSystem.getShaderFogColor()", fog)
        self.assertIn("ClipmapFogColor.upload(uniforms.fogColor())", renderer_source)
        self.assertIn("ClipmapFogColor.upload(uniforms.fogColor())", proxy_source)
        self.assertIn("VisibleSurfaceProxyRenderer.draw(event, projection,", renderer_source)
        self.assertIn("VIEW_PROJECTION.set(extendedProjection)", proxy_source)
        self.assertNotIn("VIEW_PROJECTION.set(event.getProjectionMatrix())", proxy_source)
        self.assertIn("finally", renderer_source.split("private static int linkProgram(", 1)[1].split("private static int compileShader(", 1)[0])
        self.assertIn("finally", proxy_source.split("private static int link(", 1)[1].split("private static int compile(", 1)[0])
        self.assertIn("dispose();", renderer_source.split("private static void initialize(", 1)[1].split("private static long updateTexture(", 1)[0])
        self.assertIn("dispose();", proxy_source.split("private static void ensureResources(", 1)[1].split("private record UniformLocations", 1)[0])
        self.assertIn("if (!drawSubmitted) return;", client)
        self.assertIn("static boolean render(", renderer_source)
        self.assertNotIn("return;", renderer_source.split("static boolean render(", 1)[1].split("private static void recordDebugFrame(", 1)[0])
        settings = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/GeometryClipmapSettings.java").read_text(
            encoding="utf-8"
        )
        studio = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/RealScaleStudioClient.java").read_text(
            encoding="utf-8"
        )
        self.assertIn('Path.of("config", "realscale-geometry-clipmap.properties")', settings)
        self.assertIn("private static volatile boolean enabled = loadEnabled(CONFIG_FILE)", settings)
        self.assertIn("if (!Files.isRegularFile(path))", settings)
        self.assertIn('properties.getProperty("enabled", "false")', settings)
        self.assertIn("The default is OFF", settings)
        self.assertIn("return false;", settings)
        self.assertIn("GeometryClipmapSettings.isEnabled()", client)
        self.assertIn("public static void toggleEnabled()", client)
        self.assertIn("GeometryClipmapClient.toggleEnabled()", studio)
        self.assertIn("Geometry Clipmap: ", studio)
        self.assertIn('"realscale.experimental.geometryclipmap.debug", "true"', client)
        self.assertIn("displayClientMessage(Component.literal(message), false)", client)
        self.assertIn("Clipmap: REQUEST SENT", client)
        self.assertIn("Clipmap: SNAPSHOT READY", client)
        self.assertIn("Clipmap: GPU DRAW SUBMITTED", client)
        self.assertIn("Clipmap: NO SERVER ACKNOWLEDGEMENT", client)
        self.assertIn("Clipmap: NO PROGRESS", client)
        self.assertIn("serverStateSinceNanos", client)
        self.assertIn("reportProgress(minecraft", client)
        self.assertIn("displayClientMessage(Component.literal(message), true)", client)
        self.assertIn("serverDetail", client)
        self.assertIn("serverStateSinceNanos", client)
        self.assertIn("HEARTBEAT_INTERVAL_NANOS", client)
        self.assertIn("connectionToken != player.connection", server)
        self.assertIn("current.statusDetail", server)
        self.assertIn("nextProgressThreshold", server)
        self.assertIn("pendingLevelCount()", client)
        self.assertIn("ClipmapStatusPayload.UNSUPPORTED_WORLD", client)
        grid = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/ClipmapHeightGrid.java").read_text(encoding="utf-8")
        self.assertIn("IntConsumer progress", grid)
        self.assertIn("progress.accept(100)", grid)
        renderer = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/GeometryClipmapRenderer.java").read_text(
            encoding="utf-8"
        )
        projection_math = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/ClipmapProjectionMath.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("ClipmapProjectionMath.extendFarPlane", renderer)
        self.assertIn("fogRange.endBlocks() * 1.5f", renderer)
        self.assertIn("projectionFar vanilla=", renderer)
        self.assertIn("projection.m22(", projection_math)
        self.assertIn("projection.m32(", projection_math)
        network = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/network/GeometryClipmapNetwork.java").read_text(
            encoding="utf-8"
        )
        self.assertIn('event.registrar("6")', network)
        self.assertIn("ClipmapStatusPayload.TYPE", network)
        self.assertNotIn("STILL WAITING", client)

    def test_abort_sampling_within_one_ring_and_bounded_proxy_staging(self):
        grid = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/ClipmapHeightGrid.java").read_text(
            encoding="utf-8"
        )
        buffer = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/ClipmapProxySnapshotBuffer.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("CancellationException", grid)
        self.assertIn("(index & 31) == 0", grid)
        self.assertIn("BooleanSupplier stillCurrent", grid)
        self.assertIn("private Slot<T> staged;", buffer)
        self.assertIn("private Slot<T> active;", buffer)

    def test_geomorph_uses_existing_height_grid_and_cached_uniform(self):
        renderer = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/GeometryClipmapRenderer.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("parentGrid = floor(grid * 0.5) * 2.0", renderer)
        self.assertIn("h = mix(h, parentHeight, morph);", renderer)
        self.assertIn("uniforms.morphBandCells()", renderer)
        self.assertIn('GL20.glGetUniformLocation(program, "uOrigin[0]")', renderer)
        self.assertIn('GL20.glGetUniformLocation(program, "uMorphBandCells[0]")', renderer)
        self.assertIn("GL20.glUniform1fv(uniforms.spacing()", renderer)
        self.assertIn("GL20.glUniform2fv(uniforms.origin()", renderer)


    def test_dynamic_environment_tracks_dimension_sun_and_weather(self):
        environment = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/ClipmapEnvironment.java").read_text(
            encoding="utf-8"
        )
        renderer = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/GeometryClipmapRenderer.java").read_text(
            encoding="utf-8"
        )
        proxy = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/VisibleSurfaceProxyRenderer.java").read_text(
            encoding="utf-8"
        )
        client = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/GeometryClipmapClient.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("level.getSeaLevel()", environment)
        self.assertIn("level.getSunAngle(partialTick)", environment)
        self.assertIn("level.getRainLevel(partialTick)", environment)
        self.assertIn("level.getThunderLevel(partialTick)", environment)
        self.assertIn("environment.seaLevel()", renderer)
        self.assertIn("uSeaLevel + 2.0", renderer)
        self.assertIn("uSeaLevel + 197.0", renderer)
        self.assertIn("uSeaLevel + 677.0", renderer)
        self.assertIn('"uSunDirection"', renderer)
        self.assertNotIn("vec3 sun = normalize(vec3(0.38", renderer)
        self.assertIn('"uLightLevel"', proxy)
        self.assertIn("ClipmapEnvironment.from(minecraft.level, partialTick)", client)


    def test_horizon_material_noise_is_fragment_filtered_to_prevent_lod_aliasing(self):
        renderer = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/GeometryClipmapRenderer.java").read_text(
            encoding="utf-8"
        )
        vertex_shader = renderer.split("private static final String VERTEX_SHADER", 1)[1].split(
            "private static final String FRAGMENT_SHADER", 1
        )[0]
        fragment_shader = renderer.split("private static final String FRAGMENT_SHADER", 1)[1].split(
            "private static final FloatBuffer MATRIX_UPLOAD", 1
        )[0]
        self.assertIn("vNoisePosition = levelNoiseOrigin + grid * levelSpacing", vertex_shader)
        self.assertNotIn("valueNoise(", vertex_shader)
        self.assertIn("filteredValueNoise", fragment_shader)
        self.assertIn("dFdx(p)", fragment_shader)
        self.assertIn("dFdy(p)", fragment_shader)
        self.assertIn("smoothstep(0.20, 0.65, footprint)", fragment_shader)
        self.assertIn("mix(0.97, 1.03, regional)", fragment_shader)
        self.assertIn("normalize(mix(faceNormal", fragment_shader)
        self.assertIn("smoothstep(0.16, 0.55, slope)", fragment_shader)
        self.assertIn("vNormalBlend", vertex_shader)
        self.assertIn("hLeft - hRight", vertex_shader)
        self.assertIn("dot(normal, uSunDirection)", fragment_shader)


    def test_iris_smoke_installs_client_mods_after_server_world_creation(self):
        script = (ROOT / "qa/neoforge/iris_clipmap_client_smoke.py").read_text(
            encoding="utf-8"
        )
        main = script.split("def main() -> int:", 1)[1].split("if __name__")[0]
        self.assertLess(main.index("prepare_saved_world(env)"),
                        main.index("install_iris_stack()"))
        self.assertLess(main.index("install_iris_stack()"),
                        main.index("run_client(env)"))

        self.assertIn('SERVER = RUN  # runServer uses the module run directory as --gameDir .', script)
    def test_iris_native_clipmap_uses_vanilla_gbuffer_and_shadow_terrain_passes(self):
        native = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/IrisNativeClipmapRenderer.java").read_text(
            encoding="utf-8"
        )
        mixin = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/mixin/IrisGeometryClipmapMixin.java").read_text(
            encoding="utf-8"
        )
        client = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/GeometryClipmapClient.java").read_text(
            encoding="utf-8"
        )
        iris = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/IrisRenderCompatibility.java").read_text(
            encoding="utf-8"
        )
        mods = (ROOT / "neoforge/src/main/resources/META-INF/neoforge.mods.toml").read_text(
            encoding="utf-8"
        )
        config = (ROOT / "neoforge/src/main/resources/realscale.geometryclipmap.mixins.json").read_text(
            encoding="utf-8"
        )
        self.assertIn("RenderType.solid()", native)
        self.assertIn("RenderType.cutout()", native)
        self.assertIn("RenderType.translucent()", native)
        self.assertIn("IrisRenderCompatibility.terrainVertexFormat()", native)
        self.assertIn("IrisVertexFormats", iris)
        self.assertIn('formats.getField("TERRAIN")', iris)
        self.assertIn("LightTexture.FULL_SKY", native)
        self.assertIn("shader.CHUNK_OFFSET", native)
        self.assertIn("chunkOffset.upload()", native)
        self.assertIn("target.draw()", native)
        self.assertIn("shadowPass", native)
        self.assertIn("ClipmapRingMesh.surfaceIndices(cells, holeRadius)", native)
        self.assertIn("morphedHeight(", native)
        self.assertNotIn("glUseProgram", native)
        self.assertNotIn("glBindFramebuffer", native)
        # Projection and fog are frame-scoped, not changed around an individual VBO draw.
        self.assertNotIn("ClipmapProjectionMath.extendFarPlane", native)
        self.assertNotIn("overrideGbufferProjection", native)
        self.assertIn("ClipmapProjectionMath.extendFarPlane", client)
        self.assertIn("overrideGbufferProjection(extended)", client)
        self.assertIn("Stage.AFTER_LEVEL", client)
        self.assertIn("activeIrisProjectionScope", client)
        self.assertIn("prepareIrisFrameFog", client)
        self.assertIn("RenderSystem.setShaderFogStart", client)
        self.assertIn("RenderSystem.setShaderFogEnd", client)
        self.assertIn("FogType.NONE", client)
        self.assertIn("loadIrisClass", iris)
        self.assertIn('ModList.get().isLoaded("iris")', iris)
        self.assertIn("Mode.API_UNAVAILABLE", iris)
        self.assertIn("CapturedRenderingState", iris)
        self.assertIn('method = "renderSectionLayer"', mixin)
        self.assertIn('@At("HEAD")', mixin)
        self.assertNotIn('target = "Lnet/minecraft/client/renderer/ShaderInstance;clear()V"', mixin)
        self.assertIn("GeometryClipmapClient.prepareIrisFrameFog()", mixin)
        self.assertNotIn("GeometryClipmapClient.renderIrisNativeLayer", mixin)
        self.assertIn("RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS", client)
        self.assertIn("RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS", client)
        self.assertIn("RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS", client)
        self.assertIn("renderIrisNativeLayer(RenderType", client)
        self.assertIn("IrisNativeClipmapRenderer.renderLayer", client)
        self.assertIn("allowsIrisNativeRenderer()", iris)
        self.assertIn('config="realscale.geometryclipmap.mixins.json"', mods)
        self.assertIn('"IrisGeometryClipmapMixin"', config)
        self.assertIn("ClipmapSurfaceNormals.compute(", native)
        self.assertIn("Blocks.WHITE_CONCRETE", native)
        self.assertIn("sprites.neutral()", native)
        self.assertIn("sprites.neutral()", native)
        self.assertNotIn("computeTerrainUvs", native)
        self.assertEqual(2, native.count("ClipmapTriangleUv.computeNeutralCellAlignedInto("))
        self.assertNotIn("ClipmapTriangleUv.computeWorldAlignedInto(", native)
        self.assertNotIn("ClipmapTriangleUv.computeCellAlignedInto(", native)
        self.assertNotIn("MAX_TEXTURED_DETAIL_LEVEL", native)
        self.assertNotIn("sprites.forMaterial(", native)
        self.assertIn("ClipmapSurfacePalette.terrainColorAt(", native)
        self.assertNotIn("ClipmapSurfacePalette.materialAt(", native)
        self.assertNotIn("worldHalfExtent", native)
        self.assertNotIn("ClipmapTriangleUv.computeInto(", native)
        uv = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/ClipmapTriangleUv.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("UV_MIN = 2.0f", uv)
        self.assertIn("UV_MAX = 14.0f", uv)
        self.assertIn("float uSpan = maxUAxis - minUAxis", uv)
        self.assertIn("float vSpan = maxVAxis - minVAxis", uv)
        self.assertIn("triangle UV projection is degenerate", uv)
        self.assertIn("Math.max(0, Math.min(cells, x))", native)
        self.assertNotIn("int minX = Math.min(gridX", native)
        self.assertIn("vertexColors[vertex]", native)
        self.assertNotIn("int materialRgb", native)
        terrain_triangle = native.split("private static void emitTriangle(BufferBuilder", 1)[1].split(
            "private static void triangleNormalInto", 1
        )[0]
        self.assertNotIn("8.0f, 8.0f, nx", terrain_triangle)

    def test_iris_native_terrain_uses_procedural_vertex_palette_without_atlas_materials(self):
        native = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/IrisNativeClipmapRenderer.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("sprites.neutral()", native)
        self.assertNotIn("computeTerrainUvs", native)
        self.assertIn("ClipmapTriangleUv.computeNeutralCellAlignedInto(", native)
        self.assertNotIn("ClipmapTriangleUv.computeWorldAlignedInto(", native)
        self.assertNotIn("ClipmapTriangleUv.computeCellAlignedInto(", native)
        self.assertNotIn("triangleUvs[0] = 8.0f", native)
        self.assertNotIn("MAX_TEXTURED_DETAIL_LEVEL", native)
        self.assertNotIn("sprites.forMaterial(", native)
        self.assertNotIn("ClipmapSurfacePalette.materialAt(", native)
        self.assertIn("ClipmapSurfacePalette.terrainColorAt(", native)
        self.assertIn("sprite.getU(u), sprite.getV(v)", native)  # terrain and proxy geometry
        uv = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/ClipmapTriangleUv.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("NEUTRAL_UV_MIN = 7.4375f", uv)
        self.assertIn("NEUTRAL_UV_RANGE = 0.125f", uv)
        self.assertIn("computeNeutralCellAlignedInto", uv)
        self.assertIn("computeCellAlignedPatchInto", uv)
        self.assertIn("ClipmapSurfaceNormals.compute(", native)
        self.assertIn("Math.min(8, 2 + level)", native)
        self.assertIn("normalRadiusCells", native)
        self.assertIn("float[] smoothNormals", native)
        self.assertNotIn("ClipmapTriangleUv.computeInto(", native)
        self.assertIn("triangleUvs[vertex * 2]", native)
        palette = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/ClipmapSurfacePalette.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("rockCover", palette)
        self.assertIn("beach", palette)
        self.assertIn("snowCover", palette)
        self.assertNotIn("int materialRgb", native)
        self.assertIn("one-pixel neutral patch", native)
        self.assertNotIn("int minX = Math.min(gridX", native)
        self.assertNotIn("Math.floorMod(x, side)", native)
        self.assertEqual(5, native.count("VertexFormat.Mode.TRIANGLES, irisTerrainFormat"))
        self.assertIn("shader.setDefaultUniforms(VertexFormat.Mode.TRIANGLES", native)
        self.assertNotIn("VertexFormat.Mode.QUADS, irisTerrainFormat", native)
        triangle = native.split("private static void emitTriangle(BufferBuilder", 1)[1].split(
            "private static void triangleNormalInto", 1
        )[0]
        self.assertIn("Iris's per-vertex normals intact", triangle)
        self.assertNotIn("repeated final", triangle)
        self.assertIn("putVertex(builder, sprite, rgb, 255, a[0]", native)
        self.assertIn("putVertex(builder, sprite, rgb, 255, d[0]", native)

    def test_toroidal_texture_update_plan_has_unit_coverage(self):
        planner = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/ClipmapTextureUpdatePlan.java").read_text(
            encoding="utf-8"
        )
        payload = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/network/ClipmapHeightTilePayload.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("dirtySamples * 10L >= fullSamples * 7L", planner)
        self.assertIn("Math.floorMod", planner)
        self.assertIn("appendFormerHoleExposures(", planner)
        renderer = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/GeometryClipmapRenderer.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("innerHalfCells[level]", renderer)
        self.assertIn("previous.sourceTile() == tile", renderer)
        self.assertIn("public void copyHeightsRegionTo(", payload)


    def test_iris_near_ring_transitions_from_voxel_steps_to_continuous_lods(self):
        native = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/IrisNativeClipmapRenderer.java").read_text(
            encoding="utf-8"
        )
        style = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/ClipmapVoxelStyle.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("ClipmapVoxelStyle.blend(", native)
        self.assertIn("ClipmapVoxelStyle.quantizeHeight(", native)
        self.assertIn("ClipmapVoxelStyle.blendHeight(", native)
        self.assertIn("ClipmapVoxelStyle.blendColor(", native)
        self.assertIn("appendVoxelWalls(", native)
        self.assertIn("emitVoxelWallX(", native)
        self.assertIn("emitVoxelWallZ(", native)
        self.assertIn("if (level != transitionLevel)", style)
        self.assertIn("FADE_LENGTH_CELLS = 40.0f", style)
        self.assertIn("MAX_VOXEL_CELL_SPACING_BLOCKS = 8", style)
        self.assertIn("transitionLevel(", style)
        self.assertIn("protectedStart", style)
        self.assertIn("outerRadius", style)
        self.assertIn("Math.floor(height + 0.5f)", style)


if __name__ == "__main__":
    unittest.main()

    def test_biome_materials_are_sampled_streamed_and_used_by_iris_renderer(self):
        server = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/GeometryClipmapServer.java").read_text(encoding="utf-8")
        payload = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/network/ClipmapHeightTilePayload.java").read_text(encoding="utf-8")
        renderer = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/client/clipmap/IrisNativeClipmapRenderer.java").read_text(encoding="utf-8")
        network = (ROOT / "neoforge/src/main/java/org/realscale/neoforge/network/GeometryClipmapNetwork.java").read_text(encoding="utf-8")
        self.assertIn("biomeSource.getNoiseBiome(", server)
        self.assertIn("ClipmapBiomeMaterial.fromBiomePath(path)", server)
        self.assertIn("byte[] biomeMaterials", payload)
        self.assertIn("buffer.writeBytes(materials)", payload)
        self.assertIn("tile.biomeMaterials()", renderer)
        self.assertIn("ClipmapBiomeMaterial.majority(", renderer)
        self.assertIn('event.registrar("6")', network)
