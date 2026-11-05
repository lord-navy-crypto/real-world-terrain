package org.realscale.neoforge;

import org.junit.jupiter.api.Test;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

/** Pure Java proof: memo evictions and worker isolation cannot alter X/Z data. */
public class MeasuredHorizontalMemoDensityTest {
    private static double sampled(int x,int z,double seed) {
        return seed + x*0.03125 + z*0.0078125;
    }

    @Test void a2096HighColumnUsesOneHorizontalSample() {
        var memo=new MeasuredColumnMemo();
        int computes=0;
        for(int y=-64;y<2032;y++) {
            if(!memo.lookup(1024,1024)) {
                computes++;
                memo.store(sampled(1024,1024,2.25));
            }
            assertEquals(Double.doubleToRawLongBits(sampled(1024,1024,2.25)),
                    Double.doubleToRawLongBits(memo.value()));
        }
        assertEquals(1,computes);
    }

    @Test void evictionAndSignedCoordinatesAreExact() {
        var memo=new MeasuredColumnMemo();
        for(int i=0;i<16000;i++) {
            int x=(i*73)%30001-15000;
            int z=(i*127)%32003-16001;
            if(!memo.lookup(x,z)) memo.store(sampled(x,z,0.75));
            assertEquals(Double.doubleToRawLongBits(sampled(x,z,0.75)),
                    Double.doubleToRawLongBits(memo.value()));
        }
    }

    @Test void separateSeedCachesCannotLeakAcrossWorlds() {
        var first=new MeasuredColumnMemo();
        var second=new MeasuredColumnMemo();
        assertFalse(first.lookup(1024,-512));
        first.store(sampled(1024,-512,2.0));
        assertFalse(second.lookup(1024,-512));
        second.store(sampled(1024,-512,42.0));
        assertNotEquals(Double.doubleToRawLongBits(first.value()),
                Double.doubleToRawLongBits(second.value()));
    }

    @Test void nanAndSignedZeroRetainTheirExactBits() {
        var memo=new MeasuredColumnMemo();
        assertFalse(memo.lookup(-1,-2));
        memo.store(-0.0);
        assertTrue(memo.lookup(-1,-2));
        assertEquals(Double.doubleToRawLongBits(-0.0),
                Double.doubleToRawLongBits(memo.value()));
        assertFalse(memo.lookup(-1,-3));
        memo.store(Double.longBitsToDouble(0x7ff8000000000001L));
        assertTrue(memo.lookup(-1,-3));
        assertEquals(0x7ff8000000000001L,
                Double.doubleToRawLongBits(memo.value()));
    }

    @Test void independentlyAllocatedWorkerCachesStayIsolated() {
        IntStream.range(0,8).parallel().forEach(worker->{
            var memo=new MeasuredColumnMemo();
            for(int i=0;i<4096;i++) {
                int x=i%97-48;
                int z=i%67-33;
                if(!memo.lookup(x,z))
                    memo.store(sampled(x,z,worker));
                assertEquals(Double.doubleToRawLongBits(sampled(x,z,worker)),
                        Double.doubleToRawLongBits(memo.value()));
            }
        });
    }
}
