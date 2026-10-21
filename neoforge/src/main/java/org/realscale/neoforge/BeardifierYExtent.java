package org.realscale.neoforge;

/**
 * Strictly conservative vertical support envelope for Minecraft's vanilla
 * structure terrain adaptation. Only an opt-in Beardifier mixin uses it.
 *
 * At any Y more than 256 blocks from every structure piece bounding box,
 * each piece's terrain-adjusted vertical anchor and all jigsaw ground Y,
 * the vanilla 12-block beard kernel and 6-block bury support are zero.
 * No modification to structures, noise, blocks or terrain is made.
 *
 * If metadata cannot be safely inspected, turn this entire optimization off
 * rather than estimating structure geometry.
 */
public final class BeardifierYExtent {
    private static final int ORIGINAL_GUARD=256;
    private static final int GUARD=Boolean.getBoolean(
            "realscale.experimental.beardifier.tight_y") ? 64 : ORIGINAL_GUARD;
    private long lo=Long.MAX_VALUE,hi=Long.MIN_VALUE;
    private boolean disabled;

    public void includePiece(int minY,int maxY,int groundLevelDelta) {
        include(minY);include(maxY);
        include((long)minY+groundLevelDelta);
        include((long)maxY+groundLevelDelta);
    }
    public void includeJunction(int sourceGroundY){include(sourceGroundY);}
    private void include(long y){lo=Math.min(lo,y);hi=Math.max(hi,y);}
    public void disable(){disabled=true;}
    public boolean canSkip(int y) {
        if(disabled)return false;
        if(lo==Long.MAX_VALUE)return true; // Both original iterators empty.
        return (long)y<lo-GUARD||(long)y>hi+GUARD;
    }
    public boolean canSkipOriginal(int y){
        if(disabled)return false;
        if(lo==Long.MAX_VALUE)return true;
        return (long)y<lo-ORIGINAL_GUARD||(long)y>hi+ORIGINAL_GUARD;
    }
    public int guard(){return GUARD;}
    public boolean isTight(){return GUARD<ORIGINAL_GUARD;}
}
