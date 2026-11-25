package org.realscale.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Opt-in CI smoke test. Uses the render-tick event that the legacy 1.20.1
 * client reliably posts after the title screen and each screen has rendered.
 * It is inert in normal gameplay unless REALSCALE_FORGE_CI_SMOKE=client.
 */
@Mod.EventBusSubscriber(modid = "realscale", value = Dist.CLIENT)
public final class ForgeClientSmoke {
    private static int stage;
    private static int framesOnScreen;
    private static int waitFrames;
    private static Screen title;
    private static Screen expected;
    private static String lastWaitState = "";
    private static boolean firstRenderTickReported;

    private ForgeClientSmoke() {}

    @SubscribeEvent
    public static void verify(RenderTickEvent event) {
        if (event.phase != RenderTickEvent.Phase.END
                || !"client".equals(System.getenv("REALSCALE_FORGE_CI_SMOKE"))) {
            return;
        }

        if (!firstRenderTickReported) {
            firstRenderTickReported = true;
            System.out.println("NEOFORGE_CLIENT_SMOKE_RENDER_TICK_OK");
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.getOverlay() != null) {
            reportWaitState("overlay=" + mc.getOverlay().getClass().getName());
            return;
        }

        if (stage == 0 && mc.screen instanceof AccessibilityOnboardingScreen onboarding) {
            if (++framesOnScreen >= 20) {
                System.out.println("NEOFORGE_CLIENT_ACCESSIBILITY_ONBOARDING_DISMISSED");
                onboarding.onClose();
                framesOnScreen = 0;
            }
            return;
        }

        if (stage == 0) {
            if (!(mc.screen instanceof TitleScreen)) {
                reportWaitState("screen=" + (mc.screen == null
                        ? "null" : mc.screen.getClass().getName()));
                return;
            }
            title = mc.screen;
        } else if (mc.screen != expected) {
            reportWaitState("expected=" + expected.getClass().getName()
                    + " actual=" + (mc.screen == null
                    ? "null" : mc.screen.getClass().getName()));
            framesOnScreen = 0;
            return;
        }

        lastWaitState = "";
        if (++framesOnScreen < 20) return;
        framesOnScreen = 0;

        switch (stage++) {
            case 0 -> expected = new RealScaleStudioScreen(title);
            case 1 -> expected = RealScaleTerrainLabClient.createScreen(title);
            case 2 -> expected = new RealScalePerformanceStudioScreen(title);
            case 3 -> expected = new GeographicClimateInspectorScreen(title);
            case 4 -> {
                System.out.println(
                        "FORGE_CLIENT_UI_SMOKE_OK studio terrain_lab performance climate");
                mc.stop();
                return;
            }
            default -> { return; }
        }

        if (expected == null) {
            throw new IllegalStateException(
                    "RealScale client smoke created a null screen at stage " + stage);
        }
        System.out.println("NEOFORGE_CLIENT_UI_STAGE_OK stage=" + stage
                + " screen=" + expected.getClass().getName());
        mc.setScreen(expected);
    }

    private static void reportWaitState(String state) {
        if (!state.equals(lastWaitState) && ++waitFrames % 60 == 0) {
            lastWaitState = state;
            System.out.println("NEOFORGE_CLIENT_UI_WAIT stage=" + stage + " " + state);
        }
    }
}
