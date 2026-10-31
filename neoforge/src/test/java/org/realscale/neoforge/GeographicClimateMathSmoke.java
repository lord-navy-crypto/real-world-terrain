package org.realscale.neoforge;

/** Pure-Java regression checks for the fixed 1:20 geographic climate report. */
public final class GeographicClimateMathSmoke {
    private GeographicClimateMathSmoke() { }

    public static void main(String[] args) {
        int pole = GeoMath.CLIMATE_POLE_DISTANCE_BLOCKS;
        check(pole == 500_098, "1:20 pole distance is 500,098 blocks");
        check(GeoMath.latitudeDeg(0, GeoMath.CLIMATE_EQUATOR_Z, pole) == 0.0,
                "Z=0 is the equator");
        check(close(GeoMath.latitudeDeg(-pole / 2, 0, pole), 45.0, 0.001),
                "negative Z is northern latitude");
        check(close(GeoMath.latitudeDeg(-pole, 0, pole), 90.0, 0.001),
                "negative Z pole is 90 north");
        check(close(GeoMath.latitudeDeg(pole / 2, 0, pole), -45.0, 0.001),
                "positive Z is southern latitude");
        check(close(GeoMath.latitudeDeg(pole, 0, pole), -90.0, 0.001),
                "positive Z pole is 90 south");
        check(close(GeoMath.latitudeDeg(-2 * pole, 0, pole), 0.0, 0.001),
                "latitude repeats continuously at the opposite equator");

        check(GeographicClimateMath.classify(0) == GeographicClimateMath.Belt.TROPICAL,
                "equator is tropical");
        check(GeographicClimateMath.classify(23.0) == GeographicClimateMath.Belt.TROPICAL,
                "inside Tropic boundary is tropical");
        check(GeographicClimateMath.classify(23.4366) == GeographicClimateMath.Belt.SUBTROPICAL,
                "Tropic boundary enters subtropical belt");
        check(GeographicClimateMath.classify(30.0) == GeographicClimateMath.Belt.SUBTROPICAL,
                "30 degrees is subtropical");
        check(GeographicClimateMath.classify(45.0) == GeographicClimateMath.Belt.TEMPERATE,
                "mid-latitudes are temperate");
        check(GeographicClimateMath.classify(60.0) == GeographicClimateMath.Belt.SUBPOLAR,
                "60 degrees is subpolar");
        check(GeographicClimateMath.classify(66.5634) == GeographicClimateMath.Belt.POLAR,
                "Polar Circle boundary enters polar belt");
        check(GeographicClimateMath.classify(-90.0) == GeographicClimateMath.Belt.POLAR,
                "south pole is polar");

        check("Northern Hemisphere".equals(GeographicClimateMath.hemisphereLabel(45)),
                "positive latitude is north");
        check("Southern Hemisphere".equals(GeographicClimateMath.hemisphereLabel(-45)),
                "negative latitude is south");
        check("Equator".equals(GeographicClimateMath.hemisphereLabel(0)),
                "zero latitude is equator");
        check(GeographicClimateMath.latitudeLabel(45).equals("45.00° N"),
                "north latitude formatting");
        check(GeographicClimateMath.latitudeLabel(-45).equals("45.00° S"),
                "south latitude formatting");
        check(GeographicClimateMath.boundaryLabel(23.5).contains("Tropic"),
                "tropic transition is reported");
        check(GeographicClimateMath.boundaryLabel(66.6).contains("Arctic"),
                "polar circle transition is reported");

        boolean rejected = false;
        try { GeographicClimateMath.classify(Double.NaN); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "NaN latitude rejected");
        rejected = false;
        try { GeographicClimateMath.classify(91); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "out-of-range latitude rejected");

        // Inspector-only inverse meridian arc: WGS84 45-degree arc / 20.
        // The legacy linear worldgen latitude remains untouched.
        double geodetic45 = GeographicClimateMath.geodeticLatitudeDeg(-249247, 0, pole);
        check(close(geodetic45, 45.0, .005), "WGS84 45N meridian arc at 1:20");
        check(close(GeographicClimateMath.geodeticLatitudeDeg(249247, 0, pole),
                -geodetic45, 1e-10), "hemispheres must mirror");
        check(close(GeographicClimateMath.geodeticLatitudeDeg(-pole, 0, pole), 90, 0),
                "north pole");
        check(close(GeographicClimateMath.geodeticLatitudeDeg(pole, 0, pole), -90, 0),
                "south pole");
        check(close(GeographicClimateMath.geodeticLatitudeDeg(2 * pole, 0, pole), 0, 0),
                "opposite equator");
        check(close(GeographicClimateMath.geodeticLatitudeDeg(-3 * pole, 0, pole), -90, 0),
                "wrapped south pole");
        check(Math.abs(GeographicClimateMath.geodeticLatitudeDeg(-2 * pole + 1, 0, pole)
                - GeographicClimateMath.geodeticLatitudeDeg(-2 * pole - 1, 0, pole)) < .001,
                "latitude continuous across wrapped equator");
        check(close(GeographicClimateMath.geodeticLatitudeDeg(29_999_999, 0, pole),
                -GeographicClimateMath.geodeticLatitudeDeg(-29_999_999, 0, pole),
                1e-8), "large Minecraft coordinates remain symmetric");

        GeographicClimateMath.SolarEstimate equinox =
                GeographicClimateMath.estimateSolar(0, 63, 0);
        check(close(equinox.declinationDegrees(), 0, 1e-10), "virtual equinox declination");
        check(close(equinox.daylightHours(), 12, 1e-10), "equatorial equinox daylength");
        check(close(equinox.meanToaWattsPerM2(), 1361.0 / Math.PI, 1e-6),
                "daily mean equinox equatorial solar input");
        GeographicClimateMath.SolarEstimate northSummer =
                GeographicClimateMath.estimateSolar(45, 63, 24L * 24_000L);
        GeographicClimateMath.SolarEstimate southWinter =
                GeographicClimateMath.estimateSolar(-45, 63, 24L * 24_000L);
        check(close(northSummer.declinationDegrees(), 23.4366, 1e-6),
                "north summer solstice declination");
        check(northSummer.daylightHours() > 15.0 && southWinter.daylightHours() < 9.0,
                "seasonal daylight differs between hemispheres");
        check("Summer (virtual)".equals(northSummer.season())
                && "Winter (virtual)".equals(southWinter.season()),
                "seasons are reversed across hemispheres");
        check(northSummer.meanToaWattsPerM2() > southWinter.meanToaWattsPerM2(),
                "seasonally appropriate hemisphere solar input");
        GeographicClimateMath.SolarEstimate polarSummer =
                GeographicClimateMath.estimateSolar(90, 63, 24L * 24_000L);
        GeographicClimateMath.SolarEstimate polarWinter =
                GeographicClimateMath.estimateSolar(90, 63, 72L * 24_000L);
        check(close(polarSummer.daylightHours(), 24, 1e-9)
                && close(polarWinter.daylightHours(), 0, 1e-9),
                "polar day and polar night");
        check(close(GeographicClimateMath.estimateSolar(35, 1063, 0)
                .altitudeTemperatureOffsetC(), -6.5, 1e-9),
                "standard lapse-rate offset for 1000 blocks above reference");
        check(GeographicClimateMath.estimateSolar(35, 63, -24_000)
                .virtualDay() == 95, "negative game time uses floor-mod calendar");

        System.out.println("GEOGRAPHIC_CLIMATE_INSPECTOR_1_20_PASS");
    }

    private static boolean close(double actual, double expected, double tolerance) {
        return Math.abs(actual - expected) <= tolerance;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
