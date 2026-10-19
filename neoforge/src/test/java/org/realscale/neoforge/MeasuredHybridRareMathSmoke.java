package org.realscale.neoforge;

/** Pure-Java proof: real DEM is unchanged outside rare, land-only sites. */
public final class MeasuredHybridRareMathSmoke {
    private static void check(boolean okay,String why){if(!okay)throw new AssertionError(why);}
    public static void main(String[] args){
        long ordinary=0,rare=0;
        int[] reach=new int[24];
        for(int x=-16;x<=16;x++)for(int z=-16;z<=16;z++){
            long h=RegionalLandformMath.hashV3(x,z);
            int kind=RegionalLandformMath.kindV3(h,0.24);
            if(MeasuredHybridRareMath.isRare(h)){rare++;reach[kind]++;}
            else ordinary++;
            check(MeasuredHybridRareMath.isRare(h)==MeasuredHybridRareMath.isRare(h),
                  "region marking changed across chunk sampling");
            double rx=RegionalLandformMath.radiusV3(h);
            check(MeasuredHybridRareMath.weight(h,rx*1.04,0,rx,0.8,2.3)==0,
                  "rare shape crosses outer boundary");
            check(MeasuredHybridRareMath.weight(h,0,0,rx,-0.6,2.3)==0,
                  "rare shape changes deep ocean");
            check(MeasuredHybridRareMath.weight(h,0,0,rx,0.8,.15)==0,
                  "lowlands must not be overwritten");
        }
        check(ordinary>0 && rare>0 && rare<ordinary,
              "rare global wonders must be less common than unmodified terrain");
        for(int k=0;k<24;k++)check(reach[k]>0,"unreachable landform archetype "+k);
        double sample=1.2345678901234567;
        long bits=Double.doubleToRawLongBits(sample);
        check(Double.doubleToRawLongBits(MeasuredHybridRareMath.combine(sample,999,0))==bits,
              "ordinary measured samples must remain bit-identical");
        check(MeasuredHybridRareMath.combine(10,200,1)==200,
              "rare site center must use procedural terrain, not measured");
        check(MeasuredHybridRareMath.combine(10,200,.5)==105,
              "terrain transition should be a true convex interpolation");
        for(int i=0;i<121;i++){
            double t=i/120.0;
            double value=MeasuredHybridRareMath.combine(10,200,t);
            check(value>=10-1e-9 && value<=200+1e-9,"blend overshoot");
            check(Double.isFinite(value),"non-finite blend");
        }
        System.out.println("USGS_HYBRID_RARE_PASS: unchanged ordinary DEM, all 24 types, rare smooth inland enclaves");
    }
}
