package org.realscale.neoforge;

/**
 * EXPERIMENTAL horizontal vanilla Beardifier support envelope.
 * The 32-block guard exceeds the maximum 12-block horizontal influence
 * of the vanilla jigsaw beard kernel and the shorter bury support.
 * A union box is deliberately conservative. Invalid metadata fails open.
 */
public final class BeardifierXZExtent {
    private static final int GUARD=32;
    private long minX=Long.MAX_VALUE,maxX=Long.MIN_VALUE;
    private long minZ=Long.MAX_VALUE,maxZ=Long.MIN_VALUE;
    private boolean disabled;
    public void includePiece(int x0,int x1,int z0,int z1) {
        if(x0>x1 || z0>z1){disable();return;}
        include(x0,z0);include(x1,z1);
    }
    public void includeJunction(int x,int z){include(x,z);}
    private void include(long x,long z) {
        minX=Math.min(minX,x); maxX=Math.max(maxX,x);
        minZ=Math.min(minZ,z); maxZ=Math.max(maxZ,z);
    }
    public void disable(){disabled=true;}
    public boolean canSkip(int x,int z) {
        if(disabled)return false;
        if(minX==Long.MAX_VALUE)return true;
        return (long)x<minX-GUARD||(long)x>maxX+GUARD
                ||(long)z<minZ-GUARD||(long)z>maxZ+GUARD;
    }
    public int guard(){return GUARD;}
}
