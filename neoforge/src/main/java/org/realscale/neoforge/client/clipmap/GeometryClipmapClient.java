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

import java.util.HashMap;
import java.util.Map;

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
    private static final Map<Integer, ClipmapHeightTilePayload> TILES = new HashMap<>();
    private static List<VisibleSurfaceProxy> PROXIES = List.of();
    private static ClientLevel lastLevel;
    private static int lastNearChunks = -1;

    private GeometryClipmapClient() {}

    public static void accept(ClipmapHeightTilePayload payload) {
        if (!ENABLED) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || !minecraft.level.dimension().location().toString()
                        .equals(payload.dimensionKey())) {
            return;
        }
        TILES.put(payload.levelIndex(), payload);
    }

    public static void accept(ClipmapSurfaceProxyPayload payload) {
        if (!ENABLED) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || !minecraft.level.dimension().location().toString()
                        .equals(payload.dimensionKey())) return;
        PROXIES = payload.proxies();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ENABLED || minecraft.level == null || minecraft.player == null) {
            if (lastLevel != null) clear();
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
            PacketDistributor.sendToServer(new ClipmapRequestPayload(nearChunks));
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!ENABLED || event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || TILES.size() < 6) return;
        GeometryClipmapRenderer.render(event, TILES,
                minecraft.level.dimension().location().toString(), PROXIES);
    }

    private static void clear() {
        TILES.clear();
        PROXIES = List.of();
        if (Minecraft.getInstance().isSameThread()) {
            GeometryClipmapRenderer.dispose();
        }
    }
}
