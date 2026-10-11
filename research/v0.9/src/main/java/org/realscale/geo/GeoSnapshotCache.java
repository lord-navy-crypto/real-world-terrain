package org.realscale.geo;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Explicit bounded macro-region cache for worldgen worker threads.
 * Keys include seed, region position, algorithm version and all parameters.
 * The synchronized solve prevents duplicate expensive hydrology solves.
 * Caller MUST NOT modify array contents of a returned Result.
 *
 * Note: large-scale watershed graph is still independent at macro-region borders.
 */
public final class GeoSnapshotCache {
    public static final String ALGORITHM_VERSION="v0.9.0-region-a";
    private record Key(long seed,int nx,int nz,int x0,int z0,
                       RegionalWatershed.Parameters parameters,String version) { }
    private final int capacity;
    private final Map<Key,RegionalWatershed.Result> cache;
    private long hits=0,misses=0;
    public GeoSnapshotCache(int capacity){
        if(capacity<1||capacity>8)throw new IllegalArgumentException("Cache capacity must be 1..8 regions");
        this.capacity=capacity;
        cache=new LinkedHashMap<>(16,.75f,true){
            protected boolean removeEldestEntry(Map.Entry<Key,RegionalWatershed.Result> first){
                return size()>GeoSnapshotCache.this.capacity;
            }
        };
    }
    public synchronized RegionalWatershed.Result load(long seed,int nx,int nz,int x0,int z0,
                                                       RegionalWatershed.Parameters parameters){
        if(parameters==null)throw new IllegalArgumentException("Missing parameters");
        var key=new Key(seed,nx,nz,x0,z0,parameters,ALGORITHM_VERSION);
        var value=cache.get(key);
        if(value!=null){hits++;return value;}
        misses++;
        value=RegionalWatershed.solve(GeoMacroFactory.build(seed,nx,nz,x0,z0,parameters),parameters);
        cache.put(key,value);
        return value;
    }
    public synchronized int size(){return cache.size();}
    public synchronized long hits(){return hits;}
    public synchronized long misses(){return misses;}
    public synchronized void clear(){cache.clear();}
}
