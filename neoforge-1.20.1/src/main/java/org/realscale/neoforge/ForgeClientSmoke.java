package org.realscale.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Opt-in CI smoke test. Hold each RealScale screen for 20 client ticks so the
 * normal client render loop has time to initialize and draw it before moving on.
 */
public final class ForgeClientSmoke {
    private static int stage;
    private static int ticksOnScreen;
    private static int waitTicks;
    private static Screen title;
    private static Screen expected;
    private static boolean waitStateReported;

    private ForgeClientSmoke() {}

    /** Explicit client-only registration; legacy Forge ModDev does not discover
     * this test subscriber reliably from the development classpath. */
    public static void register() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(ForgeClientSmoke.class);
        System.out.println("NEOFORGE_CLIENT_SMOKE_SUBSCRIBER_LOADED mode="
                + System.getenv("REALSCALE_FORGE_CI_SMOKE"));
    }

    @SubscribeEvent
    public static void verify(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !"client".equals(System.getenv("REALSCALE_FORGE_CI_SMOKE"))) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.getOverlay() != null) {
            reportWaitState("overlay=" + mc.getOverlay().getClass().getName());
            return;
        }

        // Fresh 1.20.1 client profiles can open this vanilla first-run screen
        // before the title screen. Close it through its own callback so the
        // smoke test follows the same normal path as a user pressing Done.
        if (stage == 0 && mc.screen instanceof AccessibilityOnboardingScreen onboarding) {
            if (++ticksOnScreen >= 20) {
                System.out.println("NEOFORGE_CLIENT_ACCESSIBILITY_ONBOARDING_DISMISSED");
                onboarding.onClose();
                ticksOnScreen = 0;
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
            ticksOnScreen = 0;
            return;
        }

        waitStateReported = false;
        if (++ticksOnScreen < 20) {
            return;
        }
        ticksOnScreen = 0;

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
            default -> {
                return;
            }
        }

        if (expected == null) {
            throw new IllegalStateException("RealScale client smoke created a null screen at stage " + stage);
        }
        System.out.println("NEOFORGE_CLIENT_UI_STAGE_OK stage=" + stage
                + " screen=" + expected.getClass().getName());
        mc.setScreen(expected);
    }

    private static void reportWaitState(String state) {
        if (!waitStateReported && ++waitTicks % 100 == 0) {
            System.out.println("NEOFORGE_CLIENT_UI_WAIT stage=" + stage + " " + state);
            waitStateReported = true;
        }
    }
}
