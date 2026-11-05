package org.realscale.neoforge;

/** No-Minecraft correctness gate for equal-area annular latitude correction. */
public final class AreaBalancedClimateSmoke {
    private AreaBalancedClimateSmoke(){}
    private static void check(boolean ok,String what){if(!ok)throw new AssertionError(what);}
    public static void main(String[] args) {
        int climatePole=GeoMath.CLIMATE_POLE_DISTANCE_BLOCKS;
        check(GeoMath.EARTH_QUARTER_MERIDIAN_METERS==10_001_966,"Earth quarter meridian metres");
        check(GeoMath.CLIMATE_LATITUDE_COMPRESSION==20,"climate-only 1:20 factor");
        check(climatePole==500_098,"1:20 climate pole distance");
        check(Math.abs(GeoMath.latitudeDeg(-climatePole,0,climatePole)-90.0)<1e-9,
                "north pole at negative Z");
        check(Math.abs(GeoMath.latitudeDeg(climatePole,0,climatePole)+90.0)<1e-9,
                "south pole at positive Z");
        check(GeoMath.latitudeDeg(0,0,climatePole)==0.0,"equator at Z=0");
        check(Math.abs(GeoMath.latitudeDeg(-2*climatePole,0,climatePole))<1e-9,
                "north-side return equator");
        check(Math.abs(GeoMath.latitudeDeg(2*climatePole,0,climatePole))<1e-9,
                "south-side return equator");
        final int q=8192;
        check(GeoMath.areaBalancedRadialLatitudeDeg(0,0,0,0,q)==0,"equator");
        int[] poles={8192,24576,40960};
        // Unlike radius-linear mode, polar peaks are located by annular area
        // rather than forced to occur at radius Q.
        for(int cycle=0;cycle<5;cycle++){
            double inner=cycle*2.0*q;
            double outer=(cycle+1)*2.0*q;
            double previous=0;
            for(int i=0;i<=4096;i++){
                int r=(int)Math.round(inner+(outer-inner)*i/4096.0);
                double latitude=GeoMath.areaBalancedRadialLatitudeDeg(r,0,0,0,q);
                check(latitude>=-1e-8 && latitude<=90+1e-8,"latitude bounds");
                check(Double.isFinite(latitude),"finite latitude");
                previous=latitude;
            }
            // Four equal-area quarters must each contain 25% of samples.
            int[] bins=new int[4];
            int count=12000;
            for(int i=0;i<count;i++){
                double areaFraction=(i+0.5)/count;
                double r=Math.sqrt(inner*inner+areaFraction*(outer*outer-inner*inner));
                double lat=GeoMath.areaBalancedRadialLatitudeDeg(
                        (int)Math.round(r),0,0,0,q);
                // Derive whether the wave sits nearer equatorial or polar.
                int phase=(int)Math.floor(areaFraction*4);
                bins[phase]++;
                double target=phase==0?90*(areaFraction*4):
                    phase==1?90*(2-areaFraction*4):
                    phase==2?90*(areaFraction*4-2):
                        90*(4-areaFraction*4);
                check(Math.abs(lat-target)<0.07,
                        "Expected area-balanced geography; cycle="+cycle);
            }
            for(int n:bins)check(n==3000,"unequal annulus area quarters");
            check(Math.abs(GeoMath.areaBalancedRadialLatitudeDeg(
                    (int)outer,0,0,0,q))<0.01,"period edge continuity");
        }
        check(GeoMath.areaBalancedRadialLatitudeDeg(-1000,0,0,0,q)==
              GeoMath.areaBalancedRadialLatitudeDeg(1000,0,0,0,q),"symmetry");
        check(GeoMath.balancedRadialLatitudeDeg(8192,0,0,0,q)==90.0,
                "legacy balanced geometry must remain unchanged");
        check(GeoMath.climateBudget(0,-2,0.66,0.34)==-0.66,"cooling budget");
        check(GeoMath.climateBudget(0,2,0.66,0.34)==0.34,"warming budget");
        System.out.println("TVA_AREA_BALANCED_CLIMATE_PASS");
    }
}
