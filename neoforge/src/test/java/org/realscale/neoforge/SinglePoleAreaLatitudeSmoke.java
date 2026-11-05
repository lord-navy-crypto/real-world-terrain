package org.realscale.neoforge;

/** Direct PR25 single-crest equal-area geometry regression, preserving old mode. */
public final class SinglePoleAreaLatitudeSmoke {
    private static void check(boolean condition,String reason) {
        if(!condition)throw new AssertionError(reason);
    }
    private static void close(double x,double y,double tolerance,String reason) {
        check(Math.abs(x-y)<=tolerance,reason+": "+x+" vs "+y);
    }
    public static void main(String[] args) {
        int q=8192;
        close(GeoMath.singlePoleAreaRadialLatitudeDeg(0,0,0,0,q),0,0,"origin");
        close(GeoMath.singlePoleAreaRadialLatitudeDeg(2*q,0,0,0,q),0,0,"seam");
        close(GeoMath.singlePoleAreaRadialLatitudeDeg((int)Math.round(Math.sqrt(2)*q),
                0,0,0,q),90,.025,"PR25 first pole");
        close(GeoMath.singlePoleAreaRadialLatitudeDeg(q,0,0,0,q),45,0,"quarter area");
        close(GeoMath.singlePoleAreaRadialLatitudeDeg(-q,0,0,0,q),
              GeoMath.singlePoleAreaRadialLatitudeDeg(q,0,0,0,q),0,"signed X");
        close(GeoMath.singlePoleAreaRadialLatitudeDeg(13337,0,3337,0,q),
              GeoMath.singlePoleAreaRadialLatitudeDeg(10000,0,0,0,q),0,"translated");
        // The pre-existing two-crest formula MUST stay exactly unchanged.
        close(GeoMath.areaBalancedRadialLatitudeDeg(q,0,0,0,q),90,0,
              "existing equal-area single Q peak");
        for(int cycle=0;cycle<8;cycle++) {
            double in=cycle*2.0*q,out=in+2.0*q;
            for(int t=0;t<2400;t++) {
                double frac=(t+.5)/2400.0;
                double radius=Math.sqrt(in*in+frac*(out*out-in*in));
                double got=GeoMath.singlePoleAreaRadialLatitudeDeg(
                    (int)Math.round(radius),0,0,0,q);
                double expected=90.0*(1-Math.abs(2*frac-1));
                check(Math.abs(got-expected)<.07,
                      "Not equal-area at cycle "+cycle+" sample "+t);
            }
        }
        System.out.println("PR25_SINGLE_POLE_AREA_PARITY_PASS");
    }
}
