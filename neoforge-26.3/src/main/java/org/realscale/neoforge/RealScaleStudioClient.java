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
        int width=196;
        int x=(pause.width-width)/2;
        int y=Math.max(4,pause.height-28);
        event.addListener(Button.builder(Component.literal("RealScale Studio"),
                button -> Minecraft.getInstance().setScreen(
                        new RealScaleStudioScreen(pause)))
                .bounds(x,y,width,20).build());
    }
}
