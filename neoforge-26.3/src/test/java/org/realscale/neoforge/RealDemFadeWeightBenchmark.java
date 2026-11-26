package org.realscale.neoforge;

import java.util.Arrays;

/** Cold-miss and warm-hit comparison on the exact DEM fade-weight expression.
 * This is only a microbenchmark; it does not measure overall chunk throughput.
 */
public final class RealDemFadeWeightBenchmark {
    private static final int SAMPLES=32768;
    private static final int WARMUP=4;
    private static final int TRIALS=9;
    private static final int[] X=new int[SAMPLES], Z=new int[SAMPLES], WIDTH=new int[SAMPLES];
    private static volatile double checksum;
    static {
        RealDemDataSource dem=RealDemDataSource.loadMeasured();
        int minX=dem.originX()-4096,minZ=dem.originZ()-4096;
        int spanX=dem.maxX()-dem.originX()+8193;
        int spanZ=dem.maxZ()-dem.originZ()+8193;
        int[] widths={256,768,2048,4096};
        for(int i=0;i<SAMPLES;i++) {
            X[i]=minX+Math.floorMod(i*137+33,spanX);
            Z[i]=minZ+Math.floorMod(i*193+71,spanZ);
            WIDTH[i]=widths[i&3];
        }
    }

    private static double reference() {
        double s=0.0;
        for(int i=0;i<SAMPLES;i++)
            s+=RealDemSeamlessMath.weightOutside(X[i],Z[i],WIDTH[i]);
        checksum=s;
        return s;
    }

    private static double cached() {
        double s=0.0;
        for(int i=0;i<SAMPLES;i++)
            s+=RealDemSeamlessMath.cachedWeightOutside(X[i],Z[i],WIDTH[i]);
        checksum=s;
        return s;
    }

    private static double median(long[] data) {
        Arrays.sort(data);
        return data[data.length/2]/1_000_000.0;
    }

    public static void main(String[] args) {
        for(int i=0;i<SAMPLES;i++) {
            long expected=Double.doubleToRawLongBits(
                    RealDemSeamlessMath.weightOutside(X[i],Z[i],WIDTH[i]));
            long actual=Double.doubleToRawLongBits(
                    RealDemSeamlessMath.cachedWeightOutside(X[i],Z[i],WIDTH[i]));
            if(expected!=actual)
                throw new AssertionError("Different DEM fade weight at "+
                        X[i]+","+Z[i]+", width="+WIDTH[i]);
        }
        for(int i=0;i<WARMUP;i++){reference();cached();}
        long[] baseline=new long[TRIALS],optimized=new long[TRIALS];
        for(int i=0;i<TRIALS;i++) {
            boolean firstCached=(i&1)==1;
            for(int pass=0;pass<2;pass++) {
                boolean runCached=(pass==0)==firstCached;
                long start=System.nanoTime();
                if(runCached)cached();else reference();
                (runCached?optimized:baseline)[i]=System.nanoTime()-start;
            }
        }
        double a=median(baseline),b=median(optimized);
        System.out.printf(java.util.Locale.ROOT,
                "REALSCALE_USGS_FADE_BENCHMARK samples=%d original_median_ms=%.3f "
                +"cached_median_ms=%.3f ratio=%.3fx raw_bits=identical%n",
                SAMPLES,a,b,a/b);
        if(!Double.isFinite(checksum))throw new AssertionError("Nonfinite checksum");
    }
}
