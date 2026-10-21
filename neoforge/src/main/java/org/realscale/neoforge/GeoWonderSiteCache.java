package org.realscale.neoforge;

/**
 * Fixed-size direct-mapped region-site memo for ONE kind and ONE worker.
 * Surface sites are deterministic in absolute grid coordinates, and can be
 * absent (null) in 20 of 21 cells; cache those misses as well.
 *
 * Eviction changes only recomputation cost, not site seed/geometry. No
 * global cache, chunk reads, or neighboring chunk dependency.
 */
final class GeoWonderSiteCache {
    private static final int SLOTS=256,MASK=SLOTS-1;
    private final long[] keys=new long[SLOTS];
    private final boolean[] visited=new boolean[SLOTS];
    private final GeoWonderGeometry.Site[] sites=new GeoWonderGeometry.Site[SLOTS];
    private long recentKey;
    private boolean hasRecent;
    private GeoWonderGeometry.Site recent;
    private final int kind;
    private long hits,misses;

    GeoWonderSiteCache(int kind) {this.kind=kind;}

    GeoWonderGeometry.Site get(int gridX,int gridZ) {
        long key=((long)gridX<<32)|(gridZ&0xffffffffL);
        if(hasRecent&&recentKey==key){hits++;return recent;}
        int slot=slot(key);
        GeoWonderGeometry.Site item;
        if(visited[slot]&&keys[slot]==key) {
            hits++;item=sites[slot];
        }else{
            item=GeoWonderGeometry.surfaceSite(gridX,gridZ,kind);
            keys[slot]=key;visited[slot]=true;sites[slot]=item;
            misses++;
        }
        hasRecent=true;recentKey=key;recent=item;
        return item;
    }
    static int slot(long key){
        long h=key^(key>>>33);
        h*=0xff51afd7ed558ccdL;
        h^=h>>>33;
        return (int)h&MASK;
    }
    long hits(){return hits;}
    long misses(){return misses;}
}
