package org.realscale.neoforge;

/**
 * Pure Java USGS DEM interpolation and transition mathematics.
 * Independently JUnit-testable without Minecraft/NeoForge on test classpath.
 * Production DensityFunction delegates to exactly these same computations.
 */
final class RealDemSeamlessMath {
    private static final RealDemTile DEM = RealDemTile.load();
    private static final double CELL = 32.0;
    private static final int GRID = 16;
    private static final double[][] HEIGHTS = loadHeights();

    /*
     * Minecraft re-evaluates final_density at many Y cells for each X/Z.
     * Bicubic DEM interpolation depends ONLY on X/Z. Avoid repeating four
     * spline evaluations for each of ~2096 vertical sample heights.
     *
     * Fixed-size direct-mapped per-thread cache: no unbounded world state,
     * no synchronization on multi-threaded worldgen, no seed dependence.
     * Hash collisions can only lower hit rate, not alter a computed value.
     */
    private static final int CACHE_SIZE = 256;
    private static final class HeightCache {
        final int[] xs = new int[CACHE_SIZE];
        final int[] zs = new int[CACHE_SIZE];
        final double[] values = new double[CACHE_SIZE];
        final boolean[] valid = new boolean[CACHE_SIZE];
    }
    private static final ThreadLocal<HeightCache> HEIGHT_CACHE =
        ThreadLocal.withInitial(HeightCache::new);

    static double cachedExtendedHeight(int x, int z) {
        HeightCache cache = HEIGHT_CACHE.get();
        int slot = (x*0x9e3779b9 ^ Integer.rotateLeft(z*0x85ebca6b,13))
                   & (CACHE_SIZE-1);
        if (cache.valid[slot] && cache.xs[slot]==x && cache.zs[slot]==z)
            return cache.values[slot];
        double height=extendedHeight(x,z);
        cache.valid[slot]=true;
        cache.xs[slot]=x;
        cache.zs[slot]=z;
        cache.values[slot]=height;
        return height;
    }

    private RealDemSeamlessMath() {}

    private static double[][] loadHeights() {
        double[][] h = new double[GRID][GRID];
        for (int z=0; z<GRID; z++)
            for (int x=0; x<GRID; x++)
                h[z][x] = DEM.worldSolidYAt(
                    DEM.originX()+CELL*x, DEM.originZ()+CELL*z);
        return h;
    }

    private static double limit(double value,double low,double high) {
        return Math.max(low,Math.min(high,value));
    }

    private static double monotoneSlope(double left,double right) {
        if (left*right<=0.0) return 0.0;
        return 2.0*left*right/(left+right);
    }

    private static double smoothSegment(double prev,double a,double b,
                                         double next,double t) {
        double d=b-a;
        if (d==0.0) return a;
        double before=a-prev;
        double after=next-b;
        double m0=monotoneSlope(before,d);
        double m1=monotoneSlope(d,after);
        double t2=t*t,t3=t2*t;
        double result=(2*t3-3*t2+1)*a+(t3-2*t2+t)*m0
                     +(-2*t3+3*t2)*b+(t3-t2)*m1;
        return limit(result,Math.min(a,b),Math.max(a,b));
    }

    private static double corner(int row,int col) {
        return HEIGHTS[(int)limit(row,0,GRID-1)][(int)limit(col,0,GRID-1)];
    }

    private static double sampleRow(int row,int ix,double a) {
        double p0=corner(row,ix),p1=corner(row,ix+1);
        double pm=ix>0?corner(row,ix-1):2*p0-p1;
        double pp=ix<GRID-2?corner(row,ix+2):2*p1-p0;
        return smoothSegment(pm,p0,p1,pp,a);
    }

    static double measuredHeight(double x,double z) {
        double cx=limit(x,DEM.originX(),DEM.maxX());
        double cz=limit(z,DEM.originZ(),DEM.maxZ());
        double gx=(cx-DEM.originX())/CELL, gz=(cz-DEM.originZ())/CELL;
        int ix=Math.min(GRID-2,(int)Math.floor(gx));
        int iz=Math.min(GRID-2,(int)Math.floor(gz));
        double a=gx-ix,b=gz-iz;
        double r0=sampleRow(iz-1,ix,a);
        double r1=sampleRow(iz,ix,a);
        double r2=sampleRow(iz+1,ix,a);
        double r3=sampleRow(iz+2,ix,a);
        double lower=iz>0?r0:2*r1-r2;
        double upper=iz<GRID-2?r3:2*r2-r1;
        return smoothSegment(lower,r1,r2,upper,b);
    }

    static double weightOutside(double x,double z,int fadeMeters) {
        double dx=x-limit(x,DEM.originX(),DEM.maxX());
        double dz=z-limit(z,DEM.originZ(),DEM.maxZ());
        double t=limit(Math.hypot(dx,dz)/fadeMeters,0.0,1.0);
        return 1.0-t*t*(3.0-2.0*t);
    }

    static double taperedOutsideDisplacement(double distance) {
        double length=Math.min(Math.abs(distance),128.0);
        double softened=length-length*length/256.0;
        return Math.copySign(softened,distance);
    }

    static double extendedHeight(double x,double z) {
        double cx=limit(x,DEM.originX(),DEM.maxX());
        double cz=limit(z,DEM.originZ(),DEM.maxZ());
        double center=measuredHeight(cx,cz);
        double originalCenter=center;
        double dx=x-cx,dz=z-cz;
        if(dx!=0.0) {
            double neighbor=measuredHeight(cx+(dx<0?CELL:-CELL),cz);
            double slope=(neighbor-center)/(dx<0?CELL:-CELL);
            center+=limit(slope,-1.25,1.25)*taperedOutsideDisplacement(dx);
        }
        if(dz!=0.0) {
            double neighbor=measuredHeight(cx,cz+(dz<0?CELL:-CELL));
            // Keep Z gradient independent of the X-axis extrapolation.
            double slope=(neighbor-originalCenter)/(dz<0?CELL:-CELL);
            center+=limit(slope,-1.25,1.25)*taperedOutsideDisplacement(dz);
        }
        return limit(center,-64.0,2031.0);
    }

}
