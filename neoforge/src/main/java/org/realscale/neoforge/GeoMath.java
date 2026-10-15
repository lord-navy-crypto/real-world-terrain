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

    /**
     * Continuous polar triangle wave from signed world Z, in degrees.
     * Equator (phase 0), north pole (1), equator (2), south pole (3),
     * equator (4), then repeats; works both for north (-Z) and south (+Z).
     * Uses doubles before modulo to avoid integer overflow at large coords.
     */
    static double latitudeDeg(int z,int equatorZ,int poleDistance){
        double phase=((double)equatorZ-(double)z)/poleDistance;
        phase=phase%4.0;
        if(phase<0)phase+=4;
        if(phase<=1.0)return 90*phase;
        if(phase<=3.0)return 90*(2-phase);
        return 90*(phase-4);
    }
    /** Repeating concentric climate belts around a fixed center.
     * Equator 0 degrees at center, pole at one radius, next equator at 2 radii.
     * North/south meridian labels are deliberately not assigned to rings.
     * double arithmetic avoids integer overflow at large Minecraft coords.
     */
    static double radialLatitudeDeg(int x,int z,int centerX,int centerZ,int poleDistance){
        double radius=Math.hypot((double)x-centerX,(double)z-centerZ);
        double phase=(radius/poleDistance)%2.0;
        return 90.0*(phase<=1.0?phase:2.0-phase);
    }
    static double balancedRadialLatitudeDeg(int x,int z,int cx,int cz,int poleDistance){
        double radius=Math.hypot((double)x-cx,(double)z-cz);
        double phase=(radius/poleDistance)%2.0;
        double outward=phase<=1.0?phase:2.0-phase;
        return 90.0*Math.pow(outward,1.15);
    }
    /**
     * Equal-area radial climate cycle. A ring's area grows with radius, so a
     * linear radius->latitude triangle unfairly concentrates cold climates
     * in the outer annulus. Partition each 2Q-wide full period into four
     * equal-area annuli: equator -> pole -> equator -> pole -> equator.
     *
     * The squared-radius phase gives each of the four temperature quarters
     * equal disk area. Repeated cycles stay continuous (including at 2Q)
     * and cover the same concentric geography without abrupt blocky bands.
     */
    static double areaBalancedRadialLatitudeDeg(int x,int z,int cx,int cz,int poleDistance) {
        double radius=Math.hypot((double)x-cx,(double)z-cz);
        double radiusPhase=radius/(2.0*poleDistance);
        double areaPhase=(radiusPhase*radiusPhase)%1.0;
        double quarter=4.0*areaPhase;
        if(quarter<=1.0)return 90.0*quarter;
        if(quarter<=2.0)return 90.0*(2.0-quarter);
        if(quarter<=3.0)return 90.0*(quarter-2.0);
        return 90.0*(4.0-quarter);
    }
    static double balancedRadialTemperature(double original,double latitude,double strength,
                                            double altitudeCooling,int y){
        double baseline=latitudeTemperature(original,latitude,strength);
        double tropical=1.0-smoothstep(12.0,37.0,latitude);
        double temperate=smoothstep(18.0,38.0,latitude)
                         *(1.0-smoothstep(47.0,67.0,latitude));
        double altitude=smoothstep(96.0,320.0,y);
        // The tropics and temperate belt are both active: distribute a small
        // additional warm/temperate climate signal smoothly, not by hard
        // selecting biomes. Polar (>67 deg) latitude is left unmodified.
        double latitudeBuffer=strength*(0.06*tropical+0.13*temperate);
        // Relative relief proxy: low-latitude highlands receive more cooling
        // relief than mid-latitude highlands. Peaks can still freeze.
        double coolingRefund=altitudeCooling*altitude
                             *(0.75*tropical+0.40*temperate);
        return baseline+latitudeBuffer+coolingRefund;
    }
    static double climateBudget(double original,double adjusted,double maxCooling,double maxWarming){
        double d=adjusted-original;
        return original+clamp(d,-maxCooling,maxWarming);
    }
    static double latitudeTemperature(double original,double latDeg,double strength){
        // Equator modestly warmer; poles markedly colder, with a smooth
        // derivative at both the equator and poles.
        double polar=Math.sin(Math.toRadians(Math.abs(latDeg)));
        double adjustment=.22-.90*Math.pow(Math.max(0,polar),1.35);
        return original+strength*adjustment;
    }
    static double basinTemperature(double original,double center,double[] neighbors,
                                   double continent,int y,double strength){
        if(neighbors.length==0 || strength==0)return original;
        double nearestRim=Double.POSITIVE_INFINITY;
        for(double neighbor:neighbors)nearestRim=Math.min(nearestRim,neighbor-center);
        // All sampled surrounding directions must stand above the basin:
        // this rejects open valleys and isolated low ridgeline shoulders.
        double enclosure=smoothstep(.04,.28,nearestRim);
        double inland=smoothstep(-.03,.25,continent);
        // Excludes deep underground biome placement, without assuming that
        // RealScale surface valleys are below Y=256 (many are above Y=500).
        double nearSurface=smoothstep(48,80,y);
        return original+strength*enclosure*inland*nearSurface;
    }
    static double strata(double base,double warp,int y,double strength,int period){
        double warpOffset=clamp(warp,-1,1)*(period/6.0);
        return base+Math.sin(2.0*Math.PI*(y+warpOffset)/period)*strength;
    }
}
