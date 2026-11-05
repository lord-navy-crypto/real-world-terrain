package org.realscale.neoforge;

import java.util.Arrays;

/**
 * Per-biome-source, per-worldgen-worker spatial memo for optional sky islands.
 *
 * Each 320x320 lattice cell has one immutable parent site and five immutable
 * satellites. Minecraft requests nearby quart coordinates (and many Y slices)
 * repeatedly during new-chunk generation. Reusing the exact original
 * SkyIslandLayout outputs avoids recomputing hashes and allocating Site records.
 *
 * Fixed-size two-way eviction only changes hit rate. A caller must clear
 * this cache when the Climate.Sampler identity changes so ocean tier decisions
 * cannot leak between worlds.
 */
final class SkyIslandSiteCache {
    static final int SLOTS=256;

    static final class Entry {
        final long gx,gz;
        private final SkyIslandLayout.Site base;
        private final SkyIslandLayout.Site[] satellites=new SkyIslandLayout.Site[SkyIslandLayout.SLOTS];
        int tier=-1;
        Entry(long gx,long gz,SkyIslandLayout.Site base) {
            this.gx=gx;
            this.gz=gz;
            this.base=base;
            satellites[0]=base;
        }
        SkyIslandLayout.Site base() {return base;}
        SkyIslandLayout.Site satellite(int slot) {
            SkyIslandLayout.Site existing=satellites[slot];
            if(existing!=null)return existing;
            SkyIslandLayout.Site created=SkyIslandLayout.satellite(base,slot);
            satellites[slot]=created;
            return created;
        }
    }

    private static final int BUCKETS=SLOTS/2;
    private final Entry[] entries=new Entry[SLOTS];
    private final byte[] victim=new byte[BUCKETS];
    private Entry mostRecent;

    static int bucket(long gx,long gz) {
        return hash(gx,gz)&(BUCKETS-1);
    }

    Entry at(int x,int z) {
        long gx=Math.floorDiv((long)x+SkyIslandLayout.GRID/2,SkyIslandLayout.GRID);
        long gz=Math.floorDiv((long)z+SkyIslandLayout.GRID/2,SkyIslandLayout.GRID);
        Entry last=mostRecent;
        if(last!=null && last.gx==gx && last.gz==gz)return last;
        int bucket=bucket(gx,gz),first=bucket<<1;
        Entry a=entries[first],b=entries[first+1];
        if(a!=null && a.gx==gx && a.gz==gz) {
            mostRecent=a;
            return a;
        }
        if(b!=null && b.gx==gx && b.gz==gz) {
            mostRecent=b;
            return b;
        }
        final int slot;
        if(a==null)slot=first;
        else if(b==null)slot=first+1;
        else {
            slot=first+(victim[bucket]&1);
            victim[bucket]^=1;
        }
        Entry cached=new Entry(gx,gz,SkyIslandLayout.at(x,z));
        entries[slot]=cached;
        mostRecent=cached;
        return cached;
    }

    void clear() {
        Arrays.fill(entries,null);
        Arrays.fill(victim,(byte)0);
        mostRecent=null;
    }

    private static int hash(long gx,long gz) {
        long v=gx*0x9e3779b97f4a7c15L ^ gz*0xc2b2ae3d27d4eb4fL;
        v^=v>>>33;
        v*=0xff51afd7ed558ccdL;
        v^=v>>>33;
        return (int)v;
    }
}
