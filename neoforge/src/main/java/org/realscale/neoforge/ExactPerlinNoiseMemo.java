package org.realscale.neoforge;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * EXPERIMENT ONLY. Exact, per-worldgen-thread memo of vanilla PerlinNoise's
 * SIX argument getValue overload. All six scalar inputs and object identity
 * are checked bit-for-bit. Collisions only evict entries, never approximate
 * or interpolate noise; misses always execute the ORIGINAL vanilla method.
 *
 * The whole table is 64 slots per worker rather than per seeded noise object.
 * Profiling reports hits even if the opt-in cache later proves slower.
 */
public final class ExactPerlinNoiseMemo {
    private static final int SIZE=64;
    private static final ConcurrentLinkedQueue<ExactPerlinNoiseMemo> ALL =
            new ConcurrentLinkedQueue<>();
    private static final ThreadLocal<ExactPerlinNoiseMemo> CURRENT =
            ThreadLocal.withInitial(()->{
                ExactPerlinNoiseMemo memo=new ExactPerlinNoiseMemo();
                ALL.add(memo);
                return memo;
            });

    private final Object[] owners=new Object[SIZE];
    private final long[] xBits=new long[SIZE],yBits=new long[SIZE],
            zBits=new long[SIZE],scaleBits=new long[SIZE],maxBits=new long[SIZE];
    private final boolean[] origins=new boolean[SIZE];
    private final double[] values=new double[SIZE];
    private boolean replaying;
    private double found;
    private long calls,cacheHits,cacheMisses,replaySamples;

    public static ExactPerlinNoiseMemo current(){return CURRENT.get();}
    public boolean replaying(){return replaying;}
    public void setReplaying(boolean active){replaying=active;}

    private static long mix(long h,long value){
        h^=value+0x9e3779b97f4a7c15L+(h<<6)+(h>>>2);
        return h;
    }
    private static int slot(Object owner,long x,long y,long z,
            long scale,long max,boolean origin){
        long h=System.identityHashCode(owner)*0x9e3779b97f4a7c15L;
        h=mix(h,x);h=mix(h,y);h=mix(h,z);
        h=mix(h,scale);h=mix(h,max);
        h=mix(h,origin?0xD1B54A32D192ED03L:0x94D049BB133111EBL);
        return (int)(h^(h>>>32))&(SIZE-1);
    }

    public boolean get(Object owner,double x,double y,double z,
            double yScale,double yMax,boolean useOrigin){
        calls++;
        long xb=Double.doubleToRawLongBits(x);
        long yb=Double.doubleToRawLongBits(y);
        long zb=Double.doubleToRawLongBits(z);
        long sb=Double.doubleToRawLongBits(yScale);
        long mb=Double.doubleToRawLongBits(yMax);
        int i=slot(owner,xb,yb,zb,sb,mb,useOrigin);
        if(owners[i]==owner && xBits[i]==xb && yBits[i]==yb &&
                zBits[i]==zb && scaleBits[i]==sb && maxBits[i]==mb &&
                origins[i]==useOrigin){
            cacheHits++;
            found=values[i];
            return true;
        }
        cacheMisses++;
        return false;
    }
    public void countWithoutCache(){calls++;}
    public void put(Object owner,double x,double y,double z,
            double yScale,double yMax,boolean useOrigin,double result){
        long xb=Double.doubleToRawLongBits(x);
        long yb=Double.doubleToRawLongBits(y);
        long zb=Double.doubleToRawLongBits(z);
        long sb=Double.doubleToRawLongBits(yScale);
        long mb=Double.doubleToRawLongBits(yMax);
        int i=slot(owner,xb,yb,zb,sb,mb,useOrigin);
        // Write all fields before publishing the non-null owner.
        owners[i]=null;
        xBits[i]=xb;yBits[i]=yb;zBits[i]=zb;
        scaleBits[i]=sb;maxBits[i]=mb;origins[i]=useOrigin;
        values[i]=result;
        owners[i]=owner;
    }
    public double value(){return found;}
    public boolean shouldReplayHit(){
        return (cacheHits&1023L)==0L;
    }
    public void verifiedReplay(double cached,double vanilla){
        if(Double.doubleToRawLongBits(cached)!=Double.doubleToRawLongBits(vanilla))
            throw new AssertionError("Perlin memo disagreed with the exact "+
                    "original vanilla result: cached="+cached+" vanilla="+vanilla);
        replaySamples++;
    }

    public static String report(){
        long total=0,hits=0,misses=0,samples=0;
        for(ExactPerlinNoiseMemo m:ALL){
            total+=m.calls;hits+=m.cacheHits;
            misses+=m.cacheMisses;samples+=m.replaySamples;
        }
        return "REALSCALE_PERLIN_EXACT_MEMO"+
                " calls="+total+" hits="+hits+
                " misses="+misses+" replay_samples="+samples+
                " hit_ratio="+String.format(java.util.Locale.ROOT,
                "%.5f",total==0?0.0:(double)hits/total);
    }
}
