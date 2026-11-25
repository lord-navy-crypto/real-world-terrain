package org.realscale.neoforge;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.TitleScreen;

public final class RealScaleFabricClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        RealScaleHovercraftClient.registerRenderers();
        if (FabricLoader.getInstance().isModLoaded("yet_another_config_lib_v3")) {
            RealScaleStudioClient.register();
        } else {
            System.err.println("[RealScale] Install YACL 3.8.1 for the Terrain Lab and Studio screens.");
        }
        ClientTickEvents.END_CLIENT_TICK.register(RealScaleAdaptiveVisibilityClient::onClientTick);
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            if (!Boolean.getBoolean("realscale.fabric.ci.clientSmoke")) return;
            if (FabricLoader.getInstance().isModLoaded("yet_another_config_lib_v3")) {
                // Force the title screen through ScreenEvents.AFTER_INIT so the Studio button is tested.
                client.setScreen(new TitleScreen());
            } else {
                System.err.println("FABRIC_CLIENT_NO_YACL_SMOKE_OK");
            }
            client.stop();
        });
    }
}
