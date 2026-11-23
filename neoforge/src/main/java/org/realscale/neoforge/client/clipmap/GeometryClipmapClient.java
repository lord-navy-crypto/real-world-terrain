package org.realscale.neoforge.client.clipmap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
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
    private static long lastRequestSentNanos;
    private static long lastServerStatusNanos;
    private static int serverStatus = -1;
    private static int serverCompletedLevels;
    private static int serverQueueDepth;
    private static String serverDetail = "";
    private static int lastReportedTileCount = -1;
    private static int retryCount;
    private static boolean unsupportedWorld;
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
                reportStatus(minecraft, "[RealScale] Clipmap: RECEIVING HEIGHT TILES ("
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
        lastServerStatusNanos = System.nanoTime();
        serverStatus = payload.state();
        serverCompletedLevels = payload.completedLevels();
        serverQueueDepth = payload.queueDepth();
        serverDetail = payload.detail();
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
                    reportStatus(minecraft, "[RealScale] Clipmap: SAMPLING "
                            + payload.completedLevels() + "/6");
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
            if (lastServerStatusNanos == 0L
                    || now - lastServerStatusNanos > HEARTBEAT_INTERVAL_NANOS * 2L) {
                reportStatus(minecraft, "[RealScale] Clipmap: NO RECENT SERVER RESPONSE - "
                        + "resending request #" + retryCount);
            } else if (serverStatus == ClipmapStatusPayload.SNAPSHOT_SENT
                    && TILES.pendingLevelCount() < ClipmapStatusPayload.LEVEL_COUNT) {
                reportStatus(minecraft, "[RealScale] Clipmap: SERVER SENT SNAPSHOT; client has "
                        + TILES.pendingLevelCount() + "/6 pending levels; requesting resend");
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!ENABLED || event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || TILES.activeKey() == null) return;
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
        GeometryClipmapRenderer.render(event, TILES.activeTiles(),
                minecraft.level.dimension().location().toString(),
                proxies == null ? List.of() : proxies);
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

    private static void reportStatus(Minecraft minecraft, String message) {
        // Client-only chat notice: visible without the launcher console, and
        // only once for each status transition (no per-frame chat spam).
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
        lastRequestSentNanos = 0L;
        lastServerStatusNanos = 0L;
        serverStatus = -1;
        serverCompletedLevels = 0;
        serverQueueDepth = 0;
        serverDetail = "";
        lastReportedTileCount = -1;
        retryCount = 0;
        unsupportedWorld = false;
        if (Minecraft.getInstance().isSameThread()) {
            GeometryClipmapRenderer.dispose();
        }
    }
}
