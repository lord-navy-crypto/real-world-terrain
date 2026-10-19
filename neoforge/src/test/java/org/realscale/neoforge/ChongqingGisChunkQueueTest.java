package org.realscale.neoforge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ACTUAL pinned 500m OSM source, not synthetic city geometry. Proves that
 * per-chunk scheduling cannot drop, duplicate or retarget a V2 shell write.
 * Does not pretend to have placed blocks in a running Minecraft client.
 */
class ChongqingGisChunkQueueTest {
    @Test void entireGenuineOSMPlanIsVisitedExactlyOnceByItsCorrectChunk()
            throws Exception {
        ChongqingGisPlan plan=ChongqingGisPlan.readBundled();
        ChongqingGisChunkQueue work=new ChongqingGisChunkQueue(plan);
        assertEquals(plan.blockWrites,work.totalWrites());
        assertTrue(work.remainingChunks()>30);
        assertNull(work.peek(200,200),"Faraway chunks cannot appear");
        int[] nearby=work.nearestPendingChunk(0,0);
        assertNotNull(nearby);
        assertTrue(work.hasPending(nearby[0],nearby[1]));
        assertTrue(Math.abs(nearby[0])<=32 && Math.abs(nearby[1])<=32);

        long visits=0;
        int visitedChunks=0;
        int validFacade=0;
        int validRoof=0;
        for (int cz=-32;cz<=31;cz++) {
            for (int cx=-32;cx<=31;cx++) {
                if (!work.hasPending(cx,cz)) continue;
                visitedChunks++;
                long lastVisits=visits;
                ChongqingGisChunkQueue.Placement placement;
                while ((placement=work.peek(cx,cz))!=null) {
                    ChongqingGisPlan.Row row=plan.rows.get(placement.rowIndex());
                    assertEquals(cx,Math.floorDiv(placement.x(),16));
                    assertEquals(cz,Math.floorDiv(row.z(),16));
                    assertTrue(placement.x()>=row.x0());
                    assertTrue(placement.x()<=row.x1());
                    int o=placement.yOffset();
                    assertTrue(o>=0 && o<=row.height());
                    if ("roof".equals(row.shell()) && o!=0)
                        assertEquals(row.height(),o,
                            "No interior block may be written in roof-only cell");
                    if ("facade".equals(row.shell()) && o>0)validFacade++;
                    if ("roof".equals(row.shell()) && o>0)validRoof++;
                    work.acknowledge(cx,cz);
                    visits++;
                }
                assertTrue(visits>lastVisits,"A source chunk must contain work");
                assertFalse(work.hasPending(cx,cz));
            }
        }
        assertTrue(visitedChunks>30);
        assertTrue(validFacade>0);
        assertTrue(validRoof>0);
        assertEquals(494_988L,visits);
        assertEquals(plan.blockWrites,visits);
        assertEquals(visits,work.completedWrites());
        assertEquals(0,work.remainingChunks());
        assertTrue(work.isComplete());
        assertNull(work.nearestPendingChunk(0,0));
        assertThrows(IllegalStateException.class,()->work.acknowledge(0,0));
    }

    @Test void negativeWorldCoordinatesUseJavaFloorChunkDivision() {
        assertEquals(ChongqingGisChunkQueue.key(-1,-1),
                ChongqingGisChunkQueue.key(
                        Math.floorDiv(-1,16),Math.floorDiv(-1,16)));
        assertNotEquals(ChongqingGisChunkQueue.key(-1,0),
                ChongqingGisChunkQueue.key(0,0));
        assertNotEquals(ChongqingGisChunkQueue.key(-2,1),
                ChongqingGisChunkQueue.key(-1,1));
    }
}
