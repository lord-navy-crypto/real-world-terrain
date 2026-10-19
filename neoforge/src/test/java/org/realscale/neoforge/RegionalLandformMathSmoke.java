package org.realscale.neoforge;

public final class RegionalLandformMathSmoke {
    private static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    public static void main(String[] args) {
        double[] centers=new double[RegionalLandformMath.KINDS];
        for(int k=0;k<centers.length;k++) {
            double middle=RegionalLandformMath.apply(k,2.2,0,0,1400,0.6);
            centers[k]=middle;
            check(Double.isFinite(middle),"nonfinite center for kind "+k);
            for(int j=0;j<120;j++){
                double phi=j*Math.PI*2/120;
                double x=Math.cos(phi)*1400*1.08,z=Math.sin(phi)*1400*1.08;
                double value=RegionalLandformMath.apply(k,2.2,x,z,1400,0.6);
                check(Math.abs(value-2.2)<1e-8,"boundary discontinuity "+k);
            }
        }
        check(centers[0]>3.0,"Tibetan plateau must actually rise");
        check(centers[4]<2.2,"Sichuan basin must actually deepen");
        check(centers[7]<2.2,"Yungui basin must have a valley floor");
        check(RegionalLandformMath.apply(0,2.3,0,0,1400,-0.4)==2.3,
                "ocean continent must be untouched");
        check(RegionalLandformMath.apply(0,0.2,0,0,1400,0.9)==0.2,
                "sea level must be untouched");
        check(RegionalLandformMath.hash(-1,5)==RegionalLandformMath.hash(-1,5),
                "negative-grid hash is unstable");
        for(int gx=-5;gx<=5;gx++)for(int gz=-5;gz<=5;gz++){
            long h=RegionalLandformMath.hash(gx,gz);
            double centerX=RegionalLandformMath.centerX(gx,h);
            double centerZ=RegionalLandformMath.centerZ(gz,h);
            double radius=RegionalLandformMath.radius(h);
            double left=(double)gx*RegionalLandformMath.CELL;
            double top=(double)gz*RegionalLandformMath.CELL;
            check(centerX-radius*1.08>left && centerX+radius*1.08<left+RegionalLandformMath.CELL,
                    "cell X seam");
            check(centerZ-radius*1.08>top && centerZ+radius*1.08<top+RegionalLandformMath.CELL,
                    "cell Z seam");
            int kind=RegionalLandformMath.kind(h,0.32);
            check(kind>=0&&kind<RegionalLandformMath.KINDS,"kind outside range");
        }
        System.out.println("REGIONAL_LANDFORM_MATH_PASS: 8 archetypes, basin/plateau/ocean and 4096-block seams");
    }
}
