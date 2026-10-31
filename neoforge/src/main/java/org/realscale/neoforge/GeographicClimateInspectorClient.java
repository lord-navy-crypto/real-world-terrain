package org.realscale.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.Locale;

/**
 * Adds the fixed 1:20 geographic latitude report to the pause menu.
 *
 * The report is intentionally independent of the active NoiseRouter. A world
 * preset may use vanilla temperature noise while the geographic coordinate
 * model still has a valid latitude at every Overworld position.
 */
@EventBusSubscriber(modid = "realscale", value = Dist.CLIENT)
public final class GeographicClimateInspectorClient {
    private GeographicClimateInspectorClient() { }

    @SubscribeEvent
    public static void onPauseMenuInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof PauseScreen pause)) return;
        if (Minecraft.getInstance().level == null) return;

        int buttonWidth = 196;
        int x = (pause.width - buttonWidth) / 2;
        int y = Math.max(4, pause.height - 28);
        event.addListener(Button.builder(
                net.minecraft.network.chat.Component.literal("Geographic Climate Report"),
                button -> Minecraft.getInstance().setScreen(
                        new GeographicClimateInspectorScreen(pause)))
                .bounds(x, y, buttonWidth, 20).build());
    }

    /**
     * Returns the one supported geographic model: 1:20 meridional latitude.
     * No generator-node discovery is performed, so standard RealScale presets
     * and existing saves do not need a special experimental climate node.
     */
    static GeographicLatitudeProfile readGeographicProfile(Minecraft minecraft) {
        if (minecraft.level == null || !minecraft.level.dimension().equals(Level.OVERWORLD))
            return null;
        return new GeographicLatitudeProfile(
                GeoMath.CLIMATE_EQUATOR_Z,
                GeoMath.CLIMATE_POLE_DISTANCE_BLOCKS);
    }

    public record GeographicLatitudeProfile(int equatorZ, int poleDistance) {
        public double latitudeAt(int z) {
            return GeoMath.latitudeDeg(z, equatorZ, poleDistance);
        }

        public String scaleLabel() {
            return "1:20";
        }

        public String coordinateReference() {
            return "Equator Z: " + equatorZ + " | Equator-to-pole: "
                    + String.format(Locale.ROOT, "%,d", poleDistance) + " blocks";
        }
    }
}
