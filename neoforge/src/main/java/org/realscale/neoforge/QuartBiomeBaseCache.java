package org.realscale.neoforge;

import java.util.Arrays;
import java.util.Objects;

/**
 * Bounded primitive-coordinate cache for underlying climate-biome samples.
 *
 * Every RealScale ecotone ray asks for many nearby quart coordinates. The old
 * access-order LinkedHashMap boxed each long coordinate and allocated nodes
 * for misses, increasing heap churn during large new-chunk generation.
 *
 * Two entries per bucket reduce collisions without allocating on hits or
 * misses. All entries belong to one BiomeSource and one worldgen worker.
 * A different Climate.Sampler MUST clear the cache. Eviction only recomputes
 * the original source lookup and can never affect the selected biome.
 */
final class QuartBiomeBaseCache<V> {
    static final int SLOTS = 8192;
    private static final int BUCKETS = SLOTS / 2;
    private final int capacity;
    private final int bucketMask;
    private final long[] keys;
    private final Object[] values;
    private final byte[] victim;
    private long lastKey;
    private Object lastValue;

    QuartBiomeBaseCache() {
        this(RealScalePerformanceProfile.cacheSlots("biome_base", SLOTS));
    }

    QuartBiomeBaseCache(int slots) {
        if (slots < 16 || (slots & (slots - 1)) != 0)
            throw new IllegalArgumentException("Cache slots must be a power of two >= 16");
        this.capacity = slots;
        this.bucketMask = (slots >>> 1) - 1;
        this.keys = new long[slots];
        this.values = new Object[slots];
        this.victim = new byte[slots >>> 1];
    }

    int capacity() {
        return capacity;
    }

    @SuppressWarnings("unchecked")
    V get(long key) {
        if (lastValue != null && lastKey == key) return (V)lastValue;
        int index=bucket(key,bucketMask)<<1;
        Object a=values[index];
        if (a!=null && keys[index]==key) {
            lastKey=key;lastValue=a;
            return (V)a;
        }
        Object b=values[index+1];
        if (b!=null && keys[index+1]==key) {
            lastKey=key;lastValue=b;
            return (V)b;
        }
        return null;
    }

    void put(long key,V value) {
        Objects.requireNonNull(value,"biome source must return a holder");
        int bucket=bucket(key,bucketMask),index=bucket<<1;
        if ((values[index]!=null && keys[index]==key)
                || values[index]==null) {
            // Keep existing key in its own slot, and otherwise fill vacancy.
        } else if ((values[index+1]!=null && keys[index+1]==key)
                || values[index+1]==null) {
            index++;
        } else {
            index+=victim[bucket]&1;
            victim[bucket]^=1;
        }
        keys[index]=key;
        values[index]=value;
        lastKey=key;
        lastValue=value;
    }

    void clear() {
        Arrays.fill(values,null);
        lastValue=null;
    }

    private static int bucket(long key) {
        return bucket(key,BUCKETS - 1);
    }

    private static int bucket(long key,int mask) {
        long h=key ^ (key>>>21) ^ (key>>>42);
        h^=h>>>33;
        h*=0xff51afd7ed558ccdL;
        h^=h>>>33;
        return (int)h&mask;
    }
}
