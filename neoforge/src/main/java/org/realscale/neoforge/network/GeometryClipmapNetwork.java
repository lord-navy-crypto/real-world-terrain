package org.realscale.neoforge.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.realscale.neoforge.GeometryClipmapServer;

/** Common-side registration for the bounded clipmap request. */
public final class GeometryClipmapNetwork {
    private GeometryClipmapNetwork() {}

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(
                ClipmapRequestPayload.TYPE,
                ClipmapRequestPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        GeometryClipmapServer.request(player,
                                payload.nearRenderDistanceChunks());
                    }
                }));
    }
}
