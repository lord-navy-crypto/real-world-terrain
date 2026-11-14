package org.realscale.neoforge.lotm.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

/** O key or a survival inventory button opens the existing synchronized LOTM panel. */
@EventBusSubscriber(modid = "realscale", value = Dist.CLIENT)
public final class LotmClientKeys {
    public static final KeyMapping OPEN_PANEL = new KeyMapping(
            "key.realscale.lotm.panel", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O, "key.categories.realscale.lotm");

    private LotmClientKeys() {}

    /** Internal menu transport. Players do not need to type commands or enable cheats. */
    private static void openPanel() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null) return;
        minecraft.getConnection().sendCommand("lotm panel");
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) return;
        while (OPEN_PANEL.consumeClick()) openPanel();
    }

    @SubscribeEvent
    public static void onInventoryOpen(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)
                || Minecraft.getInstance().player == null) return;
        int x = Math.min(screen.width - 88, (screen.width - 176) / 2 + 180);
        int y = (screen.height - 166) / 2 + 3;
        event.addListener(Button.builder(
                Component.translatable("gui.realscale.lotm_panel.open"),
                button -> {
                    Minecraft.getInstance().setScreen(null);
                    openPanel();
                }).bounds(Math.max(3, x), Math.max(3, y), 84, 20).build());
    }
}
