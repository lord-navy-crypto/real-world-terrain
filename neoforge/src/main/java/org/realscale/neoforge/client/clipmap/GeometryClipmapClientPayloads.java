package org.realscale.neoforge.client.clipmap;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.realscale.neoforge.network.ClipmapHeightTilePayload;

/** Client-only S2C payload registration; never loads Minecraft client classes on a dedicated server. */
@EventBusSubscriber(modid = "realscale",
        bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GeometryClipmapClientPayloads {
    private GeometryClipmapClientPayloads() {}

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(
                ClipmapHeightTilePayload.TYPE,
                ClipmapHeightTilePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> GeometryClipmapClient.accept(payload)));
    }
}
