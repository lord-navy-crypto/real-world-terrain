package org.realscale.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** One pause-menu entry point for Terrain Lab, Climate and Performance tools. */
@EventBusSubscriber(modid="realscale", value=Dist.CLIENT)
public final class RealScaleStudioClient {
    private RealScaleStudioClient() {}

    @SubscribeEvent
    public static void onPauseMenuInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof PauseScreen pause)) return;
        if (Minecraft.getInstance().level == null) return;
        int width=154;
        int gap=4;
        int x=(pause.width-(width*2+gap))/2;
        int y=Math.max(4,pause.height-28);
        event.addListener(Button.builder(Component.literal("RealScale Studio"),
                button -> Minecraft.getInstance().setScreen(
                        new RealScaleStudioScreen(pause)))
                .bounds(x,y,width,20).build());
        event.addListener(Button.builder(clipmapToggleLabel(),
                button -> {
                    org.realscale.neoforge.client.clipmap.GeometryClipmapClient.toggleEnabled();
                    button.setMessage(clipmapToggleLabel());
                })
                .bounds(x+width+gap,y,width,20).build());
    }

    private static Component clipmapToggleLabel() {
        boolean enabled =
                org.realscale.neoforge.client.clipmap.GeometryClipmapClient.isEnabled();
        return Component.literal("Geometry Clipmap: " + (enabled ? "ON" : "OFF"));
    }
}
