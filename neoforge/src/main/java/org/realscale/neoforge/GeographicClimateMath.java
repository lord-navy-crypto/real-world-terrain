package org.realscale.neoforge;

import java.util.Locale;

/**
 * Simplified latitude-based climate belts for the Geographic Climate Inspector.
 * These broad belts are not a Köppen classification or a local weather forecast.
 */
final class GeographicClimateMath {
    static final double TROPIC_LIMIT_DEGREES = 23.4366;
    static final double SUBTROPICAL_LIMIT_DEGREES = 35.0;
    static final double SUBPOLAR_LIMIT_DEGREES = 55.0;
    static final double POLAR_CIRCLE_DEGREES = 66.5634;
    static final double BOUNDARY_TOLERANCE_DEGREES = 0.5;

    enum Belt { TROPICAL, SUBTROPICAL, TEMPERATE, SUBPOLAR, POLAR }

    private GeographicClimateMath() { }

    static Belt classify(double latitudeDegrees) {
        if (!Double.isFinite(latitudeDegrees) || Math.abs(latitudeDegrees) > 90.000001)
            throw new IllegalArgumentException("Latitude must be finite and within -90..90 degrees");
        double latitude = Math.abs(latitudeDegrees);
        if (latitude < TROPIC_LIMIT_DEGREES) return Belt.TROPICAL;
        if (latitude < SUBTROPICAL_LIMIT_DEGREES) return Belt.SUBTROPICAL;
        if (latitude < SUBPOLAR_LIMIT_DEGREES) return Belt.TEMPERATE;
        if (latitude < POLAR_CIRCLE_DEGREES) return Belt.SUBPOLAR;
        return Belt.POLAR;
    }

    static String beltLabel(double latitudeDegrees) {
        return switch (classify(latitudeDegrees)) {
            case TROPICAL -> "Tropical belt";
            case SUBTROPICAL -> "Subtropical belt (approx.)";
            case TEMPERATE -> "Temperate belt";
            case SUBPOLAR -> "Subpolar belt (approx.)";
            case POLAR -> "Polar belt";
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
            return "Near the Tropic of Cancer / Capricorn";
        if (Math.abs(latitude - SUBTROPICAL_LIMIT_DEGREES) <= BOUNDARY_TOLERANCE_DEGREES)
            return "Near the approximate subtropical–temperate transition";
        if (Math.abs(latitude - SUBPOLAR_LIMIT_DEGREES) <= BOUNDARY_TOLERANCE_DEGREES)
            return "Near the approximate temperate–subpolar transition";
        if (Math.abs(latitude - POLAR_CIRCLE_DEGREES) <= BOUNDARY_TOLERANCE_DEGREES)
            return "Near the Arctic / Antarctic Circle";
        return "No major latitude boundary nearby";
    }

    static String latitudeLabel(double latitudeDegrees) {
        if (Math.abs(latitudeDegrees) < 0.000001) return "0.00° (Equator)";
        return String.format(Locale.ROOT, "%.2f° %s",
                Math.abs(latitudeDegrees), latitudeDegrees > 0 ? "N" : "S");
    }
}
