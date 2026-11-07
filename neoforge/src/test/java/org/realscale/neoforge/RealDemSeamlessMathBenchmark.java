package org.realscale.neoforge;

import java.lang.reflect.Field;
import java.util.Arrays;

/**
 * Same-process microbenchmark for the original and optimized USGS bicubic
 * interpolation paths. Every sample is raw-bit checked before timing.
 * This measures interpolation only, not full chunk generation or FPS.
 */
public final class RealDemSeamlessMathBenchmark {
    private static final int SAMPLES=32_768;
    private static final int WARMUP=3;
    private static final int TRIALS=7;
    private static volatile double sink;

    private static final RealDemDataSource DEM;
    private static final double CELL;
    private static final int GRID;
    private static final double[][] HEIGHTS;
    private static final int[] XS=new int[SAMPLES];
    private static final int[] ZS=new int[SAMPLES];

    static {
        try {
            Field demField=RealDemSeamlessMath.class.getDeclaredField("DEM");
            Field cellField=RealDemSeamlessMath.class.getDeclaredField("CELL");
            Field gridField=RealDemSeamlessMath.class.getDeclaredField("GRID");
            Field heightsField=RealDemSeamlessMath.class.getDeclaredField("HEIGHTS");
            for(Field field:new Field[]{demField,cellField,gridField,heightsField})
                field.setAccessible(true);
            DEM=(RealDemDataSource)demField.get(null);
            CELL=cellField.getDouble(null);
            GRID=gridField.getInt(null);
            HEIGHTS=(double[][])heightsField.get(null);
        } catch(ReflectiveOperationException ex) {
            throw new ExceptionInInitializerError(ex);
        }
        int minX=DEM.originX()-64,maxX=DEM.maxX()+64;
        int minZ=DEM.originZ()-64,maxZ=DEM.maxZ()+64;
        int width=maxX-minX+1,depth=maxZ-minZ+1;
        for(int i=0;i<SAMPLES;i++) {
            // Deterministic, interleaved X/Z order crosses all four DEM edges
            // and the short exterior clamp region without favoring one row.
            XS[i]=minX+Math.floorMod(i*137+17,width);
            ZS[i]=minZ+Math.floorMod(i*193+29,depth);
        }
    }

    private static double limit(double value,double low,double high) {
        return Math.max(low,Math.min(high,value));
    }

    private static double corner(int row,int col) {
        return HEIGHTS[(int)limit(row,0,GRID-1)]
                      [(int)limit(col,0,GRID-1)];
    }

    private static double monotoneSlope(double left,double right) {
        if(left*right<=0.0)return 0.0;
        return 2.0*left*right/(left+right);
    }

    private static double smoothSegment(double prev,double a,double b,
            double next,double t) {
        double d=b-a;
        if(d==0.0)return a;
        double before=a-prev;
        double after=next-b;
        double m0=monotoneSlope(before,d);
        double m1=monotoneSlope(d,after);
        double t2=t*t,t3=t2*t;
        double result=(2*t3-3*t2+1)*a+(t3-2*t2+t)*m0
                +(-2*t3+3*t2)*b+(t3-t2)*m1;
        return limit(result,Math.min(a,b),Math.max(a,b));
    }

    /** Frozen original implementation with repeated corner clamps. */
    private static double originalRow(int row,int ix,double a) {
        double p0=corner(row,ix),p1=corner(row,ix+1);
        double pm=ix>0?corner(row,ix-1):2*p0-p1;
        double pp=ix<GRID-2?corner(row,ix+2):2*p1-p0;
        return smoothSegment(pm,p0,p1,pp,a);
    }

    /** Frozen original implementation that always evaluates all four rows. */
    private static double originalHeight(double x,double z) {
        double cx=limit(x,DEM.originX(),DEM.maxX());
        double cz=limit(z,DEM.originZ(),DEM.maxZ());
        double gx=(cx-DEM.originX())/CELL,gz=(cz-DEM.originZ())/CELL;
        int ix=Math.min(GRID-2,(int)Math.floor(gx));
        int iz=Math.min(GRID-2,(int)Math.floor(gz));
        double a=gx-ix,b=gz-iz;
        double r0=originalRow(iz-1,ix,a);
        double r1=originalRow(iz,ix,a);
        double r2=originalRow(iz+1,ix,a);
        double r3=originalRow(iz+2,ix,a);
        double lower=iz>0?r0:2*r1-r2;
        double upper=iz<GRID-2?r3:2*r2-r1;
        return smoothSegment(lower,r1,r2,upper,b);
    }

    private static double runOriginal() {
        double sum=0.0;
        for(int i=0;i<SAMPLES;i++)sum+=originalHeight(XS[i],ZS[i]);
        sink=sum;
        return sum;
    }

    private static double runOptimized() {
        double sum=0.0;
        for(int i=0;i<SAMPLES;i++)
            sum+=RealDemSeamlessMath.measuredHeight(XS[i],ZS[i]);
        sink=sum;
        return sum;
    }

    private static double median(long[] values) {
        Arrays.sort(values);
        return values[values.length/2]/1_000_000.0;
    }

    public static void main(String[] args) {
        for(int i=0;i<SAMPLES;i++) {
            long expected=Double.doubleToRawLongBits(originalHeight(XS[i],ZS[i]));
            long actual=Double.doubleToRawLongBits(
                    RealDemSeamlessMath.measuredHeight(XS[i],ZS[i]));
            if(expected!=actual)
                throw new AssertionError("Interpolation bits differ at "
                        +XS[i]+","+ZS[i]);
        }
        for(int i=0;i<WARMUP;i++) {
            runOriginal();
            runOptimized();
        }
        long[] original=new long[TRIALS],optimized=new long[TRIALS];
        for(int trial=0;trial<TRIALS;trial++) {
            boolean optimizedFirst=(trial&1)==1;
            for(int pass=0;pass<2;pass++) {
                boolean useOptimized=(pass==0)==optimizedFirst;
                long start=System.nanoTime();
                if(useOptimized)runOptimized(); else runOriginal();
                long elapsed=System.nanoTime()-start;
                (useOptimized?optimized:original)[trial]=elapsed;
            }
        }
        double base=median(original),fast=median(optimized);
        System.out.printf(java.util.Locale.ROOT,
                "REALSCALE_USGS_INTERPOLATION_BENCHMARK samples=%d "
                +"original_median_ms=%.3f optimized_median_ms=%.3f "
                +"speedup=%.3fx raw_bits=identical%n",
                SAMPLES,base,fast,base/fast);
        if(Double.isNaN(sink))throw new AssertionError("Lost benchmark checksum");
    }
}
