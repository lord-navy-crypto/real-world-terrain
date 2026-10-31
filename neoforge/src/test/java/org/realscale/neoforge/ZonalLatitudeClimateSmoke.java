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
        final int quarterMeridianMeters=10_001_966;
        final int horizontalScale=20;
        final int poleDistanceBlocks=(int)Math.round(
                quarterMeridianMeters/(double)horizontalScale);

        close(GeoMath.scaleHorizontalCoordinate(12345,horizontalScale),246900,0,
                "X horizontal 1:20 scale");
        close(GeoMath.scaleHorizontalCoordinate(-12345,horizontalScale),-246900,0,
                "Z horizontal 1:20 scale");

        close(GeoMath.latitudeDeg(0,0,quarterMeridianMeters),0,0,"initial equator");
        close(GeoMath.latitudeDeg(-poleDistanceBlocks*horizontalScale,0,quarterMeridianMeters),
                90,0.001,"north pole");
        close(GeoMath.latitudeDeg(-2*poleDistanceBlocks*horizontalScale,0,quarterMeridianMeters),
                0,0.001,"opposite equator");
        close(GeoMath.latitudeDeg(-3*poleDistanceBlocks*horizontalScale,0,quarterMeridianMeters),
                -90,0.001,"south pole");
        close(GeoMath.latitudeDeg(-4*poleDistanceBlocks*horizontalScale,0,quarterMeridianMeters),
                0,0.001,"return to original equator");

        close(GeoMath.latitudeDeg(0,4_027_458,quarterMeridianMeters),36.24,0.001,
                "Big Sur nominal latitude anchor");

        // The old concentric algorithms remain available as opt-in experiments.
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

        System.out.println("ZONAL_PERIODIC_1_TO_20_CLIMATE_PASS");
    }
}
