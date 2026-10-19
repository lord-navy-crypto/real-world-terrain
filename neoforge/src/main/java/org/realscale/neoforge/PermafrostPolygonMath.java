package org.realscale.neoforge;

/**
 * Pure, deterministic ice-wedge-inspired patterned-ground geometry.
 * Jittered Voronoi cells make irregular polygon boundaries; coordinates are
 * WORLD coordinates, so patterns agree across 16-block chunk seams.
 * These are visual surface proxies, not a thermal/permafrost simulation.
 */
public final class PermafrostPolygonMath {
    private PermafrostPolygonMath() {}
    private static final int CELL = 24;

    private static long mix(long n) {
        n ^= n >>> 30; n *= 0xbf58476d1ce4e5b9L;
        n ^= n >>> 27; n *= 0x94d049bb133111ebL;
        return n ^ (n >>> 31);
    }

    /** True within approximately one block of a polygon border. */
    public static boolean isRim(int x,int z) {
        int cx=Math.floorDiv(x,CELL), cz=Math.floorDiv(z,CELL);
        long nearest=Long.MAX_VALUE, second=Long.MAX_VALUE;
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++) {
            long gx=(long)cx+dx, gz=(long)cz+dz;
            long salt=mix(gx*0x9e3779b97f4a7c15L
                         ^gz*0xc2b2ae3d27d4eb4fL ^0x67c14fa1L);
            long px=gx*CELL+CELL/2+(int)Math.floorMod(salt,9L)-4;
            long pz=gz*CELL+CELL/2+(int)Math.floorMod(salt>>>16,9L)-4;
            long qx=(long)x-px,qz=(long)z-pz;
            long distance=qx*qx+qz*qz;
            if(distance<nearest) {second=nearest;nearest=distance;}
            else if(distance<second) second=distance;
        }
        return second-nearest<=35;
    }
}
