package org.realscale.neoforge;

/**
 * Pure Java land and distance gates for fitted USGS terrain V2.
 * For ordinary continental interior (continentalness >= 0.18), the original
 * procedural terrain contributes EXACTLY ZERO beyond 1536m from measured DEM.
 * The coastal transition preserves ocean floors and river-to-sea shores.
 * A quintic interpolation makes both ends flat in derivative and curvature.
 */
final class RealDemFittedFullMathV2 {
    static final double BLEND_DISTANCE=1536.0;
    private RealDemFittedFullMathV2() {}
    static double clamp01(double v) {return Math.max(0.0, Math.min(1.0,v));}
    static double fade(double t) {
        t=clamp01(t);
        return t*t*t*(t*(t*6.0-15.0)+10.0);
    }
    static double landWeight(double continentalness) {
        if(continentalness<=-0.15)return 0.0;
        if(continentalness>=0.18)return 1.0; // exact 100%, no float sliver
        return fade((continentalness+0.15)/0.33);
    }
    static double demTransitionWeight(double distance) {
        if(distance<=0.0)return 0.0;
        if(distance>=BLEND_DISTANCE)return 1.0; // no generic tail
        return fade(distance/BLEND_DISTANCE);
    }
    static double fittedWeight(double distance,double continentalness) {
        return demTransitionWeight(distance)*landWeight(continentalness);
    }
    static double blendDensity(double base,double fitted,double distance,double continentalness) {
        double w=fittedWeight(distance,continentalness);
        if(w<=0.0)return base;
        if(w>=1.0)return fitted; // pure fitted, not a rounded approximation!
        return Math.fma(fitted-base,w,base);
    }
}
