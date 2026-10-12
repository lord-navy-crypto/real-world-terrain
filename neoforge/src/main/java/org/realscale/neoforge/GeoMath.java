package org.realscale.neoforge;

/** Pure math, independent of Minecraft classes for CPU-only unit testing. */
final class GeoMath {
    private GeoMath() {}
    static double clamp(double x,double low,double high){
        return Math.max(low,Math.min(high,x));
    }
    static double ridgeErosion(double base,double ridge,double erosion,double ridgeGain,double erosionGain){
        double crest=1.0-clamp(Math.abs(ridge),0,1);
        double erosivity=clamp((erosion+1.0)*0.5,0,1);
        return base+ridgeGain*crest*crest*(1.0-erosivity*0.5)
                   -erosionGain*erosivity*erosivity;
    }
    static double smoothstep(double low,double high,double x){
        double t=clamp((x-low)/(high-low),0,1);
        return t*t*(3-2*t);
    }
    static double valleyTemperature(double base,double center,double neighborsMean,
                                    double continent,int y,double strength){
        // Only pronounced depressions relative to the surrounding terrain.
        // Warming fades near coasts/sea and above Y=256.
        double valley=smoothstep(0.04,0.36,neighborsMean-center);
        double inland=smoothstep(-0.04,0.22,continent);
        double lowerLand=1.0-smoothstep(112,256,y);
        return base+strength*valley*inland*lowerLand;
    }
    static double strata(double base,double warp,int y,double strength,int period){
        double warpOffset=clamp(warp,-1,1)*(period/6.0);
        return base+Math.sin(2.0*Math.PI*(y+warpOffset)/period)*strength;
    }
}
