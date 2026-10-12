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

    static double extendedHeight(double x,double z) {
        double cx=limit(x,DEM.originX(),DEM.maxX());
        double cz=limit(z,DEM.originZ(),DEM.maxZ());
        double center=measuredHeight(cx,cz);
        double dx=x-cx,dz=z-cz;
        if(dx!=0.0) {
            double neighbor=measuredHeight(cx+(dx<0?CELL:-CELL),cz);
            double slope=(neighbor-center)/(dx<0?CELL:-CELL);
            center+=limit(slope,-1.25,1.25)*limit(dx,-128,128);
        }
        if(dz!=0.0) {
            double neighbor=measuredHeight(cx,cz+(dz<0?CELL:-CELL));
            double slope=(neighbor-center)/(dz<0?CELL:-CELL);
            center+=limit(slope,-1.25,1.25)*limit(dz,-128,128);
        }
        return limit(center,-64.0,2031.0);
    }

}
