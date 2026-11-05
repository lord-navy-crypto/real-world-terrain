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
            return latitudeDegrees >= 0 ? "Near the Tropic of Cancer" : "Near the Tropic of Capricorn";
        if (Math.abs(latitude - SUBTROPICAL_LIMIT_DEGREES) <= BOUNDARY_TOLERANCE_DEGREES)
            return "Near the approximate subtropical–temperate transition";
        if (Math.abs(latitude - SUBPOLAR_LIMIT_DEGREES) <= BOUNDARY_TOLERANCE_DEGREES)
            return "Near the approximate temperate–subpolar transition";
        if (Math.abs(latitude - POLAR_CIRCLE_DEGREES) <= BOUNDARY_TOLERANCE_DEGREES)
            return latitudeDegrees >= 0 ? "Near the Arctic Circle" : "Near the Antarctic Circle";
        return "No major latitude boundary nearby";
    }

    static String latitudeLabel(double latitudeDegrees) {
        if (Math.abs(latitudeDegrees) < 0.000001) return "0.00° (Equator)";
        return String.format(Locale.ROOT, "%.2f° %s",
                Math.abs(latitudeDegrees), latitudeDegrees > 0 ? "N" : "S");
    }

    // The report uses a fixed 1:20 meridional map, but the WGS84 meridian
    // does not advance by exactly the same number of metres per degree.
    // Leave GeoMath.latitudeDeg untouched: it is part of legacy worldgen.
    private static final double WGS84_SEMIMAJOR_METRES = 6_378_137.0;
    private static final double WGS84_ECCENTRICITY_SQUARED = 0.00669437999014;
    private static final double WGS84_QUARTER_MERIDIAN_METRES = 10_001_965.729;
    static final int SOLAR_YEAR_DAYS = 96;
    private static final double SOLAR_CONSTANT_WATTS_PER_M2 = 1_361.0;
    private static final double LAPSE_RATE_C_PER_METRE = 0.0065;
    private static final int REFERENCE_SEA_LEVEL_Y = 63;

    /** WGS84 geodetic latitude for the repeating, north-negative-Z meridian. */
    static double geodeticLatitudeDeg(int z, int equatorZ, int poleDistance) {
        if (poleDistance <= 0) throw new IllegalArgumentException("Pole distance must be positive");
        double phase = (((double) equatorZ - (double) z) / poleDistance) % 4.0;
        if (phase < 0.0) phase += 4.0;
        double signedArcFraction;
        if (phase <= 1.0) signedArcFraction = phase;
        else if (phase <= 2.0) signedArcFraction = 2.0 - phase;
        else if (phase <= 3.0) signedArcFraction = 2.0 - phase;
        else signedArcFraction = phase - 4.0;
        double fraction = Math.abs(signedArcFraction);
        if (fraction == 0.0) return 0.0;
        if (fraction >= 1.0) return Math.copySign(90.0, signedArcFraction);
        // Invert the WGS84 meridian arc using the fourth-order footpoint
        // latitude series. No I/O, iteration, or worldgen state is required.
        double e2 = WGS84_ECCENTRICITY_SQUARED;
        double e4 = e2 * e2;
        double e6 = e4 * e2;
        double mu = fraction * WGS84_QUARTER_MERIDIAN_METRES /
                (WGS84_SEMIMAJOR_METRES * (1.0 - e2 / 4.0 - 3.0 * e4 / 64.0 - 5.0 * e6 / 256.0));
        double e1 = (1.0 - Math.sqrt(1.0 - e2)) / (1.0 + Math.sqrt(1.0 - e2));
        double e1_2 = e1 * e1, e1_3 = e1_2 * e1, e1_4 = e1_2 * e1_2;
        double radians = mu
                + (3.0 * e1 / 2.0 - 27.0 * e1_3 / 32.0) * Math.sin(2.0 * mu)
                + (21.0 * e1_2 / 16.0 - 55.0 * e1_4 / 32.0) * Math.sin(4.0 * mu)
                + 151.0 * e1_3 / 96.0 * Math.sin(6.0 * mu)
                + 1097.0 * e1_4 / 512.0 * Math.sin(8.0 * mu);
        return Math.copySign(Math.min(90.0, Math.toDegrees(radians)), signedArcFraction);
    }

    /**
     * Solar geometry for a deliberately simulated 96-Minecraft-day year.
     * Day zero is a virtual March equinox, not a real-world calendar date.
     * Irradiance is daily-mean TOP-OF-ATMOSPHERE sunlight, never surface
     * weather or actual temperature. Altitude only reports a lapse-rate
     * DIFFERENCE from Y=63, not an absolute air-temperature prediction.
     */
    record SolarEstimate(int virtualDay, double declinationDegrees,
                         double daylightHours, double meanToaWattsPerM2,
                         double altitudeTemperatureOffsetC, String season) { }

    static SolarEstimate estimateSolar(double latitudeDegrees, int y, long gameTimeTicks) {
        classify(latitudeDegrees); // rejects NaN, infinity, and invalid latitudes
        int day = (int) Math.floorMod(Math.floorDiv(gameTimeTicks, 24_000L), SOLAR_YEAR_DAYS);
        double theta = 2.0 * Math.PI * day / SOLAR_YEAR_DAYS;
        double declination = Math.asin(Math.sin(Math.toRadians(TROPIC_LIMIT_DEGREES))
                * Math.sin(theta));
        double latitude = Math.toRadians(latitudeDegrees);
        double cosHourAngle = -Math.tan(latitude) * Math.tan(declination);
        double hourAngle = Math.acos(Math.max(-1.0, Math.min(1.0, cosHourAngle)));
        double daylightHours = 24.0 * hourAngle / Math.PI;
        double irradiance = SOLAR_CONSTANT_WATTS_PER_M2 / Math.PI *
                (hourAngle * Math.sin(latitude) * Math.sin(declination)
                        + Math.cos(latitude) * Math.cos(declination) * Math.sin(hourAngle));
        // Minecraft's Y coordinate is not automatically a surveyed elevation.
        double relativeCooling = -(y - REFERENCE_SEA_LEVEL_Y) * LAPSE_RATE_C_PER_METRE;
        String season;
        if (Math.abs(latitudeDegrees) < TROPIC_LIMIT_DEGREES) {
            season = "Equatorial/tropical solar cycle";
        } else {
            String northern = switch (day / (SOLAR_YEAR_DAYS / 4)) {
                case 0 -> "Spring"; case 1 -> "Summer";
                case 2 -> "Autumn"; default -> "Winter";
            };
            season = latitudeDegrees > 0.0 ? northern : switch (northern) {
                case "Spring" -> "Autumn"; case "Summer" -> "Winter";
                case "Autumn" -> "Spring"; default -> "Summer";
            };
            season += " (virtual)";
        }
        return new SolarEstimate(day, Math.toDegrees(declination),
                daylightHours, Math.max(0.0, irradiance), relativeCooling, season);
    }
}
