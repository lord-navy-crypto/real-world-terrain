package org.realscale.neoforge.client.clipmap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
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
 * <p>The experimental TEST JAR enables Geometry Clipmap automatically.
 * No launcher arguments are required. To disable it for troubleshooting,
 * use {@code -Drealscale.experimental.geometryclipmap=false}.
 */
@EventBusSubscriber(modid = "realscale", value = Dist.CLIENT)
public final class GeometryClipmapClient {
    // TEST-CANDIDATE DEFAULT: automatic ON. This lives only on the Draft
    // research branch, not in main or any public stable release.
    private static final boolean ENABLED =
            Boolean.parseBoolean(System.getProperty(
                    "realscale.experimental.geometryclipmap", "true"));
    private static final ClipmapSnapshotAssembler<ClipmapHeightTilePayload> TILES =
            new ClipmapSnapshotAssembler<>(6);
    private static final ClipmapProxySnapshotBuffer<List<VisibleSurfaceProxy>> PROXY_BUFFER =
            new ClipmapProxySnapshotBuffer<>();
    // The diagnostic candidate logs snapshot and draw-submission timings
    // without requiring the user to edit launch arguments.
    private static final boolean DEBUG =
            Boolean.parseBoolean(System.getProperty(
                    "realscale.experimental.geometryclipmap.debug", "true"));
    private static ClientLevel lastLevel;
    private static int lastNearChunks = -1;
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
    // Iris's main G-buffer projection must match the projection used to write
    // every depth value for the whole frame, not just the clipmap draw call.
    private static IrisRenderCompatibility.ProjectionScope activeIrisProjectionScope;
    private static Matrix4f activeIrisProjectionTarget;
    private static Matrix4f activeIrisOriginalProjection;
    private static final long HEARTBEAT_INTERVAL_NANOS = 8_000_000_000L;

    private GeometryClipmapClient() {}

    public static void accept(ClipmapHeightTilePayload payload) {
        if (!ENABLED) return;
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
            lastReportedTileCount = ClipmapStatusPayload.LEVEL_COUNT;
            if (!firstSnapshotRecorded) {
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
        if (!ENABLED) return;
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
        if (!ENABLED) return;
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
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        // Recover safely if a previous frame aborted before AFTER_LEVEL was posted.
        restoreIrisProjectionFrame();
        if (!ENABLED || minecraft.level == null || minecraft.player == null) {
            if (lastLevel != null || activeRequestId != 0L) clear();
            lastLevel = null;
            lastNearChunks = -1;
            return;
        }

        ClientLevel level = minecraft.level;
        int nearChunks = Math.max(2,
                Math.min(64, minecraft.options.getEffectiveRenderDistance()));
        if (level != lastLevel || nearChunks != lastNearChunks) {
            clear();
            lastLevel = level;
            lastNearChunks = nearChunks;
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
                    new ClipmapRequestPayload(nearChunks, activeRequestId));
        }

        long now = System.nanoTime();
        if (!firstSnapshotRecorded && !unsupportedWorld && activeRequestId > 0L
                && now - lastRequestSentNanos >= HEARTBEAT_INTERVAL_NANOS) {
            PacketDistributor.sendToServer(
                    new ClipmapRequestPayload(nearChunks, activeRequestId));
            lastRequestSentNanos = now;
            retryCount++;
            if (!waitingDiagnosticShown
                    && (lastServerStatusNanos == 0L
                        || now - lastServerStatusNanos > HEARTBEAT_INTERVAL_NANOS * 2L)) {
                waitingDiagnosticShown = true;
                reportStatus(minecraft, "[RealScale] Clipmap: NO SERVER ACKNOWLEDGEMENT - "
                        + "resending request #" + retryCount);
            } else if (!waitingDiagnosticShown && serverStateSinceNanos != 0L
                    && now - serverStateSinceNanos > HEARTBEAT_INTERVAL_NANOS * 2L) {
                waitingDiagnosticShown = true;
                String stalledState = switch (serverStatus) {
                    case ClipmapStatusPayload.REQUEST_ACCEPTED ->
                            "server acknowledged request but sampler has not started";
                    case ClipmapStatusPayload.QUEUED ->
                            "still queued; sampler queue depth " + serverQueueDepth;
                    case ClipmapStatusPayload.SAMPLING ->
                            "sampler has not advanced; last progress: " + serverDetail;
                    case ClipmapStatusPayload.SNAPSHOT_SENT ->
                            "server sent snapshot but client has " + TILES.pendingLevelCount() + "/6 tiles";
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
        // NeoForge posts AFTER_LEVEL only after LevelRenderer.renderLevel returns;
        // Iris has already completed its final G-buffer/composite work at this point.
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            restoreIrisProjectionFrame();
            return;
        }
        if (!ENABLED || event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
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
                    key.gridCells(), key.baseSpacing(), 6);
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

    private static void restoreIrisProjectionFrame() {
        Matrix4f target = activeIrisProjectionTarget;
        Matrix4f original = activeIrisOriginalProjection;
        IrisRenderCompatibility.ProjectionScope scope = activeIrisProjectionScope;
        activeIrisProjectionTarget = null;
        activeIrisOriginalProjection = null;
        activeIrisProjectionScope = null;
        try {
            if (target != null && original != null) target.set(original);
        } finally {
            if (scope != null) scope.close();
        }
    }

    /**
     * Called by IrisGeometryClipmapMixin immediately before vanilla clears the
     * active terrain shader. At this point Iris has selected the matching
     * G-buffer phase and the current RenderType state is still active.
     */
    public static void renderIrisNativeLayer(RenderType renderType,
            double cameraX, double cameraY, double cameraZ,
            Matrix4f frustumMatrix, Matrix4f projectionMatrix) {
        if (!ENABLED || lastNearChunks < 2) return;
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

        List<VisibleSurfaceProxy> proxies = PROXY_BUFFER.activeFor(key);
        boolean submitted = IrisNativeClipmapRenderer.renderLayer(renderType,
                TILES.activeTiles(),
                minecraft.level.dimension().location().toString(),
                key.requestId(), key.centerX(), key.centerZ(), lastNearChunks,
                proxies == null ? List.of() : proxies, minecraft.level,
                cameraX, cameraY, cameraZ, frustumMatrix, projectionMatrix,
                iris.shadowPass());
        if (submitted && !firstIrisRenderSubmitted) {
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
