package org.realscale.neoforge;

import java.util.Arrays;

/** Paired benchmark for the former two-cache and new one-cache DEM column paths. */
public final class RealDemColumnSampleBenchmark {
    private static final int COLUMNS=1024,VERTICAL=64,SAMPLES=COLUMNS*VERTICAL;
    private static final int FADE=2048;
    private static final long BASE_BITS=Double.doubleToRawLongBits(0.125);
    private static volatile long sink;
    private RealDemColumnSampleBenchmark() {}

    private static final int[] XS=new int[SAMPLES],ZS=new int[SAMPLES],YS=new int[SAMPLES];
    static {
        int i=0;
        for(int c=0;c<COLUMNS;c++) {
            // A 512x512 window in the actual 2048m DEM transition ring:
            // every column has a non-zero fade weight, matching the hot path.
            int x=800+(c%32)*16;
            int z=800+(c/32)*16;
            for(int y=0;y<VERTICAL;y++) {
                XS[i]=x;ZS[i]=z;YS[i]=-64+y*32;i++;
            }
        }
    }

    private static double separate(int x,int z,int y) {
        double w=RealDemSeamlessMath.cachedWeightOutside(x,z,FADE);
        if(w<=0.0)return Double.longBitsToDouble(BASE_BITS);
        double solid=(RealDemSeamlessMath.cachedExtendedHeight(x,z)-y)/128.0;
        if(w>=1.0)return solid;
        return solid*w+Double.longBitsToDouble(BASE_BITS)*(1.0-w);
    }

    private static double combined(int x,int z,int y) {
        RealDemSeamlessMath.ColumnSample sample=
                RealDemSeamlessMath.cachedColumnSample(x,z,FADE);
        double w=sample.weight;
        if(w<=0.0)return Double.longBitsToDouble(BASE_BITS);
        double solid=(sample.height-y)/128.0;
        if(w>=1.0)return solid;
        return solid*w+Double.longBitsToDouble(BASE_BITS)*(1.0-w);
    }

    private static void verify() {
        for(int i=0;i<SAMPLES;i++) {
            if(RealDemSeamlessMath.cachedWeightOutside(XS[i],ZS[i],FADE)<=0.0)
                throw new AssertionError("Benchmark sample escaped the DEM transition ring");
            long a=Double.doubleToRawLongBits(separate(XS[i],ZS[i],YS[i]));
            long b=Double.doubleToRawLongBits(combined(XS[i],ZS[i],YS[i]));
            if(a!=b)throw new AssertionError("DEM column sample raw-bit mismatch at "+i);
        }
    }

    private static long separateRun() {
        long bits=0;
        for(int i=0;i<SAMPLES;i++)
            bits^=Double.doubleToRawLongBits(separate(XS[i],ZS[i],YS[i]));
        return bits;
    }
    private static long combinedRun() {
        long bits=0;
        for(int i=0;i<SAMPLES;i++)
            bits^=Double.doubleToRawLongBits(combined(XS[i],ZS[i],YS[i]));
        return bits;
    }
    private static double median(long[] samples) {
        Arrays.sort(samples);
        return samples[samples.length/2]/1_000_000.0;
    }

    public static void main(String[] args) {
        verify();
        for(int i=0;i<3;i++){sink^=separateRun();sink^=combinedRun();}
        long[] old=new long[7],one=new long[7];
        for(int i=0;i<7;i++) {
            long start=System.nanoTime();sink^=separateRun();old[i]=System.nanoTime()-start;
            start=System.nanoTime();sink^=combinedRun();one[i]=System.nanoTime()-start;
        }
        double oldMs=median(old),newMs=median(one);
        System.out.printf(java.util.Locale.ROOT,
                "REALSCALE_DEM_COLUMN_CACHE_BENCHMARK samples=%d separate_median_ms=%.3f "
                +"combined_median_ms=%.3f speedup=%.3fx raw_bits=identical%n",
                SAMPLES,oldMs,newMs,oldMs/newMs);
        if(sink==Long.MIN_VALUE)System.out.println(sink);
    }
}
