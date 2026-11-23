package org.realscale.neoforge.client.clipmap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.realscale.neoforge.network.ClipmapHeightTilePayload;
import org.realscale.neoforge.network.ClipmapRequestPayload;
import org.realscale.neoforge.network.ClipmapSurfaceProxyPayload;
import java.util.List;


/**
 * Opt-in client entry point for the Geometry Clipmap renderer.
 *
 * <p>Enable with JVM argument:
 * {@code -Drealscale.experimental.geometryclipmap=true}
 */
@EventBusSubscriber(modid = "realscale", value = Dist.CLIENT)
public final class GeometryClipmapClient {
    private static final boolean ENABLED =
            Boolean.getBoolean("realscale.experimental.geometryclipmap");
    private static final ClipmapSnapshotAssembler<ClipmapHeightTilePayload> TILES =
            new ClipmapSnapshotAssembler<>(6);
    private static List<VisibleSurfaceProxy> PROXIES = List.of();
    private static ClientLevel lastLevel;
    private static int lastNearChunks = -1;
    private static long proxyCenterX = Long.MIN_VALUE;
    private static long proxyCenterZ = Long.MIN_VALUE;
    private static long requestSequence;
    private static long activeRequestId;

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
            if (key != null
                    && (proxyCenterX != key.centerX()
                        || proxyCenterZ != key.centerZ())) {
                PROXIES = List.of();
            }
        }
    }

    public static void accept(ClipmapSurfaceProxyPayload payload) {
        if (!ENABLED) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || activeRequestId <= 0L
                || payload.requestId() != activeRequestId
                || !minecraft.level.dimension().location().toString()
                        .equals(payload.dimensionKey())) return;
        var key = TILES.activeKey();
        if (key == null || key.requestId() != activeRequestId
                || key.centerX() != payload.centerX()
                || key.centerZ() != payload.centerZ()) {
            return;
        }
        PROXIES = payload.proxies();
        proxyCenterX = payload.centerX();
        proxyCenterZ = payload.centerZ();
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
            PacketDistributor.sendToServer(
                    new ClipmapRequestPayload(nearChunks, activeRequestId));
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
        GeometryClipmapRenderer.render(event, TILES.activeTiles(),
                minecraft.level.dimension().location().toString(), PROXIES);
    }

    private static long nextRequestId() {
        if (requestSequence == Long.MAX_VALUE) requestSequence = 0L;
        return ++requestSequence;
    }

    private static void clear() {
        activeRequestId = 0L;
        TILES.clear();
        PROXIES = List.of();
        proxyCenterX = Long.MIN_VALUE;
        proxyCenterZ = Long.MIN_VALUE;
        if (Minecraft.getInstance().isSameThread()) {
            GeometryClipmapRenderer.dispose();
        }
    }
}
