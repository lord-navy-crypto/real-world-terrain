package org.realscale.neoforge;

import java.util.Arrays;

/**
 * Bounded, worker-confined cache for the northern-v4 fixed-Y=16 surface
 * climate lookup. The same X/Z quart column is evaluated repeatedly at
 * cave heights -12 through 9; the surface query never changes its Y.
 *
 * Strictly cache only the exact resolved surface biome key and the pure
 * world-absolute regional hash. Reset immediately when the Climate.Sampler
 * object changes; instances are owned by one BiomeSource, not global.
 * Hash collisions NEVER return neighboring columns' biomes.
 */
final class NorthernCaveSurfaceMemoV6 {
    static final int SLOTS=2048;
    private static final int MASK=SLOTS-1;
    private final long[] keys=new long[SLOTS];
    private final String[] names=new String[SLOTS];
    private final long[] regions=new long[SLOTS];
    private final boolean[] valid=new boolean[SLOTS];
    private Object samplerIdentity;
    private long latestKey;
    private String latestName;
    private long latestRegion;
    private boolean latestValid;
    private long hits,misses;

    void prepare(Object sampler) {
        if(samplerIdentity==sampler)return;
        samplerIdentity=sampler;
        latestValid=false;
        Arrays.fill(valid,false);
    }

    private static long pack(int qx,int qz) {
        return ((long)qx<<32)|(qz&0xffffffffL);
    }
    static int slot(int qx,int qz) {
        int h=qx*0x9e3779b9^Integer.rotateLeft(qz*0x85ebca6b,13);
        return (h^(h>>>16))&MASK;
    }
    boolean lookup(int qx,int qz) {
        long key=pack(qx,qz);
        if(latestValid&&latestKey==key) {
            hits++;
            return true;
        }
        int i=slot(qx,qz);
        if(valid[i]&&keys[i]==key) {
            latestKey=key;latestValid=true;
            latestName=names[i];latestRegion=regions[i];
            hits++;
            return true;
        }
        misses++;
        return false;
    }
    void store(int qx,int qz,String name,long regionHash) {
        long key=pack(qx,qz);
        int i=slot(qx,qz);
        keys[i]=key;names[i]=name;regions[i]=regionHash;valid[i]=true;
        latestKey=key;latestName=name;latestRegion=regionHash;latestValid=true;
    }
    String name(){return latestName;}
    long regionHash(){return latestRegion;}
    long hits(){return hits;}
    long misses(){return misses;}
}
