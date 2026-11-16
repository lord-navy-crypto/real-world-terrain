package org.realscale.neoforge.lotm;

import net.minecraft.core.BlockPos;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LotmResourceProvenanceTest {
    @Test
    void markedResourceDoesNotEarnFieldworkAndRemovalPrunesLedger() {
        LotmResourceProvenance ledger = new LotmResourceProvenance();
        BlockPos first = new BlockPos(2, -18, 8);
        BlockPos second = new BlockPos(-50, 42, 11);
        ledger.markPlaced(first);
        ledger.markPlaced(second);
        ledger.markPlaced(first);
        assertEquals(2, ledger.trackedPositions());
        assertTrue(ledger.consumePlacement(first));
        assertFalse(ledger.consumePlacement(first));
        assertEquals(1, ledger.trackedPositions());
        assertFalse(ledger.consumePlacement(BlockPos.ZERO));
        assertTrue(ledger.consumePlacement(second));
        assertEquals(0, ledger.trackedPositions());
    }
    @Test
    void explosionCleanupRemovesEveryAffectedPlacementInOneSaveMutation() {
        LotmResourceProvenance ledger = new LotmResourceProvenance();
        BlockPos first = new BlockPos(4, 30, 9);
        BlockPos second = new BlockPos(-22, 70, 6);
        ledger.markPlaced(first);
        ledger.markPlaced(second);
        assertEquals(2, ledger.consumePlacements(List.of(first, BlockPos.ZERO, second)));
        assertEquals(0, ledger.trackedPositions());
        assertEquals(0, ledger.consumePlacements(List.of(first, second)));
    }
}
