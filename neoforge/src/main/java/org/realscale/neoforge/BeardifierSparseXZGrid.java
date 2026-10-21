package org.realscale.neoforge;

import java.util.ArrayList;
import java.util.BitSet;

/**
 * Optional coarse spatial support index for vanilla Beardifier.
 *
 * Each vanilla structure piece/junction is expanded by 32 blocks in X/Z
 * before any occupancy cells are marked. Every 16x16 cell that intersects
 * the expanded support is marked, even if only one corner intersects.
 * Thus empty cells are provably outside all recorded vanilla influences.
 * Grid over 4096 cells or unexpected metadata FAILS OPEN (vanilla).
 *
 * This only optimizes CPU worldgen. It never changes render culling.
 */
public final class BeardifierSparseXZGrid {
    private static final int GUARD=32;
    private static final long CELL=16;
    private static final int MAX_CELLS=4096;
    private static final int MAX_ENTRIES=4096;

    private record Rectangle(long minX,long maxX,long minZ,long maxZ) {}
    private final ArrayList<Rectangle> rectangles=new ArrayList<>();
    private boolean disabled,ready;
    private long cellMinX,cellMinZ;
    private int cellsX,cellsZ;
    private BitSet occupied;

    public void includePiece(int minX,int maxX,int minZ,int maxZ){
        if(minX>maxX || minZ>maxZ){disable();return;}
        add((long)minX-GUARD,(long)maxX+GUARD,
                (long)minZ-GUARD,(long)maxZ+GUARD);
    }
    public void includeJunction(int x,int z){
        add((long)x-GUARD,(long)x+GUARD,
                (long)z-GUARD,(long)z+GUARD);
    }
    private void add(long minX,long maxX,long minZ,long maxZ){
        if(disabled || ready)return;
        if(rectangles.size()>=MAX_ENTRIES){disable();return;}
        rectangles.add(new Rectangle(minX,maxX,minZ,maxZ));
    }
    public void disable(){
        disabled=true;
        ready=false;
        occupied=null;
        rectangles.clear();
    }
    public boolean enabled(){return ready&&!disabled;}
    public void finish(){
        if(disabled||ready)return;
        if(rectangles.isEmpty()){
            cellMinX=cellMinZ=0;
            cellsX=cellsZ=0;
            occupied=new BitSet();
            ready=true;
            return;
        }
        long minCX=Long.MAX_VALUE,maxCX=Long.MIN_VALUE;
        long minCZ=Long.MAX_VALUE,maxCZ=Long.MIN_VALUE;
        for(Rectangle rect:rectangles){
            minCX=Math.min(minCX,Math.floorDiv(rect.minX(),CELL));
            maxCX=Math.max(maxCX,Math.floorDiv(rect.maxX(),CELL));
            minCZ=Math.min(minCZ,Math.floorDiv(rect.minZ(),CELL));
            maxCZ=Math.max(maxCZ,Math.floorDiv(rect.maxZ(),CELL));
        }
        long width=maxCX-minCX+1,height=maxCZ-minCZ+1;
        if(width<=0||height<=0||width>MAX_CELLS
                ||height>MAX_CELLS ||width*height>MAX_CELLS){
            disable();
            return;
        }
        cellsX=(int)width;cellsZ=(int)height;
        cellMinX=minCX;cellMinZ=minCZ;
        occupied=new BitSet(cellsX*cellsZ);
        for(Rectangle rect:rectangles){
            long lowZ=Math.floorDiv(rect.minZ(),CELL);
            long highZ=Math.floorDiv(rect.maxZ(),CELL);
            long lowX=Math.floorDiv(rect.minX(),CELL);
            long highX=Math.floorDiv(rect.maxX(),CELL);
            for(long z=lowZ;z<=highZ;z++){
                int row=(int)(z-cellMinZ)*cellsX;
                for(long x=lowX;x<=highX;x++)
                    occupied.set(row+(int)(x-cellMinX));
            }
        }
        rectangles.clear();
        ready=true;
    }
    /**
     * True ONLY for an empty cell outside every piece/junction support.
     * NOT an occlusion test: deep caves and surfaces remain unchanged.
     */
    public boolean canSkip(int blockX,int blockZ){
        if(!enabled())return false;
        if(cellsX==0)return true;
        long x=Math.floorDiv((long)blockX,CELL)-cellMinX;
        long z=Math.floorDiv((long)blockZ,CELL)-cellMinZ;
        if(x<0||z<0||x>=cellsX||z>=cellsZ)return true;
        return !occupied.get((int)z*cellsX+(int)x);
    }
    public int cells(){return cellsX*cellsZ;}
    public int guard(){return GUARD;}
}
