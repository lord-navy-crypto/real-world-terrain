package org.realscale.neoforge;

/** Pure-Java checks for the default finite Z-latitude / X-longitude map. */
public final class ZonalLatitudeClimateSmoke {
    private ZonalLatitudeClimateSmoke() {}
    private static void check(boolean ok,String reason) {
        if(!ok)throw new AssertionError(reason);
    }
    private static void close(double actual,double expected,double tolerance,String reason) {
        check(Math.abs(actual-expected)<=tolerance,
                reason+": "+actual+" vs "+expected);
    }
    public static void main(String[] args) {
        final int quarterMeridian=10_001_966;
        final int equatorialCircumference=40_075_017;

        close(GeoMath.clampedLatitudeDeg(0,0,quarterMeridian),0,0,"equator");
        close(GeoMath.clampedLatitudeDeg(-quarterMeridian,0,quarterMeridian),90,0,"north pole");
        close(GeoMath.clampedLatitudeDeg(quarterMeridian,0,quarterMeridian),-90,0,"south pole");
        close(GeoMath.clampedLatitudeDeg(-2*quarterMeridian,0,quarterMeridian),90,0,
                "north polar cap must clamp");
        close(GeoMath.clampedLatitudeDeg(2*quarterMeridian,0,quarterMeridian),-90,0,
                "south polar cap must clamp");
        close(GeoMath.clampedLatitudeDeg(0,4_027_458,quarterMeridian),36.24,0.001,
                "Big Sur nominal latitude anchor");

        close(GeoMath.longitudeDeg(0,0),0,0,"prime meridian");
        close(GeoMath.longitudeDeg(10_018_754,0),90,0.001,"east quarter-turn");
        close(GeoMath.longitudeDeg(-10_018_754,0),-90,0.001,"west quarter-turn");
        close(GeoMath.longitudeDeg(equatorialCircumference,0),0,0.001,"longitude wraps");
        close(GeoMath.longitudeDeg(0,13_559_827),-121.81,0.001,
                "Big Sur nominal longitude anchor");

        // The old periodic latitude and every radial geometry remain available.
        close(GeoMath.latitudeDeg(-2*quarterMeridian,0,quarterMeridian),0,0,
                "legacy periodic latitude semantics");
        close(GeoMath.areaBalancedRadialLatitudeDeg(8192,0,0,0,8192),90,0,
                "legacy radial equal-area mode");

        double equatorial=GeoMath.balancedLatitudeTemperature(0,0,0.58,0.42,320);
        double polar=GeoMath.balancedLatitudeTemperature(0,90,0.58,0.42,320);
        double northernMidLatitude=GeoMath.balancedLatitudeTemperature(0,40,0.58,0.42,320);
        double southernMidLatitude=GeoMath.balancedLatitudeTemperature(0,-40,0.58,0.42,320);
        check(Double.isFinite(equatorial)&&Double.isFinite(polar),"finite climate values");
        check(equatorial>polar,"latitude must cool toward poles");
        close(northernMidLatitude,southernMidLatitude,1e-12,
                "annual-mean climate must be symmetric across hemispheres");

        System.out.println("ZONAL_LATITUDE_LONGITUDE_CLIMATE_PASS");
    }
}
