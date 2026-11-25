package org.realscale.neoforge;

import java.util.Arrays;

/** Measures only source-corner extrapolation, with original math kept here. */
public final class RealDemCornerBenchmark {
    private static final RealDemDataSource DEM=RealDemDataSource.loadMeasured();
    private static final int COUNT=32768;
    private static final int RUNS=9;
    private static final int[] X=new int[COUNT],Z=new int[COUNT];
    private static volatile double sink;

    static {
        RealDemDataSource source=DEM;
        for(int i=0;i<COUNT;i++){
            boolean lowX=(i&1)==0,lowZ=(i&2)==0;
            int bx=lowX?source.originX():source.maxX();
            int bz=lowZ?source.originZ():source.maxZ();
            int dx=1+Math.floorMod(i*73+7,1300);
            int dz=1+Math.floorMod(i*107+23,1300);
            X[i]=bx+(lowX?-dx:dx);
            Z[i]=bz+(lowZ?-dz:dz);
        }
    }

    private static double clamp(double a,double lo,double hi){
        return Math.max(lo,Math.min(hi,a));
    }

    /** Frozen original implementation: recomputes 3 bicubic samples every call. */
    private static double original(double x,double z) {
        RealDemDataSource source=DEM;
        double cell=source.cellMeters();
        double cx=clamp(x,source.originX(),source.maxX());
        double cz=clamp(z,source.originZ(),source.maxZ());
        double center=RealDemSeamlessMath.measuredHeight(cx,cz);
        double originalCenter=center;
        double dx=x-cx,dz=z-cz;
        if(dx!=0.0){
            double neighbor=RealDemSeamlessMath.measuredHeight(
                    cx+(dx<0?cell:-cell),cz);
            double slope=(neighbor-center)/(dx<0?cell:-cell);
            center+=clamp(slope,-1.25,1.25)
                    *RealDemSeamlessMath.taperedOutsideDisplacement(dx);
        }
        if(dz!=0.0){
            double neighbor=RealDemSeamlessMath.measuredHeight(
                    cx,cz+(dz<0?cell:-cell));
            double slope=(neighbor-originalCenter)/(dz<0?cell:-cell);
            center+=clamp(slope,-1.25,1.25)
                    *RealDemSeamlessMath.taperedOutsideDisplacement(dz);
        }
        return clamp(center,-64.0,2031.0);
    }

    private static double run(boolean optimized) {
        double sum=0;
        for(int i=0;i<COUNT;i++)
            sum+=optimized?RealDemSeamlessMath.extendedHeight(X[i],Z[i])
                    :original(X[i],Z[i]);
        sink=sum;
        return sum;
    }

    private static double median(long[] ns){
        Arrays.sort(ns);
        return ns[ns.length/2]/1_000_000.0;
    }

    public static void main(String[] args){
        for(int i=0;i<COUNT;i++){
            long expected=Double.doubleToRawLongBits(original(X[i],Z[i]));
            long actual=Double.doubleToRawLongBits(
                    RealDemSeamlessMath.extendedHeight(X[i],Z[i]));
            if(expected!=actual)throw new AssertionError(
                    "Corner extrapolation altered at "+X[i]+","+Z[i]);
        }
        for(int i=0;i<4;i++){run(false);run(true);}
        long[] old=new long[RUNS],fast=new long[RUNS];
        for(int trial=0;trial<RUNS;trial++){
            boolean fastFirst=(trial&1)==1;
            for(int pass=0;pass<2;pass++){
                boolean optimized=(pass==0)==fastFirst;
                long start=System.nanoTime();
                run(optimized);
                (optimized?fast:old)[trial]=System.nanoTime()-start;
            }
        }
        double baseline=median(old),changed=median(fast);
        System.out.printf(java.util.Locale.ROOT,
                "REALSCALE_USGS_CORNER_BENCHMARK samples=%d "
                +"original_median_ms=%.3f optimized_median_ms=%.3f "
                +"speedup=%.3fx raw_bits=identical%n",
                COUNT,baseline,changed,baseline/changed);
        if(!Double.isFinite(sink))throw new AssertionError("Bad checksum");
    }
}
