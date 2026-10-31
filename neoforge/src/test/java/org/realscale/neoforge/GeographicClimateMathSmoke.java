package org.realscale.neoforge;

/** Pure-Java regression checks for the Geographic Climate Inspector. */
public final class GeographicClimateMathSmoke {
    private GeographicClimateMathSmoke() { }

    public static void main(String[] args) {
        check(GeographicClimateMath.classify(0) == GeographicClimateMath.Belt.TROPICAL,
                "equator is tropical");
        check(GeographicClimateMath.classify(23.0) == GeographicClimateMath.Belt.TROPICAL,
                "inside Tropic boundary is tropical");
        check(GeographicClimateMath.classify(23.4366) == GeographicClimateMath.Belt.TEMPERATE,
                "Tropic boundary belongs to temperate side");
        check(GeographicClimateMath.classify(45.0) == GeographicClimateMath.Belt.TEMPERATE,
                "mid-latitudes are temperate");
        check(GeographicClimateMath.classify(66.5634) == GeographicClimateMath.Belt.POLAR,
                "Polar Circle boundary belongs to polar side");
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
        check(GeographicClimateMath.boundaryLabel(66.6).contains("Polar Circle"),
                "polar transition is reported");

        boolean rejected = false;
        try { GeographicClimateMath.classify(Double.NaN); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "NaN latitude rejected");
        rejected = false;
        try { GeographicClimateMath.classify(91); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "out-of-range latitude rejected");

        System.out.println("GEOGRAPHIC_CLIMATE_INSPECTOR_MATH_PASS");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
