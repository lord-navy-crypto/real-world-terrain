package org.realscale.neoforge;

/** Smooth land-only elevation bias, designed only for horizontal procedural
 * height signals (128 blocks per signal unit in the Natural 2096-height
 * graph). No spatial cells, no chunky boundaries, no ocean/DEM mutations.
 * Strength 0..0.45 guarantees strictly increasing output.
 * This does not claim an exactly matched global area histogram until the
 * generated world heightmap has been audited.
 */
final class LandHypsometryMath {
    private LandHypsometryMath() {}
    static final double BEGIN=0.35, FULL=1.50, FADE=5.0, END=9.0;
    static double integratedSmoothstep(double h,double a,double b) {
        if(h<=a)return 0.0;
        if(h>=b)return h-(a+b)*0.5;
        double t=(h-a)/(b-a);
        return (b-a)*(t*t*t-0.5*t*t*t*t);
    }
    static double adjust(double horizontalHeight,double strength) {
        if(!(horizontalHeight>0.0)||strength==0.0)return horizontalHeight;
        double integral=integratedSmoothstep(horizontalHeight,BEGIN,FULL)
                -integratedSmoothstep(horizontalHeight,FADE,END);
        return horizontalHeight-strength*integral;
    }
}
