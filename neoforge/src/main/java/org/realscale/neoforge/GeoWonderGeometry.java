package org.realscale.neoforge;

/**
 * Pure coordinate geometry for rare, world-space geological wonder regions.
 * Safe to exercise without a Minecraft instance. Uses long intermediates for
 * negative coordinates and same geometry across all chunk visitation orders.
 */
public final class GeoWonderGeometry {
    private GeoWonderGeometry() {}
    public static final int GRID=256;

    public static final class Site {
        public final int x,z,r;
        public final long seed;
        public Site(int x,int z,int r,long seed) {
            this.x=x;this.z=z;this.r=r;this.seed=seed;
        }
    }
    public static long mix(long n) {
        n^=n>>>30;n*=0xbf58476d1ce4e5b9L;
        n^=n>>>27;n*=0x94d049bb133111ebL;
        return n^(n>>>31);
    }
    public static Site surfaceSite(int gx,int gz,int kindOrdinal) {
        long hash=mix(((long)gx*0x9e3779b97f4a7c15L)
                     ^ ((long)gz*0xc2b2ae3d27d4eb4fL)
                     ^ ((long)(kindOrdinal+1)*0x165667b19e3779f9L));
        if(Math.floorMod(hash,21L)!=0)return null;
        int x=gx*GRID+(int)Math.floorMod(hash>>>9,65L)-32;
        int z=gz*GRID+(int)Math.floorMod(hash>>>25,65L)-32;
        int r=56+(int)Math.floorMod(hash>>>43,36L);
        return new Site(x,z,r,hash);
    }
    public static boolean intersects(Site s,int minX,int minZ) {
        return s.x+s.r>=minX && s.x-s.r<minX+16
                && s.z+s.r>=minZ && s.z-s.r<minZ+16;
    }
    public static boolean contains(Site s,int x,int z) {
        long dx=(long)x-s.x,dz=(long)z-s.z;
        return dx*dx+dz*dz<=(long)s.r*s.r;
    }
    public static int gridAtChunk(int chunkMin) {
        return (int)Math.floorDiv((long)chunkMin+8+GRID/2,GRID);
    }
}
