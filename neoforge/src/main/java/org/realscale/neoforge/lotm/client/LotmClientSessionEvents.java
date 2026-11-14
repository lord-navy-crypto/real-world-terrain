package org.realscale.neoforge.lotm.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.realscale.neoforge.lotm.LotmPanelNetwork;

/** Prevent one world's private research journal from leaking into another client session. */
@EventBusSubscriber(modid = "realscale", bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class LotmClientSessionEvents {
    private LotmClientSessionEvents() {}

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        LotmPanelNetwork.clearClientResearchLog();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        LotmPanelNetwork.clearClientResearchLog();
    }
}
