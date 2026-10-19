package org.realscale.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Adds a separate world-list Atlas entry; vanilla Play/Create/Delete remain
 * untouched. Does NOT intercept CreateWorldScreen or launch a hidden server.
 */
@EventBusSubscriber(modid="realscale", value=Dist.CLIENT)
public final class WorldAtlasMenuClient {
    private WorldAtlasMenuClient() { }

    @SubscribeEvent
    public static void onWorldListInit(ScreenEvent.Init.Post event) {
        if(!(event.getScreen() instanceof SelectWorldScreen screen))return;
        int x=Math.max(4,screen.width-122);
        event.addListener(Button.builder(
                Component.translatable("realscale.atlas.entry"),
                button -> Minecraft.getInstance().setScreen(
                        new WorldAtlasWorldListScreen(screen)))
                .bounds(x,7,116,20).build());
    }
}
