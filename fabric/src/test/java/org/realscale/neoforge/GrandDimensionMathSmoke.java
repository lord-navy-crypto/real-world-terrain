package org.realscale.neoforge;
public final class GrandDimensionMathSmoke {
    private static void check(boolean v,String msg){if(!v)throw new AssertionError(msg);}
    public static void main(String[] args){
        for(int x=-1200;x<=1200;x+=53)for(int z=-1200;z<=1200;z+=61)
            if((long)x*x+(long)z*z<=1200L*1200L)
                check(GrandDimensionMath.outerEndBlend(x,z)==0.0,"Dragon island protected");
        check(GrandDimensionMath.outerEndBlend(1700,0)==1.0,"far-End activation");
        check(GrandDimensionMath.outerEndBlend(-1700,0)==1.0,"negative X symmetry");
        check(GrandDimensionMath.outerEndBlend(0,-1700)==1.0,"negative Z symmetry");
        double last=0;
        for(int x=1200;x<=1700;x+=5){
            double n=GrandDimensionMath.outerEndBlend(x,0);
            check(n>=last&&n<=1.0,"radial continuity");last=n;
        }
        check(GrandDimensionMath.outerEndIslandDensity(190,0.9,0.1)>0,"low strata");
        check(GrandDimensionMath.outerEndIslandDensity(360,0.1,0.95)>0,"ridge-driven high strata");
        check(GrandDimensionMath.outerEndIslandDensity(360,0.7,-0.2)>0,
                "continent-driven upper strata must survive weak erosion signal");
        check(GrandDimensionMath.outerEndIslandDensity(360,-0.4,-0.4)<0,
                "distant upper End must retain open void");
        check(GrandDimensionMath.outerEndIslandDensity(500,.6,.6)<0,"upper void");
        check(GrandDimensionMath.HEIGHT==512,"Phase I dimension limit");
        System.out.println("PASS: dragon protection and multi-altitude distant End");
    }
}
