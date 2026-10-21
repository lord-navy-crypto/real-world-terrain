package org.realscale.neoforge;

/**
 * Small per-feature-placement memo for overlapping 7x7 source-water scans.
 *
 * The landmark feature checks 3-4 nearby rock columns, and may try 3 sites
 * before writing blocks. All preflight checks happen before ANY placement,
 * so the water state is constant throughout this one place() invocation.
 * Cache exactly (x,y,z), never only XZ, and never share between placements,
 * dimensions, chunk workers, or world seeds. Collision is a miss, not a hit.
 */
final class LandmarkWaterMemo {
    interface Probe {
        boolean isSourceWater(int x,int y,int z);
    }
    static final int SLOTS=256;
    private final int[] xs=new int[SLOTS];
    private final int[] ys=new int[SLOTS];
    private final int[] zs=new int[SLOTS];
    private final byte[] states=new byte[SLOTS]; // 0=unknown; 1=dry; 2=water
    private final Probe probe;
    private long hits,misses;
    LandmarkWaterMemo(Probe probe){this.probe=probe;}
    boolean isSourceWater(int x,int y,int z){
        int slot=slot(x,y,z);
        byte b=states[slot];
        if(b!=0 && xs[slot]==x && ys[slot]==y && zs[slot]==z){
            hits++;
            return b==2;
        }
        boolean found=probe.isSourceWater(x,y,z);
        xs[slot]=x;ys[slot]=y;zs[slot]=z;
        states[slot]=(byte)(found?2:1);
        misses++;
        return found;
    }
    static int slot(int x,int y,int z){
        int hash=x*0x9e3779b9^Integer.rotateLeft(y*0x85ebca6b,11)
                ^Integer.rotateLeft(z*0xc2b2ae35,19);
        hash^=hash>>>16;
        return hash&(SLOTS-1);
    }
    long hits(){return hits;}
    long misses(){return misses;}
}
