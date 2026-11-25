package org.realscale.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

import java.util.Locale;

/**
 * Adds the fixed 1:20 geographic latitude report to the pause menu.
 *
 * The report is intentionally independent of the active NoiseRouter. A world
 * preset may use vanilla temperature noise while the geographic coordinate
 * model still has a valid latitude at every Overworld position.
 */
public final class GeographicClimateInspectorClient {
    private GeographicClimateInspectorClient() { }

    /**
     * Returns the one supported geographic model: 1:20 meridional latitude.
     * No generator-node discovery is performed, so standard RealScale presets
     * and existing saves do not need a special experimental climate node.
     */
    static GeographicLatitudeProfile readGeographicProfile(Minecraft minecraft) {
        if (minecraft.level == null || !minecraft.level.dimension().equals(Level.OVERWORLD))
            return null;
        return new GeographicLatitudeProfile();
    }

    public record GeographicLatitudeProfile() {
        public double latitudeAt(int z) {
            return GeographicClimateMath.geodeticLatitudeDeg(z);
        }

        public String scaleLabel() {
            return "1:20";
        }

        public String coordinateReference() {
            return "Equator Z: " + GeoMath.CLIMATE_EQUATOR_Z + " | Equator-to-pole: "
                    + String.format(Locale.ROOT, "%,d", GeoMath.CLIMATE_POLE_DISTANCE_BLOCKS)
                    + " blocks (WGS84 meridional approximation)";
        }
    }
}
