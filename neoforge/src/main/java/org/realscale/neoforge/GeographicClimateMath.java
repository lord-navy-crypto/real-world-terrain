package org.realscale.neoforge;

import java.util.Locale;

/**
 * Broad astronomical latitude belts for the in-game Geographic Climate Inspector.
 * These are latitude zones (tropics / temperate / polar), not a Köppen climate
 * classification or a prediction of local weather.
 */
final class GeographicClimateMath {
    static final double TROPIC_LIMIT_DEGREES = 23.4366;
    static final double POLAR_CIRCLE_DEGREES = 66.5634;
    static final double BOUNDARY_TOLERANCE_DEGREES = 0.5;

    enum Belt { TROPICAL, TEMPERATE, POLAR }

    private GeographicClimateMath() { }

    static Belt classify(double latitudeDegrees) {
        double latitude = Math.abs(latitudeDegrees);
        if (!Double.isFinite(latitudeDegrees) || latitude > 90.000001)
            throw new IllegalArgumentException("Latitude must be finite and within -90..90 degrees");
        if (latitude < TROPIC_LIMIT_DEGREES) return Belt.TROPICAL;
        if (latitude < POLAR_CIRCLE_DEGREES) return Belt.TEMPERATE;
        return Belt.POLAR;
    }

    static String beltLabel(double latitudeDegrees) {
        return switch (classify(latitudeDegrees)) {
            case TROPICAL -> "Tropical belt";
            case TEMPERATE -> "Temperate belt";
            case POLAR -> "Polar / frigid belt";
        };
    }

    static String hemisphereLabel(double latitudeDegrees) {
        if (latitudeDegrees > 0.000001) return "Northern Hemisphere";
        if (latitudeDegrees < -0.000001) return "Southern Hemisphere";
        return "Equator";
    }

    static String boundaryLabel(double latitudeDegrees) {
        double latitude = Math.abs(latitudeDegrees);
        if (Math.abs(latitude - TROPIC_LIMIT_DEGREES) <= BOUNDARY_TOLERANCE_DEGREES)
            return "Near the tropical–temperate boundary (Tropic)";
        if (Math.abs(latitude - POLAR_CIRCLE_DEGREES) <= BOUNDARY_TOLERANCE_DEGREES)
            return "Near the temperate–polar boundary (Polar Circle)";
        return "No major astronomical belt boundary nearby";
    }

    static String latitudeLabel(double latitudeDegrees) {
        if (Math.abs(latitudeDegrees) < 0.000001) return "0.00° (Equator)";
        return String.format(Locale.ROOT, "%.2f° %s",
                Math.abs(latitudeDegrees), latitudeDegrees > 0 ? "N" : "S");
    }
}
