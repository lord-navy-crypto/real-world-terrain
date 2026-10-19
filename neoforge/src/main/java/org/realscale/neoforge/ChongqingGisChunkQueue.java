package org.realscale.neoforge;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic, source-preserving per-chunk cursor for the authentic OSM plan.
 *
 * The GIS rows remain untouched. Each horizontal row is split on REAL
 * Minecraft 16x16 chunk boundaries, including negative coordinates.
 * Unloaded chunks are never marked done and are never force-loaded.
 *
 * Server-thread-only state: no chat commands, file writes, or Minecraft API
 * calls. Actual block writes are performed by the guarded GUI executor.
 */
public final class ChongqingGisChunkQueue {
    public record Placement(int rowIndex, int x, int yOffset) {}

    private static final class Slice {
        final int rowIndex;
        final int lastX;
        int x;
        int offset;

        Slice(int rowIndex,int firstX,int lastX) {
            this.rowIndex=rowIndex;
            this.x=firstX;
            this.lastX=lastX;
        }
    }

    private final List<ChongqingGisPlan.Row> rows;
    private final Map<Long,ArrayDeque<Slice>> chunks=new HashMap<>();
    private final long totalWrites;
    private long completedWrites;

    static long key(int cx,int cz) {
        return ((long)cx<<32) | (cz & 0xffffffffL);
    }

    public ChongqingGisChunkQueue(ChongqingGisPlan plan) {
        rows=plan.rows;
        totalWrites=plan.blockWrites;
        long accounted=0;
        for (int index=0;index<rows.size();index++) {
            ChongqingGisPlan.Row row=rows.get(index);
            int x=row.x0();
            while (x<=row.x1()) {
                int cx=Math.floorDiv(x,16);
                int last=Math.min(row.x1(),(cx+1)*16-1);
                long chunk=key(cx,Math.floorDiv(row.z(),16));
                chunks.computeIfAbsent(chunk,k->new ArrayDeque<>())
                        .addLast(new Slice(index,x,last));
                accounted+=(long)(last-x+1) *
                        (1 + ("facade".equals(row.shell()) ? row.height()
                              : "roof".equals(row.shell()) ? 1 : 0));
                x=last+1;
            }
        }
        if (accounted!=totalWrites || chunks.isEmpty())
            throw new IllegalArgumentException(
                    "GIS chunked plan writes differ from immutable row count");
    }

    public boolean hasPending(int chunkX,int chunkZ) {
        return chunks.containsKey(key(chunkX,chunkZ));
    }

    public Placement peek(int chunkX,int chunkZ) {
        ArrayDeque<Slice> work=chunks.get(key(chunkX,chunkZ));
        if (work==null || work.isEmpty()) return null;
        Slice s=work.peekFirst();
        return new Placement(s.rowIndex,s.x,s.offset);
    }

    /** Call exactly once AFTER an individual world placement succeeded. */
    public void acknowledge(int chunkX,int chunkZ) {
        long k=key(chunkX,chunkZ);
        ArrayDeque<Slice> work=chunks.get(k);
        if (work==null || work.isEmpty())
            throw new IllegalStateException("No active GIS chunk task");
        Slice s=work.peekFirst();
        ChongqingGisPlan.Row row=rows.get(s.rowIndex);
        s.offset=row.nextOffsetAfter(s.offset);
        if (s.offset>row.height()) {
            s.offset=0;
            s.x++;
            if (s.x>s.lastX) work.removeFirst();
        }
        if (work.isEmpty()) chunks.remove(k);
        completedWrites++;
        if (completedWrites>totalWrites)
            throw new IllegalStateException("GIS executor exceeded plan writes");
    }

    public long completedWrites() { return completedWrites; }
    public long totalWrites() { return totalWrites; }
    public int remainingChunks() { return chunks.size(); }
    public boolean isComplete() {
        return chunks.isEmpty() && completedWrites==totalWrites;
    }
}
