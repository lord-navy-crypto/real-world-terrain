package org.realscale.neoforge;
/** Pure dimension-scale math, independent of game state. */
public final class GrandDimensionMath {
    private GrandDimensionMath() {}
    public static final int HEIGHT = 512;
    public static final int DRAGON_PROTECTION_RADIUS = 1200;
    public static final int DRAGON_FADE_END_RADIUS = 1700;
    public static double outerEndBlend(int x,int z) {
        double d=Math.hypot((double)x,(double)z);
        if(d<=DRAGON_PROTECTION_RADIUS)return 0.0;
        if(d>=DRAGON_FADE_END_RADIUS)return 1.0;
        double t=(d-DRAGON_PROTECTION_RADIUS)/(DRAGON_FADE_END_RADIUS-DRAGON_PROTECTION_RADIUS);
        return t*t*(3.0-2.0*t);
    }
    public static double outerEndIslandDensity(int y,double n,double m) {
        double lowerCenter=195.0+48.0*m;
        double upperCenter=356.0+28.0*n;
        double lower=(n-0.27)*1.7-Math.abs(y-lowerCenter)/65.0;
        // The old upper tier depended solely on high erosion noise (m>0.41).
        // Some valid world seeds then generated zero upper-tier islands across
        // broad distant-End regions. Combine two seeded signals instead:
        // continentalness supports stacked islands while erosion can still
        // form isolated upper islands without a lower counterpart.
        double upperSignal=Math.max((n-0.20)*1.8+0.10*m,
                                    (m-0.50)*1.1);
        double upper=upperSignal-Math.abs(y-upperCenter)/60.0;
        return Math.max(lower,upper);
    }
}
