package org.realscale.neoforge;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

public final class RealScaleFabricClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        RealScaleHovercraftClient.registerRenderers();
        if (FabricLoader.getInstance().isModLoaded("yet_another_config_lib_v3")) {
            RealScaleStudioClient.register();
        } else {
            System.err.println("[RealScale] Install YACL 3.6.6 for the Terrain Lab and Studio screens.");
        }
        ClientTickEvents.END_CLIENT_TICK.register(RealScaleAdaptiveVisibilityClient::onClientTick);
    }
}
