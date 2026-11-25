package org.realscale.neoforge;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.TitleScreen;

public final class RealScaleFabricClient implements ClientModInitializer {
    private static boolean noYaclSmokeReported;

    @Override public void onInitializeClient() {
        RealScaleHovercraftClient.registerRenderers();
        if (FabricLoader.getInstance().isModLoaded("yet_another_config_lib_v3")) {
            RealScaleStudioClient.register();
        } else {
            System.err.println("[RealScale] Install YACL 3.6.6 for the Terrain Lab and Studio screens.");
        }
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            RealScaleAdaptiveVisibilityClient.onClientTick(client);
            if (Boolean.getBoolean("realscale.fabric.ci.clientSmoke")
                    && !noYaclSmokeReported
                    && !FabricLoader.getInstance().isModLoaded("yet_another_config_lib_v3")
                    && client.screen instanceof TitleScreen) {
                noYaclSmokeReported = true;
                System.err.println("FABRIC_CLIENT_NO_YACL_SMOKE_OK");
            }
        });
    }
}
