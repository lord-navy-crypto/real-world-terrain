package org.realscale.neoforge;

/**
 * Fixed-size, allocation-free XZ cache for a single worker and a single
 * remapped USGS density graph. Never static/shared across seeds or threads.
 * A cache collision affects performance ONLY, not values or seam geometry.
 * Coordinates are absolute signed Minecraft block coordinates.
 */
final class RealDemFittedColumnCache {
    static final int SLOTS=4096;
    private static final int MASK=SLOTS-1;
    private final long[] keys=new long[SLOTS];
    private final boolean[] used=new boolean[SLOTS];
    private final double[] fittedWeights=new double[SLOTS];
    private final double[] fittedHeights=new double[SLOTS];

    private long recentKey;
    private boolean recentValid;
    private double currentWeight,currentHeight;
    private long hits,misses;

    static long key(int x,int z){
        return ((long)x<<32)|(z&0xffffffffL);
    }
    static int index(int x,int z){
        int h=x*0x9e3779b9^Integer.rotateLeft(z*0x85ebca6b,13);
        return (h^(h>>>16))&MASK;
    }
    boolean lookup(int x,int z){
        final long packed=key(x,z);
        if(recentValid && recentKey==packed){
            hits++;
            return true;
        }
        final int slot=index(x,z);
        if(used[slot] && keys[slot]==packed){
            recentKey=packed;recentValid=true;
            currentWeight=fittedWeights[slot];
            currentHeight=fittedHeights[slot];
            hits++;
            return true;
        }
        misses++;
        return false;
    }
    void store(int x,int z,double weight,double height){
        final long packed=key(x,z);
        final int slot=index(x,z);
        used[slot]=true;
        keys[slot]=packed;
        fittedWeights[slot]=weight;
        fittedHeights[slot]=height;
        recentKey=packed;recentValid=true;
        currentWeight=weight;currentHeight=height;
    }
    double weight(){return currentWeight;}
    double height(){return currentHeight;}
    long hits(){return hits;}
    long misses(){return misses;}
}
