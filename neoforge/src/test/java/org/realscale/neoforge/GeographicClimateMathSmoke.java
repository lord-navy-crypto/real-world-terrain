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

        System.out.println("GEOGRAPHIC_CLIMATE_INSPECTOR_1_20_PASS");
    }

    private static boolean close(double actual, double expected, double tolerance) {
        return Math.abs(actual - expected) <= tolerance;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
