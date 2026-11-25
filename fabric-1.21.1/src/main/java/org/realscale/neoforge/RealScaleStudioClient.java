package org.realscale.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;

/** One pause-menu entry point for Terrain Lab, Climate and Performance tools. */
public final class RealScaleStudioClient {
    private static boolean smokeReported;
    private RealScaleStudioClient() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, pause, sw, sh) -> {
        if (!(pause instanceof PauseScreen) && !(pause instanceof TitleScreen)) return;
        int width=196;
        int x=(pause.width-width)/2;
        int y=Math.max(4,pause.height-28);
        Screens.getButtons(pause).add(Button.builder(Component.literal("RealScale Studio"),
                button -> Minecraft.getInstance().setScreen(
                        new RealScaleStudioScreen(pause)))
                .bounds(x,y,width,20).build());
        if (!smokeReported && pause instanceof TitleScreen
                && Boolean.getBoolean("realscale.fabric.ci.clientSmoke")) {
            smokeReported = true;
            System.err.println("FABRIC_CLIENT_UI_SMOKE_OK");
        }
        });
    }
}
