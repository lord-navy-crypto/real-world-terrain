package org.realscale.neoforge;

import java.util.function.BiPredicate;

/**
 * Deterministic, world-seed-independent geometry for optional fantasy sky
 * archipelagos. The wrapped Minecraft climate source decides which candidate
 * cells are actually far enough from land to create islands.
 */
public final class SkyIslandLayout {
    private SkyIslandLayout() {}
    public static final int GRID = 320;
    public static final int MIN_ISOLATION = 256;
    public static final int MAX_ISOLATION = 640;
    public static final int SLOTS = 5;
    // Five island footprints lie at the base center or +/-80 X/Z. Every
    // satellite radius is 7..11; a 94-block axis bound is conservative.
    // This rejects most sky-biome samples before allocating any satellites.
    public static final int MAX_SITE_REACH = 94;
    // Base tops are 183..203; satellite tops add -3..+3, and
    // insideBiome extends -13..+8 from each top. Every sky island biome
    // sample outside this inclusive height range is impossible.
    public static final int MIN_BIOME_Y = 167;
    public static final int MAX_BIOME_Y = 214;
    private static final int DIRECTIONS = 16;
    // More numerous small near-shore islets and denser far-ocean clusters.
    // Ocean probes progressively increase the required offshore isolation.
    private static final int[] PROBE_RADII = {64,128,192,256,352,448,544,640};
    private static final int[] PROBE_X = new int[PROBE_RADII.length*DIRECTIONS];
    private static final int[] PROBE_Z = new int[PROBE_X.length];
    private static final int[][] SATELLITES = {{0,0},{80,0},{-80,0},{0,80},{0,-80}};
    static {
        for (int n=0;n<PROBE_RADII.length;n++) for (int i=0;i<DIRECTIONS;i++) {
            double angle=i*2.0*Math.PI/DIRECTIONS;
            PROBE_X[n*DIRECTIONS+i]=(int)Math.round(PROBE_RADII[n]*Math.cos(angle));
            PROBE_Z[n*DIRECTIONS+i]=(int)Math.round(PROBE_RADII[n]*Math.sin(angle));
        }
    }
    public record Site(int x,int z,int topY,int radius) {}
    private static long mix(long x) {
        x^=x>>>30; x*=0xbf58476d1ce4e5b9L;
        x^=x>>>27; x*=0x94d049bb133111ebL;
        return x^(x>>>31);
    }
    public static Site at(int blockX,int blockZ) {
        long gx=Math.floorDiv((long)blockX+GRID/2,GRID);
        long gz=Math.floorDiv((long)blockZ+GRID/2,GRID);
        long hash=mix((gx*0x9e3779b97f4a7c15L)
               ^ (gz*0xc2b2ae3d27d4eb4fL) ^ 0x3bc7f54a83e1d209L);
        // Keep each satellite in the same lattice cell as its parent.
        // Previously +/-128 jitter plus +/-96 offsets escaped the cell,
        // so home chunks selected a DIFFERENT base and many islands vanished.
        int x=(int)(gx*GRID-48+Math.floorMod(hash,96L));
        int z=(int)(gz*GRID-48+Math.floorMod(hash>>>15,96L));
        return new Site(x,z,183+(int)Math.floorMod(hash>>>30,21L),
                10+(int)Math.floorMod(hash>>>47,5L));
    }
    /** The main island plus four distant, independently shaped satellites. */
    /** Conservative per-axis reject; never hides a valid sky island. */
    public static boolean mayIntersectIslands(Site base,int x,int z) {
        return Math.abs((long)x-base.x())<=MAX_SITE_REACH
                && Math.abs((long)z-base.z())<=MAX_SITE_REACH;
    }
    public static Site satellite(Site base,int slot) {
        if(slot<0||slot>=SLOTS) throw new IllegalArgumentException("invalid sky slot");
        if(slot==0)return base;
        long hash=mix((((long)base.x())<<32) ^ (base.z()&0xffffffffL)
                ^ (0x9e3779b97f4a7c15L*slot));
        return new Site(base.x()+SATELLITES[slot][0],
                base.z()+SATELLITES[slot][1],
                base.topY()+(int)Math.floorMod(hash,7L)-3,
                7+(int)Math.floorMod(hash>>>27,5L));
    }
    /** 0 absent, 1 sparse, 2 a three-island group, 3 a five-island cluster.
     * Returns only a *sampled lower bound* on coast isolation, NOT an exact
     * shortest distance to every land block. The first failed ring stops
     * all further checks, preventing needless climate lookups near coasts.
     */
    public static int oceanTier(Site base,
            BiPredicate<Integer,Integer> deep,
            BiPredicate<Integer,Integer> ocean) {
        if(!deep.test(base.x(),base.z()))return 0;
        for(int ring=0;ring<PROBE_RADII.length;ring++) {
            int first=ring*DIRECTIONS;
            for(int i=0;i<DIRECTIONS;i++)
                if(!ocean.test(base.x()+PROBE_X[first+i],
                               base.z()+PROBE_Z[first+i]))
                    return ring<4?0:ring<6?1:2;
        }
        return 3;
    }
    public static int countForTier(int tier) {
        return switch(tier) {
            case 1 -> 1;
            case 2 -> 3;
            case 3 -> 5;
            default -> 0;
        };
    }
    public static boolean remoteDeepOcean(Site base,
            BiPredicate<Integer,Integer> deep,
            BiPredicate<Integer,Integer> ocean) {
        return oceanTier(base,deep,ocean)>0;
    }
    public static boolean insideBiome(Site site,int x,int y,int z) {
        long dx=(long)x-site.x(), dz=(long)z-site.z();
        return dx*dx+dz*dz <= (long)site.radius()*site.radius()
                && y>=site.topY()-13 && y<=site.topY()+8;
    }
}
