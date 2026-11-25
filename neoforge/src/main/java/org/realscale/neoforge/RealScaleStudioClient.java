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

        int gap = 4;
        int buttonWidth = Math.min(154, Math.max(88, (pause.width - 16 - gap * 2) / 3));
        int rowWidth = buttonWidth * 3 + gap * 2;
        int x = (pause.width - rowWidth) / 2;
        int y = Math.max(4, pause.height - 28);
        boolean compact = buttonWidth < 140;

        event.addListener(Button.builder(Component.literal("RealScale Studio"),
                button -> Minecraft.getInstance().setScreen(
                        new RealScaleStudioScreen(pause)))
                .bounds(x, y, buttonWidth, 20).build());
        event.addListener(Button.builder(clipmapToggleLabel(compact),
                button -> {
                    org.realscale.neoforge.client.clipmap.GeometryClipmapClient.toggleEnabled();
                    button.setMessage(clipmapToggleLabel(compact));
                })
                .bounds(x + buttonWidth + gap, y, buttonWidth, 20).build());
        event.addListener(Button.builder(clipmapRangeLabel(compact),
                button -> {
                    org.realscale.neoforge.client.clipmap.GeometryClipmapClient.cycleRange();
                    button.setMessage(clipmapRangeLabel(compact));
                })
                .bounds(x + (buttonWidth + gap) * 2, y, buttonWidth, 20).build());
    }

    private static Component clipmapToggleLabel(boolean compact) {
        boolean enabled =
                org.realscale.neoforge.client.clipmap.GeometryClipmapClient.isEnabled();
        String state = enabled ? "ON" : "OFF";
        return Component.literal(compact ? "Clipmap: " + state
                : "Geometry Clipmap: " + state);
    }

    private static Component clipmapRangeLabel(boolean compact) {
        String label =
                org.realscale.neoforge.client.clipmap.GeometryClipmapClient.rangeLabel();
        return Component.literal(compact ? label.replace("Clipmap ", "") : label);
    }
}
