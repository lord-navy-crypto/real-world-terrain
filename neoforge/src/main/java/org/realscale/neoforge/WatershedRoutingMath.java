package org.realscale.neoforge;

import java.util.ArrayDeque;

/**
 * Deterministic D8 drainage-graph building block for RealScale worldgen.
 *
 * <p>This is a REGIONAL hydrology primitive, not a Minecraft fluid simulator.
 * A globally addressable elevation sampler ensures that routing direction at
 * a tile edge does not change with generation order. Accumulation is local to
 * the supplied rectangle: an actual complete watershed needs a larger region,
 * boundary inflow exchange or precomputed upstream drainage data. Do not
 * directly interpret truncated edge accumulation as full river discharge.
 *
 * <p>One height sample per interior cell plus exterior halo; O(width*height)
 * memory, O(width*height) graph processing. No chunk loads and no writes.
 */
public final class WatershedRoutingMath {
    private WatershedRoutingMath() {}

    @FunctionalInterface
    public interface Elevation {
        int at(int absoluteX, int absoluteZ);
    }

    @FunctionalInterface
    public interface Runoff {
        long at(int absoluteX, int absoluteZ);
    }

    /** Cardinal directions first in equal-slope situations. */
    private static final int[] DX = {1, -1, 0, 0, 1, 1, -1, -1};
    private static final int[] DZ = {0, 0, 1, -1, 1, -1, 1, -1};
    private static final double DIAGONAL_INV = 1.0 / Math.sqrt(2.0);

    /** Immutable after build(): safe to share with chunk-generation threads. */
    public static final class Tile {
        public final int originX, originZ, width, height, cellSize;
        private final int[] elevation, downstreamX, downstreamZ, downstreamIndex, streamOrder;
        private final long[] runoff, accumulatedRunoff;

        private Tile(int originX, int originZ, int width, int height, int cellSize,
                     int[] elevation, int[] downstreamX, int[] downstreamZ,
                     int[] downstreamIndex, int[] streamOrder,
                     long[] runoff, long[] accumulatedRunoff) {
            this.originX=originX; this.originZ=originZ;
            this.width=width; this.height=height; this.cellSize=cellSize;
            this.elevation=elevation; this.downstreamX=downstreamX;
            this.downstreamZ=downstreamZ; this.downstreamIndex=downstreamIndex;
            this.streamOrder=streamOrder;
            this.runoff=runoff; this.accumulatedRunoff=accumulatedRunoff;
        }

        private int index(int col, int row) {
            if (col<0 || col>=width || row<0 || row>=height)
                throw new IndexOutOfBoundsException(col + "," + row);
            return row*width+col;
        }
        public int elevation(int col, int row) { return elevation[index(col,row)]; }
        public int downstreamX(int col, int row) { return downstreamX[index(col,row)]; }
        public int downstreamZ(int col, int row) { return downstreamZ[index(col,row)]; }
        public int downstreamIndex(int col, int row) { return downstreamIndex[index(col,row)]; }
        public long localRunoff(int col, int row) { return runoff[index(col,row)]; }
        public long accumulation(int col, int row) { return accumulatedRunoff[index(col,row)]; }
        public int streamOrder(int col, int row) { return streamOrder[index(col,row)]; }

        /** Outgoing flow to neighboring tiles must be stitched by the caller. */
        public long edgeOutflow(int col, int row) {
            int i=index(col,row);
            return downstreamIndex[i]<0 && downstreamX[i]!=Integer.MIN_VALUE
                    ? accumulatedRunoff[i] : 0L;
        }
    }

    public static Tile solve(int originX, int originZ, int width, int height,
                             int cellSize, Elevation elevation, Runoff runoff) {
        if (width<1 || height<1 || cellSize<1 || (long)width*height>262144)
            throw new IllegalArgumentException("Expected bounded regional grid");
        if (elevation==null || runoff==null)
            throw new IllegalArgumentException("Elevation and runoff required");
        int n=width*height;
        int[] heights=new int[n], downX=new int[n], downZ=new int[n];
        int[] receiver=new int[n], remainingIncoming=new int[n], order=new int[n];
        int[] topOrder=new int[n], topOrderCount=new int[n];
        long[] local=new long[n], accumulated=new long[n];
        for (int row=0;row<height;row++) for (int col=0;col<width;col++) {
            int i=row*width+col;
            int x=Math.toIntExact((long)originX+(long)col*cellSize);
            int z=Math.toIntExact((long)originZ+(long)row*cellSize);
            heights[i]=elevation.at(x,z);
            local[i]=runoff.at(x,z);
            if(local[i]<0)throw new IllegalArgumentException("Negative runoff");
            accumulated[i]=local[i];
            downX[i]=Integer.MIN_VALUE;
            downZ[i]=Integer.MIN_VALUE;
            receiver[i]=-1;
        }

        // Directions use globally sampled elevations, even at grid edges.
        // Strictly descending edges are acyclic; local sinks represent lakes
        // or flat depressions awaiting a proper lake/spillway solver.
        for (int row=0;row<height;row++) for (int col=0;col<width;col++) {
            int i=row*width+col;
            int x=Math.toIntExact((long)originX+(long)col*cellSize);
            int z=Math.toIntExact((long)originZ+(long)row*cellSize);
            double bestSlope=0.0;
            int bestDirection=-1;
            for (int d=0;d<8;d++) {
                int nc=col+DX[d], nr=row+DZ[d];
                int nx=Math.toIntExact((long)x+(long)DX[d]*cellSize);
                int nz=Math.toIntExact((long)z+(long)DZ[d]*cellSize);
                int nextHeight=nc>=0 && nc<width && nr>=0 && nr<height
                        ? heights[nr*width+nc] : elevation.at(nx,nz);
                long drop=(long)heights[i]-nextHeight;
                if(drop<=0)continue;
                double slope=drop * (d<4 ? 1.0 : DIAGONAL_INV);
                if(slope>bestSlope) {
                    bestSlope=slope;
                    bestDirection=d;
                }
            }
            if(bestDirection<0)continue;
            int nx=col+DX[bestDirection], nz=row+DZ[bestDirection];
            downX[i]=Math.toIntExact((long)x+(long)DX[bestDirection]*cellSize);
            downZ[i]=Math.toIntExact((long)z+(long)DZ[bestDirection]*cellSize);
            if(nx>=0 && nx<width && nz>=0 && nz<height) {
                receiver[i]=nz*width+nx;
                remainingIncoming[receiver[i]]++;
            }
        }

        ArrayDeque<Integer> queue=new ArrayDeque<>();
        for(int i=0;i<n;i++)if(remainingIncoming[i]==0)queue.addLast(i);
        int processed=0;
        while(!queue.isEmpty()) {
            int i=queue.removeFirst();
            processed++;
            order[i]=topOrder[i]==0 ? 1
                    : topOrder[i]+(topOrderCount[i]>=2?1:0);
            int next=receiver[i];
            if(next<0)continue;
            accumulated[next]=Math.addExact(accumulated[next],accumulated[i]);
            if(order[i]>topOrder[next]) {
                topOrder[next]=order[i];
                topOrderCount[next]=1;
            } else if(order[i]==topOrder[next]) {
                topOrderCount[next]++;
            }
            if(--remainingIncoming[next]==0)queue.addLast(next);
        }
        if(processed!=n)throw new IllegalStateException("Drainage contains a cycle");
        return new Tile(originX,originZ,width,height,cellSize,heights,
                downX,downZ,receiver,order,local,accumulated);
    }
}
