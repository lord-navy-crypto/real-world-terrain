package org.realscale.neoforge.client.clipmap;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.material.FogType;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.realscale.neoforge.network.ClipmapHeightTilePayload;
import org.realscale.neoforge.network.ClipmapRequestPayload;
import org.realscale.neoforge.network.ClipmapSurfaceProxyPayload;
import org.realscale.neoforge.network.ClipmapStatusPayload;
import java.util.List;


/**
 * Diagnostic test-build client entry point for the Geometry Clipmap renderer.
 *
 * <p>Geometry Clipmap is disabled by default and can be enabled at runtime
 * from the in-game pause menu. The preference persists in the config folder.
 */
@EventBusSubscriber(modid = "realscale", value = Dist.CLIENT)
public final class GeometryClipmapClient {
    private static final ClipmapSnapshotAssembler<ClipmapHeightTilePayload> TILES =
            new ClipmapSnapshotAssembler<>(6);
    private static final ClipmapProxySnapshotBuffer<List<VisibleSurfaceProxy>> PROXY_BUFFER =
            new ClipmapProxySnapshotBuffer<>();
    // The diagnostic candidate logs snapshot and draw-submission timings
    // without requiring the user to edit launch arguments.
    private static final boolean DEBUG =
            Boolean.parseBoolean(System.getProperty(
                    "realscale.experimental.geometryclipmap.debug", "true"));
    // CI-only camera aiming for reproducible visual captures. Disabled for normal users.
    private static final boolean SMOKE_CAMERA =
            Boolean.parseBoolean(System.getProperty(
                    "realscale.experimental.geometryclipmap.smokeCamera", "false"));
    private static ClientLevel lastLevel;
    private static int lastNearChunks = -1;
    private static int lastGridCells = -1;
    private static long requestSequence;
    private static long activeRequestId;
    private static long requestStartedNanos;
    private static boolean firstSnapshotRecorded;
    private static boolean firstRenderSubmitted;
    private static boolean firstIrisRenderSubmitted;
    private static long lastRequestSentNanos;
    private static long lastServerStatusNanos;
    private static long serverStateSinceNanos;
    private static boolean waitingDiagnosticShown;
    private static int serverStatus = -1;
    private static int serverCompletedLevels;
    private static int serverQueueDepth;
    private static String serverDetail = "";
    private static int lastReportedTileCount = -1;
    private static int retryCount;
    private static boolean unsupportedWorld;
    private static boolean irisCompatibilityNoticeShown;
    private static boolean cameraVantageReported;
    // Iris's main G-buffer projection must match the projection used to write
    // every depth value for the whole frame, not just the clipmap draw call.
    private static IrisRenderCompatibility.ProjectionScope activeIrisProjectionScope;
    private static Matrix4f activeIrisProjectionTarget;
    private static Matrix4f activeIrisOriginalProjection;
    private static boolean activeIrisFogCaptured;
    private static boolean irisFogHookReported;
    private static boolean irisFogRangeReported;
    private static boolean irisFogSkipReported;
    private static float activeIrisOriginalFogStart;
    private static float activeIrisOriginalFogEnd;
    private static float activeIrisExtendedFogStart;
    private static float activeIrisExtendedFogEnd;
    private static final long HEARTBEAT_INTERVAL_NANOS = 8_000_000_000L;

    private static final boolean EXTEND_IRIS_FAR = Boolean.parseBoolean(
            System.getProperty("realscale.experimental.geometryclipmap.irisFarUniform", "true"));
    private static int lastReportedIrisFar = -1;

