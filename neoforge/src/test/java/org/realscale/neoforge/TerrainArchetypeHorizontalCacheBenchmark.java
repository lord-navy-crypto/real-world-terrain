package org.realscale.neoforge;

import java.util.Arrays;

/**
 * Informational paired microbenchmark for the production fitted horizontal
 * morphology cache. It reports timings but has no noisy CI speed threshold.
 * Every measured result is checked against an independent uncached formula.
 */
public final class TerrainArchetypeHorizontalCacheBenchmark {
    private static final String[] FAMILIES={"alpine","canyon","glacial","dune"};
    private static final int COLUMNS=1024;
    private static final int VERTICAL_SAMPLES=64;
    private static final int SAMPLES=COLUMNS*VERTICAL_SAMPLES;
    private static volatile long sink;

    private TerrainArchetypeHorizontalCacheBenchmark() {}

    private record Inputs(double[] original,double[] continent,double[] signal,
                          double[] x,double[] z) {}

    private static Inputs inputs() {
        double[] original=new double[SAMPLES],continent=new double[SAMPLES];
        double[] signal=new double[SAMPLES],x=new double[SAMPLES],z=new double[SAMPLES];
        int index=0;
        for(int column=0;column<COLUMNS;column++) {
            int bx=(column%32)*64-1024;
            int bz=(column/32)*64-1024;
            for(int y=0;y<VERTICAL_SAMPLES;y++) {
                original[index]=0.42+(y%10)*0.137;
                continent[index]=(y%7==0)?-0.05:0.35;
                signal[index]=Math.sin(y*0.173);
                x[index]=bx;
                z[index]=bz;
                index++;
            }
        }
        return new Inputs(original,continent,signal,x,z);
    }

    private static double smoothstep(double low,double high,double x) {
        if(low==high)return x<low?0.0:1.0;
        double t=Math.max(0.0,Math.min(1.0,(x-low)/(high-low)));
        return t*t*(3.0-2.0*t);
    }

    private static double reference(String family,double original,double continent,
            double rawSignal,double x,double z,double axisCos,double axisSin) {
        double gate=smoothstep(-0.10,0.22,continent);
        if(gate<=0.0)return original;
        double signal=Math.max(-1.0,Math.min(1.0,rawSignal));
        double across=x*axisCos+z*axisSin;
        return switch(family) {
            case "alpine" -> {
                double highland=smoothstep(0.65,1.7,original);
                double phase=2.0*Math.PI*across/9800.0;
                double bands=0.85+0.15*Math.pow(
                        Math.max(0.0,Math.cos(phase)),2.0);
                yield original+gate*highland*0.71*0.22*0.63*0.48
                        *bands*(1.0-Math.abs(signal));
            }
            case "canyon" -> {
                double vd=Math.abs(Math.sin(Math.PI*across/9800.0));
                double trough=1.0-smoothstep(0.0,0.18,vd);
                double shoulder=smoothstep(0.18,0.25,vd)
                        *(1.0-smoothstep(0.25,0.42,vd));
                yield original-gate*0.71*(0.28*0.58*0.62*Math.abs(signal)
                        +0.10*0.53*trough)
                        +gate*0.71*0.025*0.62*shoulder;
            }
            case "glacial" -> {
                int cellX=Math.floorDiv((int)x,2048);
                double width=343.0;
                double centerX=cellX*2048.0+1024.0
                        +width*0.22*Math.sin(2.0*Math.PI*z/9800.0+cellX*1.17);
                double acrossX=Math.abs(x-centerX);
                double trough=1.0-smoothstep(width*0.28,width,acrossX);
                yield original-gate*0.71*0.44*Math.max(0.53,0.12)*trough;
            }
            case "dune" -> {
                double phase=2.0*Math.PI*across/9800.0+signal*0.12;
                double wave=Math.sin(phase)+0.28*Math.sin(2.0*phase+0.6);
                yield original+gate*0.71*0.10*Math.max(0.63,0.12)*1.25*wave;
            }
            default -> original;
        };
    }

    private static long runReference(String family,Inputs in,double cos,double sin) {
        long checksum=0;
        for(int i=0;i<SAMPLES;i++) {
            double value=reference(family,in.original[i],in.continent[i],
                    in.signal[i],in.x[i],in.z[i],cos,sin);
            checksum^=Double.doubleToRawLongBits(value);
        }
        return checksum;
    }

    private static long runCached(TerrainArchetypeMath math,Inputs in) {
        long checksum=0;
        for(int i=0;i<SAMPLES;i++) {
            double value=math.compute(in.original[i],in.continent[i],in.signal[i],
                    in.x[i],in.z[i]);
            checksum^=Double.doubleToRawLongBits(value);
        }
        return checksum;
    }

    private static long median(long[] values) {
        Arrays.sort(values);
        return values[values.length/2];
    }

    public static void main(String[] args) {
        Inputs in=inputs();
        double cos=Math.cos(0.29),sin=Math.sin(0.29);
        for(String family:FAMILIES) {
            TerrainArchetypeMath math=new TerrainArchetypeMath(
                    family,0.63,0.58,0.39,0.71,0.48,0.62,0.53,0.69,
                    1.25,0.29,9800.0);
            long expected=runReference(family,in,cos,sin);
            long actual=runCached(math,in);
            if(expected!=actual)throw new AssertionError(
                    "Raw-bit mismatch in horizontal cache benchmark: "+family);
            for(int i=0;i<3;i++) {
                sink^=runReference(family,in,cos,sin);
                sink^=runCached(math,in);
            }
            long[] reference=new long[5],cached=new long[5];
            for(int i=0;i<5;i++) {
                long start=System.nanoTime();
                reference[i]=System.nanoTime()-start;
                start=System.nanoTime();
                sink^=runReference(family,in,cos,sin);
                reference[i]=System.nanoTime()-start;
                start=System.nanoTime();
                sink^=runCached(math,in);
                cached[i]=System.nanoTime()-start;
            }
            double refMs=median(reference)/1_000_000.0;
            double cachedMs=median(cached)/1_000_000.0;
            System.out.printf(
                    "REALSCALE_HORIZONTAL_CACHE_BENCHMARK family=%s samples=%d "
                    +"reference_median_ms=%.3f cached_median_ms=%.3f speedup=%.3fx raw_bits=identical%n",
                    family,SAMPLES,refMs,cachedMs,refMs/cachedMs);
        }
        if(sink==Long.MIN_VALUE)System.out.println("benchmark sink="+sink);
    }
}
