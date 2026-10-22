package org.realscale.neoforge;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * EXPERIMENT ONLY: the cheapest conservative repeated vanilla Perlin call.
 *
 * No hash table or buckets: compare only with the IMMEDIATELY preceding
 * completed getValue(DDDDDZ)D of this same worldgen thread. Every difference
 * in the object identity, five raw double bit patterns or boolean results in
 * unmodified vanilla computation. Misses never approximate or interpolate.
 *
 * A replay re-enters vanilla with a per-thread recursion guard. The Mixin
 * method signature and vanilla return-value injection are unchanged.
 */
public final class ExactPerlinNoiseMemo {
    private static final ConcurrentLinkedQueue<ExactPerlinNoiseMemo> ALL =
            new ConcurrentLinkedQueue<>();
    private static final ThreadLocal<ExactPerlinNoiseMemo> CURRENT =
            ThreadLocal.withInitial(()->{
                ExactPerlinNoiseMemo m=new ExactPerlinNoiseMemo();
                ALL.add(m);
                return m;
            });

    private Object owner;
    private long xb,yb,zb,sb,mb;
    private boolean origin,valid,replaying;
    private double lastValue,found;
    private long calls,cacheHits,cacheMisses,replaySamples;

    public static ExactPerlinNoiseMemo current(){return CURRENT.get();}
    public boolean replaying(){return replaying;}
    public void setReplaying(boolean active){replaying=active;}

    public boolean get(Object noise,double x,double y,double z,
            double yScale,double yMax,boolean useOrigin){
        calls++;
        // Test object identity first, keeping the common miss inexpensive.
        if(valid && owner==noise &&
                xb==Double.doubleToRawLongBits(x) &&
                yb==Double.doubleToRawLongBits(y) &&
                zb==Double.doubleToRawLongBits(z) &&
                sb==Double.doubleToRawLongBits(yScale) &&
                mb==Double.doubleToRawLongBits(yMax) &&
                origin==useOrigin){
            cacheHits++;
            found=lastValue;
            return true;
        }
        cacheMisses++;
        return false;
    }
    public void countWithoutCache(){calls++;}
    public void put(Object noise,double x,double y,double z,
            double yScale,double yMax,boolean useOrigin,double result){
        // One cache entry per thread. No allocation, hash or eviction policy.
        valid=false;
        owner=noise;
        xb=Double.doubleToRawLongBits(x);
        yb=Double.doubleToRawLongBits(y);
        zb=Double.doubleToRawLongBits(z);
        sb=Double.doubleToRawLongBits(yScale);
        mb=Double.doubleToRawLongBits(yMax);
        origin=useOrigin;
        lastValue=result;
        valid=true;
    }
    public double value(){return found;}
    public boolean shouldReplayHit(){return (cacheHits&1023L)==0L;}
    public void verifiedReplay(double cached,double vanilla){
        if(Double.doubleToRawLongBits(cached)!=Double.doubleToRawLongBits(vanilla))
            throw new AssertionError("Last-call Perlin memo changed original "+
                    "vanilla bits: cached="+cached+" vanilla="+vanilla);
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
                "%.5f",total==0?0.0:(double)hits/total)+
                " strategy=single_last_value_no_hash";
    }
}
