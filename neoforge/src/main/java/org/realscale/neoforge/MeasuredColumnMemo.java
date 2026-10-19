package org.realscale.neoforge;

/**
 * Exact, bounded X/Z cache for a proven Y-invariant (2D) density input.
 *
 * Package-private and Minecraft-free so eviction, zero/NaN storage and
 * per-instance isolation can be tested without NeoForge on the JUnit classpath.
 * The caller decides when it is valid to reuse a value across different Y.
 */
final class MeasuredColumnMemo {
    static final int SLOTS=8192;
    private final long[] coordinates=new long[SLOTS];
    private final double[] values=new double[SLOTS];
    private final boolean[] valid=new boolean[SLOTS];
    private boolean lastValid;
    private long lastCoordinate;
    private double lastValue;
    private long missCoordinate;
    private int missSlot;

    boolean lookup(int x,int z) {
        long coordinate=((long)x<<32)|(z&0xffffffffL);
        if(lastValid && lastCoordinate==coordinate)return true;
        int hash=x*0x9e3779b9 ^ Integer.rotateLeft(z*0x85ebca6b,13);
        int slot=(hash^(hash>>>16))&(SLOTS-1);
        if(valid[slot] && coordinates[slot]==coordinate) {
            lastCoordinate=coordinate;
            lastValue=values[slot];
            lastValid=true;
            return true;
        }
        missCoordinate=coordinate;
        missSlot=slot;
        return false;
    }

    double value() { return lastValue; }

    /**
     * Call after a lookup miss, without any intervening lookup. This is
     * thread-confined by the owner ThreadLocal and retains the exact bits.
     */
    void store(double value) {
        coordinates[missSlot]=missCoordinate;
        values[missSlot]=value;
        valid[missSlot]=true;
        lastCoordinate=missCoordinate;
        lastValue=value;
        lastValid=true;
    }
}
