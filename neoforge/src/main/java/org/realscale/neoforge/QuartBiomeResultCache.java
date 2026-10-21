package org.realscale.neoforge;

import java.util.Arrays;
import java.util.Objects;

/**
 * Bounded two-way quart-coordinate biome-result memo for one BiomeSource and
 * one worker thread. High (2096-block) worlds revisit many biome quart
 * columns while caves, surfaces, structures and feature checks interleave.
 *
 * v2: 8192 entries / 4096 buckets, two candidates in each bucket, avoids
 * direct-mapped churn when adjacent noisy-biome queries hash together.
 * No biome approximation: a miss always recomputes the exact original Holder.
 *
 * The owner MUST clear() when Climate.Sampler identity changes. No global
 * sharing between dimensions, world seeds, or generation threads.
 */
final class QuartBiomeResultCache<V> {
    static final int SLOTS=8192;
    private static final int BUCKETS=SLOTS/2;
    private final long[] coordinates=new long[SLOTS];
    private final Object[] results=new Object[SLOTS];
    private final byte[] victim=new byte[BUCKETS];
    private long lastCoordinate;
    private Object lastValue;

    @SuppressWarnings("unchecked")
    V get(long coordinate) {
        if(lastValue!=null && lastCoordinate==coordinate)
            return (V)lastValue;
        final int index=bucket(coordinate)<<1;
        Object result=results[index];
        if(result!=null && coordinates[index]==coordinate) {
            lastCoordinate=coordinate;
            lastValue=result;
            return (V)result;
        }
        result=results[index+1];
        if(result!=null && coordinates[index+1]==coordinate){
            lastCoordinate=coordinate;
            lastValue=result;
            return (V)result;
        }
        return null;
    }

    void put(long coordinate,V value) {
        Objects.requireNonNull(value,"biome resolution must return a Holder");
        final int bucket=bucket(coordinate);
        int index=bucket<<1;
        if((results[index]!=null && coordinates[index]==coordinate)
                ||results[index]==null) {
            // Update existing left entry, otherwise fill empty slot.
        }else if((results[index+1]!=null && coordinates[index+1]==coordinate)
                ||results[index+1]==null) {
            index++;
        }else{
            // Two competing quart keys get independent places; when full,
            // alternate victim. Eviction never changes source biome results.
            index+=victim[bucket]&1;
            victim[bucket]^=1;
        }
        coordinates[index]=coordinate;
        results[index]=value;
        lastCoordinate=coordinate;
        lastValue=value;
    }

    void clear() {
        Arrays.fill(results,null);
        Arrays.fill(victim,(byte)0);
        lastValue=null;
    }

    static int bucket(long coordinate) {
        long h=coordinate^(coordinate>>>21)^(coordinate>>>42);
        h^=h>>>33;
        h*=0xff51afd7ed558ccdL;
        h^=h>>>33;
        return (int)h&(BUCKETS-1);
    }

    // Used by CI to detect pathological aliasing without retaining coordinates.
    static int slot(long coordinate) {
        return bucket(coordinate)<<1;
    }
}
