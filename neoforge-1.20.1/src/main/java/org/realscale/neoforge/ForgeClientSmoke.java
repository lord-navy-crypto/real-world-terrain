package org.realscale.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in CI checks actual screen initialization and rendered frames. */
@Mod.EventBusSubscriber(modid = "realscale", value = Dist.CLIENT)
public final class ForgeClientSmoke {
    private static int stage;
    private static int frames;
    private static Screen title;
    private static Screen expected;
    private ForgeClientSmoke() {}

    @SubscribeEvent
    public static void verify(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !"client".equals(System.getenv("REALSCALE_FORGE_CI_SMOKE"))) return;
        var mc = Minecraft.getInstance();
        if (stage == 0) {
            if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
            title = mc.screen;
        } else if (mc.screen != expected || mc.getOverlay() != null) {
            frames = 0;
            return;
        }
        if (++frames < 12) return;
        frames = 0;
        switch (stage++) {
            case 0 -> expected = new RealScaleStudioScreen(title);
            case 1 -> expected = RealScaleTerrainLabClient.createScreen(title);
            case 2 -> expected = new RealScalePerformanceStudioScreen(title);
            case 3 -> expected = new GeographicClimateInspectorScreen(title);
            case 4 -> {
                System.out.println("FORGE_CLIENT_UI_SMOKE_OK studio terrain_lab performance climate");
                mc.stop();
                return;
            }
            default -> { return; }
        }
        mc.setScreen(expected);
    }
}
