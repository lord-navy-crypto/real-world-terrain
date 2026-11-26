package org.realscale.neoforge.network;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.realscale.neoforge.GeometryClipmapServer;

/**
 * Both directions are registered on BOTH physical sides. Merely registering
 * clientbound codecs in a Dist.CLIENT subscriber leaves a dedicated server
 * unable to encode the packets it sends.
 *
 * <p>The only references to client-only classes occur inside guarded client
 * delivery callbacks. Dedicated servers never execute those callbacks.
 */
public final class GeometryClipmapNetwork {
    private GeometryClipmapNetwork() {}

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("6");
        registrar.playToServer(
                ClipmapRequestPayload.TYPE,
                ClipmapRequestPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        GeometryClipmapServer.request(player,
                                payload.nearRenderDistanceChunks(),
                                payload.gridCells(), payload.requestId());
                    }
                }));
        registrar.playToClient(
                ClipmapHeightTilePayload.TYPE,
                ClipmapHeightTilePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        clientHeight(payload);
                    }
                }));
        registrar.playToClient(
                ClipmapSurfaceProxyPayload.TYPE,
                ClipmapSurfaceProxyPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        clientProxies(payload);
                    }
                }));
        registrar.playToClient(
                ClipmapStatusPayload.TYPE,
                ClipmapStatusPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        clientStatus(payload);
                    }
                }));
    }

    private static void clientHeight(ClipmapHeightTilePayload payload) {
        org.realscale.neoforge.client.clipmap.GeometryClipmapClientPayloads
                .acceptHeight(payload);
    }

    private static void clientProxies(ClipmapSurfaceProxyPayload payload) {
        org.realscale.neoforge.client.clipmap.GeometryClipmapClientPayloads
                .acceptProxies(payload);
    }

    private static void clientStatus(ClipmapStatusPayload payload) {
        org.realscale.neoforge.client.clipmap.GeometryClipmapClientPayloads
                .acceptStatus(payload);
    }
}