    /** Shader-pack distance only: never modifies the vanilla chunk-loading option. */
    public static int irisFarDistanceBlocks(int vanillaFar) {
        if (!GeometryClipmapSettings.isEnabled() || !EXTEND_IRIS_FAR || lastLevel == null) return vanillaFar;
        var key = TILES.activeKey();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != lastLevel || key == null || key.requestId() != activeRequestId
                || !IrisRenderCompatibility.hasNativeProjectionBridge()
                || !IrisRenderCompatibility.current().allowsIrisNativeRenderer()) {
            return vanillaFar;
        }
        // Keep shader-pack horizon stable across partial snapshot publication.
        long radius = (long) key.gridCells() / 2L * key.baseSpacing() * (1L << (ClipmapStatusPayload.LEVEL_COUNT - 1));
        int far = (int) Math.max(vanillaFar, Math.min(Integer.MAX_VALUE, radius));
        if (DEBUG && far != lastReportedIrisFar) {
            lastReportedIrisFar = far;
            System.out.printf(java.util.Locale.ROOT,
                    "[RealScale Iris Clipmap] IRIS_FAR_UNIFORM vanilla=%d clipmap=%d%n",
                    vanillaFar, far);
        }
        return far;
    }

    private GeometryClipmapClient() {}

    public static boolean isEnabled() {
        return GeometryClipmapSettings.isEnabled();
    }

    /** Human-readable actual far radius, after protecting the vanilla near field. */
    public static String rangeLabel() {
        Minecraft minecraft = Minecraft.getInstance();
        int nearChunks = Math.max(2,
                Math.min(64, minecraft.options.getEffectiveRenderDistance()));
        GeometryClipmapLayout.Layout layout =
                GeometryClipmapLayout.forRenderDistanceChunks(
                        nearChunks, GeometryClipmapSettings.gridCells()).compute(0.0, 0.0);
        long blocks = layout.maxAxisExtentBlocks();
        long kilometres = Math.round(blocks / 1024.0);
        return "Clipmap Range: " + kilometres + "k";
    }

    /** Cycle among safe effective grid sizes without changing vanilla view distance. */
    public static void cycleRange() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            minecraft.execute(GeometryClipmapClient::cycleRange);
            return;
        }
        int nearChunks = Math.max(2,
                Math.min(64, minecraft.options.getEffectiveRenderDistance()));
        int current = GeometryClipmapLayout.forRenderDistanceChunks(
                nearChunks, GeometryClipmapSettings.gridCells()).gridCells();
        int next = -1;
        for (int requested : new int[]{64, 128, 256}) {
            int effective = GeometryClipmapLayout.forRenderDistanceChunks(
                    nearChunks, requested).gridCells();
            if (effective > current) {
                next = effective;
                break;
            }
        }
        if (next < 0) {
            for (int requested : new int[]{64, 128, 256}) {
                int effective = GeometryClipmapLayout.forRenderDistanceChunks(
                        nearChunks, requested).gridCells();
                if (effective < current) {
                    next = effective;
                    break;
                }
            }
        }
        if (next < 0 || next == current) return;
        try {
            GeometryClipmapSettings.setGridCells(next);
        } catch (IllegalStateException failure) {
            reportStatus(minecraft,
                    "[RealScale] Clipmap: COULD NOT SAVE RANGE - " + failure.getMessage());
            return;
        }
        clear();
        lastLevel = null;
        lastNearChunks = -1;
        lastGridCells = -1;
        reportStatus(minecraft, "[RealScale] Clipmap: RANGE CHANGED - " + rangeLabel());
    }

    /** Toggle the client renderer and persist the choice immediately. */
    public static void toggleEnabled() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            minecraft.execute(GeometryClipmapClient::toggleEnabled);
            return;
        }

        boolean next = !GeometryClipmapSettings.isEnabled();
        try {
            GeometryClipmapSettings.setEnabled(next);
        } catch (IllegalStateException failure) {
            reportStatus(minecraft,
                    "[RealScale] Clipmap: COULD NOT SAVE THE ON/OFF SETTING - "
                            + failure.getMessage());
            return;
        }

        // Clear the active snapshot and GPU resources on disable. On re-enable,
        // the next client tick requests a fresh six-LOD set.
        clear();
        lastLevel = null;
        lastNearChunks = -1;
        lastGridCells = -1;
        cameraVantageReported = false;
        reportStatus(minecraft, next
                ? "[RealScale] Clipmap: ON - automatic terrain rendering will initialize"
                : "[RealScale] Clipmap: OFF - automatic terrain rendering stopped");
    }

    public static void accept(ClipmapHeightTilePayload payload) {
        if (!GeometryClipmapSettings.isEnabled()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || activeRequestId <= 0L
                || payload.requestId() != activeRequestId
                || !minecraft.level.dimension().location().toString()
                        .equals(payload.dimensionKey())) {
            return;
        }
        if (TILES.accept(payload.requestId(), payload.levelIndex(), payload.gridCells(),
                payload.cellSpacingBlocks(), payload.originX(), payload.originZ(),
                payload.nearRenderDistanceChunks(), payload.dimensionKey(), payload)) {
            var key = TILES.activeKey();
            PROXY_BUFFER.onHeightSnapshot(key);
            int received = TILES.activeTiles().size();
            lastReportedTileCount = received;
            serverStateSinceNanos = System.nanoTime();
            waitingDiagnosticShown = false;
            if (received < ClipmapStatusPayload.LEVEL_COUNT) {
                reportProgress(minecraft, "[RealScale] Clipmap: RENDERABLE LOD PREFIX (" + received + "/6)");
            }
            if (DEBUG) System.out.printf(java.util.Locale.ROOT,
                    "[RealScale Geometry Clipmap] CLIENT_LOD_ACTIVE levels=%d center=(%d,%d)%n",
                    received, key.centerX(), key.centerZ());
            if (!firstSnapshotRecorded && received == ClipmapStatusPayload.LEVEL_COUNT) {
                firstSnapshotRecorded = true;
                reportStatus(minecraft,
                        "[RealScale] Clipmap: SNAPSHOT READY (6/6 height levels)");
                if (DEBUG && requestStartedNanos != 0L) {
                    System.out.printf(java.util.Locale.ROOT,
                            "[RealScale Geometry Clipmap] firstSnapshotReady=%.1fms " +
                                    "dimension=%s center=(%d,%d)%n",
                            (System.nanoTime() - requestStartedNanos) / 1_000_000.0,
                            key.dimension(), key.centerX(), key.centerZ());
                }
            }
        } else {
            int received = TILES.pendingLevelCount();
            if (received != lastReportedTileCount && received > 0) {
                lastReportedTileCount = received;
                waitingDiagnosticShown = false;
                reportProgress(minecraft, "[RealScale] Clipmap: RECEIVING HEIGHT TILES ("
                        + received + "/6)");
            }
        }
    }

    public static void accept(ClipmapStatusPayload payload) {
        if (!GeometryClipmapSettings.isEnabled()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || activeRequestId <= 0L
                || payload.requestId() != activeRequestId
                || !minecraft.level.dimension().location().toString()
                        .equals(payload.dimensionKey())) return;
        long now = System.nanoTime();
        boolean stateChanged = payload.state() != serverStatus
                || payload.completedLevels() != serverCompletedLevels
                || !payload.detail().equals(serverDetail);
        lastServerStatusNanos = now;
        if (stateChanged) {
            serverStateSinceNanos = now;
            waitingDiagnosticShown = false;
        }
        serverStatus = payload.state();
        serverCompletedLevels = payload.completedLevels();
        serverQueueDepth = payload.queueDepth();
        serverDetail = payload.detail();
        if (!stateChanged) return;
        switch (payload.state()) {
            case ClipmapStatusPayload.REQUEST_ACCEPTED ->
                    reportStatus(minecraft, "[RealScale] Clipmap: SERVER ACKNOWLEDGED REQUEST");
            case ClipmapStatusPayload.UNSUPPORTED_WORLD -> {
                unsupportedWorld = true;
                reportStatus(minecraft, "[RealScale] Clipmap: UNSUPPORTED WORLD - "
                        + payload.detail());
            }
            case ClipmapStatusPayload.QUEUED ->
                    reportStatus(minecraft, "[RealScale] Clipmap: QUEUED - " + payload.detail());
            case ClipmapStatusPayload.SAMPLING ->
                    reportProgress(minecraft, "[RealScale] Clipmap: " + payload.detail());
            case ClipmapStatusPayload.SNAPSHOT_SENT ->
                    reportStatus(minecraft, "[RealScale] Clipmap: SERVER SENT 6/6 TILES");
            case ClipmapStatusPayload.BUSY ->
                    reportStatus(minecraft, "[RealScale] Clipmap: SERVER BUSY - " + payload.detail());
            case ClipmapStatusPayload.FAILED ->
                    reportStatus(minecraft, "[RealScale] Clipmap: SAMPLING FAILED - "
                            + payload.detail());
            case ClipmapStatusPayload.STALLED ->
                    reportStatus(minecraft, "[RealScale] Clipmap: SAMPLER STALLED - "
                            + payload.detail());
            default -> { }
        }
    }

    public static void accept(ClipmapSurfaceProxyPayload payload) {
        if (!GeometryClipmapSettings.isEnabled()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || activeRequestId <= 0L
                || payload.requestId() != activeRequestId
                || !minecraft.level.dimension().location().toString()
                        .equals(payload.dimensionKey())) return;
        // Staging one unmatched snapshot prevents losing proxies if a valid
        // proxy packet is processed before the last height tile arrives.
        PROXY_BUFFER.accept(payload.requestId(), payload.dimensionKey(),
                payload.centerX(), payload.centerZ(), payload.proxies(),
                TILES.activeKey());
        if (DEBUG) System.out.printf(java.util.Locale.ROOT,
                "[RealScale Geometry Clipmap] CLIENT_PROXY_RECEIVED center=(%d,%d) count=%d%n",
                payload.centerX(), payload.centerZ(), payload.proxies().size());
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        // Recover safely if a previous frame aborted before AFTER_LEVEL was posted.
        restoreIrisProjectionFrame();
        if (!GeometryClipmapSettings.isEnabled() || minecraft.level == null || minecraft.player == null) {
            if (lastLevel != null || activeRequestId != 0L) clear();
            lastLevel = null;
            lastNearChunks = -1;
            lastGridCells = -1;
            return;
        }

        ClientLevel level = minecraft.level;
        int nearChunks = Math.max(2,
                Math.min(64, minecraft.options.getEffectiveRenderDistance()));
        int gridCells = GeometryClipmapSettings.gridCells();
        if (level != lastLevel || nearChunks != lastNearChunks
                || gridCells != lastGridCells) {
            clear();
            lastLevel = level;
            lastNearChunks = nearChunks;
            lastGridCells = gridCells;
            activeRequestId = nextRequestId();
            requestStartedNanos = System.nanoTime();
            firstSnapshotRecorded = false;
            firstRenderSubmitted = false;
            lastRequestSentNanos = requestStartedNanos;
            lastServerStatusNanos = 0L;
            serverStateSinceNanos = requestStartedNanos;
            waitingDiagnosticShown = false;
            serverStatus = -1;
            serverCompletedLevels = 0;
            serverQueueDepth = 0;
            serverDetail = "";
            lastReportedTileCount = 0;
            retryCount = 0;
            unsupportedWorld = false;
            reportStatus(minecraft,
                    "[RealScale] Clipmap: REQUEST SENT - awaiting server acknowledgement");
            PacketDistributor.sendToServer(
                    new ClipmapRequestPayload(nearChunks, gridCells, activeRequestId));
        }

        if (TILES.promotePendingForCamera(minecraft.player.getX(), minecraft.player.getZ())) {
            PROXY_BUFFER.onHeightSnapshot(TILES.activeKey());
        }
        long now = System.nanoTime();
        if (!firstSnapshotRecorded && !unsupportedWorld && activeRequestId > 0L
                && now - lastRequestSentNanos >= HEARTBEAT_INTERVAL_NANOS) {
            PacketDistributor.sendToServer(
                    new ClipmapRequestPayload(nearChunks, gridCells, activeRequestId));
            lastRequestSentNanos = now;
            retryCount++;
            if (!waitingDiagnosticShown
                    && (lastServerStatusNanos == 0L
                        || now - lastServerStatusNanos > HEARTBEAT_INTERVAL_NANOS * 2L)) {
                waitingDiagnosticShown = true;
                reportStatus(minecraft, "[RealScale] Clipmap: NO SERVER ACKNOWLEDGEMENT - "
                        + "resending request #" + retryCount);
            } else if (!waitingDiagnosticShown && serverStateSinceNanos != 0L
                    && now - serverStateSinceNanos > 90_000_000_000L) {
                waitingDiagnosticShown = true;
                String stalledState = switch (serverStatus) {
                    case ClipmapStatusPayload.REQUEST_ACCEPTED ->
                            "server acknowledged request but sampler has not started";
                    case ClipmapStatusPayload.QUEUED ->
                            "still queued; sampler queue depth " + serverQueueDepth;
                    case ClipmapStatusPayload.SAMPLING ->
                            "sampler has not advanced; last progress: " + serverDetail;
                    case ClipmapStatusPayload.SNAPSHOT_SENT ->
                            "server sent snapshot but client has " + Math.max(TILES.activeTiles().size(), TILES.pendingLevelCount()) + "/6 tiles";
                    case ClipmapStatusPayload.BUSY ->
                            "server is busy: " + serverDetail;
                    case ClipmapStatusPayload.FAILED ->
                            "server sampling failed: " + serverDetail;
                    case ClipmapStatusPayload.STALLED ->
                            "sampler watchdog fired: " + serverDetail;
                    default -> "server state is unknown";
                };
                reportStatus(minecraft, "[RealScale] Clipmap: NO PROGRESS - " + stalledState);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // The CI harness verifies camera height from this render-thread telemetry,
        // not OCR of the debug overlay. Emit it once after the spectator reaches
        // the high-altitude vantage so the screenshot is taken from a known view.
        if (DEBUG && !cameraVantageReported
                && event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            var cameraPosition = event.getCamera().getPosition();
            double cameraVantageThreshold = SMOKE_CAMERA ? 2300.0 : 1100.0;
            if (cameraPosition.y >= cameraVantageThreshold) {
                cameraVantageReported = true;
                Minecraft minecraft = Minecraft.getInstance();
                if (SMOKE_CAMERA && minecraft.player != null) {
                    // Face down toward the generated surface. This is strictly
                    // opt-in for the automated screenshot harness, never normal gameplay.
                    minecraft.player.setYRot(45.0f);
                    minecraft.player.setXRot(35.0f);
                    minecraft.player.setYHeadRot(45.0f);
                }
                float pitch = minecraft.player == null ? 0.0f : minecraft.player.getXRot();
                float yaw = minecraft.player == null ? 0.0f : minecraft.player.getYRot();
                System.out.printf(java.util.Locale.ROOT,
                        "[RealScale Geometry Clipmap] CLIENT_CAMERA_VANTAGE_READY: "
                                + "x=%.1f y=%.1f z=%.1f pitch=%.1f yaw=%.1f%n",
                        cameraPosition.x, cameraPosition.y, cameraPosition.z, pitch, yaw);
            }
        }

        // AFTER_LEVEL runs after Iris has completed its final G-buffer/composite work.
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            restoreIrisProjectionFrame();
            return;
        }

        // Vanilla NeoForge and Sodium's PlatformLevelRenderHooks both dispatch
        // these events after their matching terrain layer. This avoids relying
        // on a vanilla-only ShaderInstance.clear bytecode instruction that
        // Sodium replaces when it overwrites LevelRenderer.renderSectionLayer.
        RenderType irisLayer = null;
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) {
            irisLayer = RenderType.solid();
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
            irisLayer = RenderType.cutout();
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            irisLayer = RenderType.translucent();
        }
        if (irisLayer != null) {
            var camera = event.getCamera().getPosition();
            renderIrisNativeLayer(irisLayer, camera.x, camera.y, camera.z,
                    event.getModelViewMatrix(), event.getProjectionMatrix());
            return;
        }

        if (!GeometryClipmapSettings.isEnabled() || event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || TILES.activeKey() == null) return;

        // The standalone OpenGL program remains the no-shader fallback. With
        // an active Iris pack, extend the shared frame projection before vanilla
        // terrain starts so vanilla geometry, clipmap geometry, and G-buffer
        // depth reconstruction all use the same projection.
        IrisRenderCompatibility.State iris = IrisRenderCompatibility.current();
        if (iris.shadowPass()) return;
        if (iris.allowsIrisNativeRenderer()
                && IrisRenderCompatibility.hasNativeProjectionBridge()) {
            if (!beginIrisProjectionFrame(event)) {
                if (!irisCompatibilityNoticeShown) {
                    irisCompatibilityNoticeShown = true;
                    reportStatus(minecraft,
                            "[RealScale] Clipmap: FAILED TO EXTEND SHARED IRIS/G-BUFFER PROJECTION - native draw paused safely");
                }
                return;
            }
            if (!irisCompatibilityNoticeShown) {
                irisCompatibilityNoticeShown = true;
                reportStatus(minecraft,
                        "[RealScale] Clipmap: IRIS NATIVE TERRAIN PATH ACTIVE - shared frame projection extended; waiting for solid/cutout/water passes");
            }
            return;
        }
        if (iris.allowsIrisNativeRenderer()
                && !IrisRenderCompatibility.hasNativeProjectionBridge()) {
            if (!irisCompatibilityNoticeShown) {
                irisCompatibilityNoticeShown = true;
                reportStatus(minecraft,
                        "[RealScale] Clipmap: this Iris build lacks the required G-buffer projection bridge; native draw paused safely");
            }
            return;
        }
        if (!iris.allowsRawOpenGlRenderer()) {
            if (!irisCompatibilityNoticeShown) {
                irisCompatibilityNoticeShown = true;
                reportStatus(minecraft,
                        "[RealScale] Clipmap: IRIS API UNAVAILABLE - custom draw paused safely");
            }
            return;
        }
        irisCompatibilityNoticeShown = false;
        cameraVantageReported = false;

        var key = TILES.activeKey();
        if (key == null || key.requestId() != activeRequestId) return;
        // Do not show the old region's terrain after a teleport while
        // the server is preparing the next sparse surface snapshot.
        double cameraX = event.getCamera().getPosition().x;
        double cameraZ = event.getCamera().getPosition().z;
        double safeX = key.gridCells() * (double) key.baseSpacing() / 2.0;
        if (Math.abs(cameraX - key.centerX()) > safeX
                || Math.abs(cameraZ - key.centerZ()) > safeX) return;
        List<VisibleSurfaceProxy> proxies = PROXY_BUFFER.activeFor(key);
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        ClipmapEnvironment.State environment = ClipmapEnvironment.from(minecraft.level, partialTick);
        boolean drawSubmitted = GeometryClipmapRenderer.render(event, TILES.activeTiles(),
                minecraft.level.dimension().location().toString(),
                proxies == null ? List.of() : proxies, environment);
        if (!drawSubmitted) return;
        if (!firstRenderSubmitted) {
            firstRenderSubmitted = true;
            reportStatus(minecraft,
                    "[RealScale] Clipmap: GPU DRAW SUBMITTED (visual visibility not yet verified)");
            if (DEBUG && requestStartedNanos != 0L) {
                System.out.printf(java.util.Locale.ROOT,
                        "[RealScale Geometry Clipmap] firstEligibleRenderSubmission=%.1fms%n",
                        (System.nanoTime() - requestStartedNanos) / 1_000_000.0);
            }
        }
    }

    /**
     * Extend the exact projection object shared by the remainder of the main
     * LevelRenderer frame, and keep Iris's captured G-buffer matrix in sync
     * until AFTER_LEVEL, after Iris has completed depth-dependent composites.
     */
    private static boolean beginIrisProjectionFrame(RenderLevelStageEvent event) {
        restoreIrisProjectionFrame();
        var key = TILES.activeKey();
        Matrix4f target = event.getProjectionMatrix();
        if (key == null || target == null) return false;

        Matrix4f original = new Matrix4f(target);
        IrisRenderCompatibility.ProjectionScope scope = null;
        try {
            ClipmapFogRange fogRange = ClipmapFogRange.forGrid(
                    key.gridCells(), key.baseSpacing(), ClipmapStatusPayload.LEVEL_COUNT);
            Matrix4f extended = new Matrix4f(target);
            ClipmapProjectionMath.extendFarPlane(
                    extended, fogRange.endBlocks() * 1.5f);
            scope = IrisRenderCompatibility.overrideGbufferProjection(extended);
            if (scope == null) return false;

            target.set(extended);
            activeIrisProjectionTarget = target;
            activeIrisOriginalProjection = original;
            activeIrisProjectionScope = scope;
            return true;
        } catch (RuntimeException | LinkageError failure) {
            if (scope != null) scope.close();
            target.set(original);
            System.err.println("[RealScale Iris Clipmap] frame projection extension failed safely: "
                    + failure.getClass().getSimpleName() + ": " + failure.getMessage());
            return false;
        }
    }

    /**
     * Called at renderSectionLayer HEAD after FogRenderer has installed the
     * main-camera fog parameters but before the terrain shader uploads them.
     * Keep the extended fog range for the whole G-buffer frame so later Iris
     * composites interpret distant depth and fog with the same horizon.
     */
    public static void prepareIrisFrameFog() {
        if (SMOKE_CAMERA && !irisFogHookReported) {
            irisFogHookReported = true;
            System.out.printf(java.util.Locale.ROOT,
                    "[RealScale Iris Clipmap] IRIS_FOG_PREPARE_HOOK_HIT scope=%s nearChunks=%d%n",
                    activeIrisProjectionScope != null, lastNearChunks);
        }
        if (!GeometryClipmapSettings.isEnabled() || activeIrisProjectionScope == null || lastNearChunks < 2) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || minecraft.gameRenderer.getMainCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }
        var key = TILES.activeKey();
        if (key == null || key.requestId() != activeRequestId) return;

        // Other terrain phases can reinstall vanilla fog during the same frame.
        // Reassert our captured range without replacing the original restoration state.
        if (activeIrisFogCaptured) {
            RenderSystem.setShaderFogStart(activeIrisExtendedFogStart);
            RenderSystem.setShaderFogEnd(activeIrisExtendedFogEnd);
            return;
        }
        float originalStart = RenderSystem.getShaderFogStart();
        float originalEnd = RenderSystem.getShaderFogEnd();
        float minimumNormalFogEnd = Math.max(16.0f, lastNearChunks * 16.0f * 0.55f);
        if (!Float.isFinite(originalStart) || !Float.isFinite(originalEnd)
                || originalEnd <= originalStart || originalEnd < minimumNormalFogEnd) {
            // Preserve short environmental fog (for example, dense biome fog).
            if (SMOKE_CAMERA && !irisFogSkipReported) {
                irisFogSkipReported = true;
                System.out.printf(java.util.Locale.ROOT,
                        "[RealScale Iris Clipmap] IRIS_FOG_RANGE_SKIPPED originalStart=%.1f originalEnd=%.1f minimumEnd=%.1f%n",
                        originalStart, originalEnd, minimumNormalFogEnd);
            }
            return;
        }

        ClipmapFogRange fogRange = ClipmapFogRange.forGrid(
                key.gridCells(), key.baseSpacing(), ClipmapStatusPayload.LEVEL_COUNT);
        float extendedStart = Math.max(originalStart, fogRange.startBlocks());
        float extendedEnd = Math.max(originalEnd, fogRange.endBlocks() * 1.5f);
        if (!Float.isFinite(extendedStart) || !Float.isFinite(extendedEnd)
                || extendedEnd <= extendedStart) {
            return;
        }

        activeIrisOriginalFogStart = originalStart;
        activeIrisOriginalFogEnd = originalEnd;
        activeIrisExtendedFogStart = extendedStart;
        activeIrisExtendedFogEnd = extendedEnd;
        activeIrisFogCaptured = true;
        RenderSystem.setShaderFogStart(extendedStart);
        RenderSystem.setShaderFogEnd(extendedEnd);
        if (SMOKE_CAMERA && !irisFogRangeReported) {
            irisFogRangeReported = true;
            System.out.printf(java.util.Locale.ROOT,
                    "[RealScale Iris Clipmap] IRIS_FOG_RANGE original=(%.1f,%.1f) extended=(%.1f,%.1f)%n",
                    originalStart, originalEnd, extendedStart, extendedEnd);
        }
    }

    private static void restoreIrisProjectionFrame() {
        Matrix4f target = activeIrisProjectionTarget;
        Matrix4f original = activeIrisOriginalProjection;
        IrisRenderCompatibility.ProjectionScope scope = activeIrisProjectionScope;
        boolean restoreFog = activeIrisFogCaptured;
        float originalFogStart = activeIrisOriginalFogStart;
        float originalFogEnd = activeIrisOriginalFogEnd;
        activeIrisProjectionTarget = null;
        activeIrisOriginalProjection = null;
        activeIrisProjectionScope = null;
        activeIrisFogCaptured = false;
        try {
            if (target != null && original != null) target.set(original);
        } finally {
            try {
                if (restoreFog) {
                    RenderSystem.setShaderFogStart(originalFogStart);
                    RenderSystem.setShaderFogEnd(originalFogEnd);
                }
            } finally {
                if (scope != null) scope.close();
            }
        }
    }

    /**
     * Called from the matching NeoForge terrain-stage event. With Sodium,
     * PlatformLevelRenderHooks dispatches this event before renderSectionLayer
     * returns, while Iris's matching terrain phase is still active.
     */
    public static void renderIrisNativeLayer(RenderType renderType,
            double cameraX, double cameraY, double cameraZ,
            Matrix4f frustumMatrix, Matrix4f projectionMatrix) {
        if (!GeometryClipmapSettings.isEnabled() || lastNearChunks < 2) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        IrisRenderCompatibility.State iris = IrisRenderCompatibility.current();
        if (!iris.allowsIrisNativeRenderer()
                || !IrisRenderCompatibility.hasNativeProjectionBridge()) return;
        var key = TILES.activeKey();
        if (key == null || key.requestId() != activeRequestId) return;

        if (!iris.shadowPass()) {
            // Never draw a stale snapshot after a teleport while the server
            // is preparing a new set of six height tiles.
            double safeHalfExtent = key.gridCells() * (double) key.baseSpacing() / 2.0;
            if (Math.abs(cameraX - key.centerX()) > safeHalfExtent
                    || Math.abs(cameraZ - key.centerZ()) > safeHalfExtent) return;
        }

        // Main-pass draw uniforms must use exactly the extended projection
        // installed in Iris's captured G-buffer state, not a stale stage copy.
        // Shadow rendering retains its own light-space projection.
        Matrix4f drawProjection = projectionMatrix;
        if (!iris.shadowPass()) {
            if (activeIrisProjectionScope == null || activeIrisProjectionTarget == null) return;
            drawProjection = activeIrisProjectionTarget;
            prepareIrisFrameFog();
        }

        List<VisibleSurfaceProxy> proxies = PROXY_BUFFER.activeFor(key);
        boolean submitted = IrisNativeClipmapRenderer.renderLayer(renderType,
                TILES.activeTiles(),
                minecraft.level.dimension().location().toString(),
                key.requestId(), key.centerX(), key.centerZ(), lastNearChunks,
                proxies == null ? List.of() : proxies, minecraft.level,
                cameraX, cameraY, cameraZ, frustumMatrix, drawProjection,
                iris.shadowPass());
        // The smoke-test success marker must represent the main G-buffer
        // terrain pass, not an earlier Iris shadow-map submission.
        if (submitted && !iris.shadowPass() && !firstIrisRenderSubmitted) {
            firstIrisRenderSubmitted = true;
            reportStatus(minecraft,
                    "[RealScale] Clipmap: IRIS NATIVE TERRAIN DRAW SUBMITTED (visual visibility not yet verified)");
            if (DEBUG && requestStartedNanos != 0L) {
                System.out.printf(java.util.Locale.ROOT,
                        "[RealScale Geometry Clipmap] firstIrisNativeDraw=%.1fms shadowPass=%s%n",
                        (System.nanoTime() - requestStartedNanos) / 1_000_000.0,
                        iris.shadowPass());
            }
        }
    }

    private static void reportProgress(Minecraft minecraft, String message) {
        // High-frequency sampling and packet progress belongs in the action bar,
        // not chat, so a slow first load cannot flood the user's chat history.
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal(message), true);
        }
        System.out.println("[RealScale Geometry Clipmap] " + message);
    }

    private static void reportStatus(Minecraft minecraft, String message) {
        // Major state transitions and actionable errors are shown in chat.
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal(message), false);
        }
        System.out.println("[RealScale Geometry Clipmap] " + message);
    }

    private static long nextRequestId() {
        if (requestSequence == Long.MAX_VALUE) requestSequence = 0L;
        return ++requestSequence;
    }

    private static void clear() {
        activeRequestId = 0L;
        lastReportedIrisFar = -1;
        TILES.clear();
        PROXY_BUFFER.clear();
        requestStartedNanos = 0L;
        firstSnapshotRecorded = false;
        firstRenderSubmitted = false;
        firstIrisRenderSubmitted = false;
        lastRequestSentNanos = 0L;
        lastServerStatusNanos = 0L;
        serverStateSinceNanos = 0L;
        waitingDiagnosticShown = false;
        serverStatus = -1;
        serverCompletedLevels = 0;
        serverQueueDepth = 0;
        serverDetail = "";
        lastReportedTileCount = -1;
        retryCount = 0;
        unsupportedWorld = false;
        irisCompatibilityNoticeShown = false;
        if (Minecraft.getInstance().isSameThread()) {
            restoreIrisProjectionFrame();
            GeometryClipmapRenderer.dispose();
            IrisNativeClipmapRenderer.dispose();
        }
    }
}
