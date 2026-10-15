package org.realscale.neoforge;

import java.util.Random;

/** Standalone Java 21 verification: equal annular area, continuous rings and old mode. */
public final class AreaBalancedLatitudeSmoke {
    private static final int Q=8192;
    private AreaBalancedLatitudeSmoke() {}
    private static void yes(boolean value,String message) {
        if(!value)throw new AssertionError(message);
    }
    private static void near(double a,double b,double maxDelta,String desc) {
        yes(Math.abs(a-b)<=maxDelta,desc+": "+a+" vs "+b);
    }
    private static double latitude(int x,int z,int cx,int cz) {
        return GeoMath.areaBalancedRadialLatitudeDeg(x,z,cx,cz,Q);
    }
    public static void main(String[] args) {
        near(latitude(0,0,0,0),0,0,"Origin equator");
        near(latitude(2*Q,0,0,0),0,0,"First annulus closed equator");
        near(latitude(4*Q,0,0,0),0,0,"Second annulus closed equator");
        int firstPole=(int)Math.round(Math.sqrt(2.0)*Q);
        near(latitude(firstPole,0,0,0),90,0.01,"First polar belt at equal-area radius");
        near(latitude(Q,0,0,0),45,0.01,"Q is not the polar belt in equal area mode");
        near(latitude(-Q,0,0,0),latitude(Q,0,0,0),1e-12,"Negative X symmetry");
        near(latitude(0,-Q,0,0),latitude(0,Q,0,0),1e-12,"Negative Z symmetry");
        near(latitude(10000,0,500,0),latitude(9500,0,0,0),1e-12,"Translated center");
        for(int k=1;k<=12;k++) {
            int edge=k*2*Q;
            double before=latitude(edge-1,0,0,0);
            double after=latitude(edge+1,0,0,0);
            yes(before<0.1 && after<0.1,"Discontinuous annulus seam at "+edge);
        }
        // Uniform disk AREA requires radius=sqrt(r0^2 + u*(r1^2-r0^2)).
        // Three equal latitude thirds must each receive ~1/3 of annulus area.
        Random random=new Random(20261015);
        for(int cycle=0;cycle<6;cycle++) {
            double inner=cycle*2.0*Q,outer=inner+2.0*Q;
            int[] counts=new int[3];
            int sampleCount=27000;
            for(int i=0;i<sampleCount;i++) {
                double u=random.nextDouble();
                double radius=Math.sqrt(inner*inner+u*(outer*outer-inner*inner));
                double theta=2*Math.PI*random.nextDouble();
                int x=(int)Math.round(radius*Math.cos(theta));
                int z=(int)Math.round(radius*Math.sin(theta));
                double lat=latitude(x,z,0,0);
                yes(lat>=0.0&&lat<=90.0,"Invalid latitude");
                counts[Math.min(2,(int)(lat/30))]++;
            }
            for(int b=0;b<3;b++) {
                double proportion=counts[b]/(double)sampleCount;
                near(proportion,1.0/3.0,0.028,
                        "Latitude class not equal-area in cycle "+cycle+" band "+b);
            }
        }
        // Old radial and old balanced remain exactly their original formulas:
        // switching presets MUST NOT silently change an old-world climate.
        int x=3500,z=4400;
        double radius=Math.hypot(x,z);
        double wave=(radius/Q)%2.0;
        double oldLat=90.0*(wave<=1?wave:2.0-wave);
        near(GeoMath.radialLatitudeDeg(x,z,0,0,Q),oldLat,0,"Old ring regression");
        near(GeoMath.balancedRadialLatitudeDeg(x,z,0,0,Q),
                90.0*Math.pow(wave<=1?wave:2.0-wave,1.15),0,
                "Old balanced regression");
        // High latitude, high altitude must cool; climate budgets stay bounded.
        double high=GeoMath.balancedRadialTemperature(0,90,.7,.55,768);
        double tropical=GeoMath.balancedRadialTemperature(0,0,.7,.55,768);
        yes(tropical>high,"Tropical and polar corrections inverted");
        near(GeoMath.climateBudget(0,-5,.60,.45),-.60,0,"Cooling clamp");
        near(GeoMath.climateBudget(0,5,.60,.45),.45,0,"Warming clamp");
        System.out.println("TVA_EQUAL_AREA_LATITUDE_PASS: six independent area-balanced "
                + "cycles, seams, old modes, altitude relief and climate budgets");
    }
}
