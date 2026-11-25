package org.realscale.neoforge;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class GpuTimestampStatsTest {
    @Test void emptyWindowIsZeroAndPercentilesAreDefined() {
        GpuTimestampStats stats=new GpuTimestampStats();
        assertEquals(0,stats.samples());
        assertEquals(0.0,stats.meanMs());
        assertEquals(0.0,stats.percentileUpperMs(.95));
    }
    @Test void recordsBoundedPercentilesAndLongFrameCount() {
        GpuTimestampStats stats=new GpuTimestampStats();
        stats.add(1_000_000L);
        stats.add(2_000_000L);
        stats.add(4_000_000L);
        stats.add(60_000_000L);
        assertEquals(4,stats.samples());
        assertEquals(16.75,stats.meanMs(),1.0e-9);
        assertEquals(1.0,stats.percentileUpperMs(.25));
        assertEquals(2.0,stats.percentileUpperMs(.50));
        assertTrue(stats.percentileUpperMs(.95)>=60.0);
        assertEquals(1,stats.over50ms());
    }
    @Test void ignoresInvalidSamplesAndResets() {
        GpuTimestampStats stats=new GpuTimestampStats();
        stats.add(0);
        stats.add(-1);
        assertEquals(0,stats.samples());
        stats.add(2_000_000L);
        stats.reset();
        assertEquals(0,stats.samples());
        assertEquals(0.0,stats.maxMs());
    }
}
