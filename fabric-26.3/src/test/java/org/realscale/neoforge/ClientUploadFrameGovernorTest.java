package org.realscale.neoforge;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ClientUploadFrameGovernorTest {
    @Test void twoSlowFramesEngageAndFourHealthyFramesRestore() {
        ClientUploadFrameGovernor governor=new ClientUploadFrameGovernor();
        assertFalse(governor.constrained());
        governor.observe(28_000_000L);
        assertFalse(governor.constrained());
        governor.observe(29_000_000L);
        assertTrue(governor.constrained());
        assertEquals(2L,governor.slowFrames());
        for(int i=0;i<3;i++) {
            governor.observe(16_666_667L);
            assertTrue(governor.constrained());
        }
        governor.observe(18_000_000L);
        assertFalse(governor.constrained());
    }

    @Test void neutralFramesBreakStreaksAndPreventOscillation() {
        ClientUploadFrameGovernor governor=new ClientUploadFrameGovernor();
        governor.observe(32_000_000L);
        governor.observe(22_000_000L);
        governor.observe(32_000_000L);
        assertFalse(governor.constrained());
        governor.observe(32_000_000L);
        assertTrue(governor.constrained());
        governor.observe(12_000_000L);
        governor.observe(23_000_000L);
        for(int i=0;i<3;i++)governor.observe(12_000_000L);
        assertTrue(governor.constrained());
        governor.observe(12_000_000L);
        assertFalse(governor.constrained());
    }

    @Test void invalidAndSuspendedIntervalsAreIgnored() {
        ClientUploadFrameGovernor governor=new ClientUploadFrameGovernor();
        governor.observe(-1L);
        governor.observe(0);
        governor.observe(501_000_000L);
        assertFalse(governor.constrained());
        governor.observe(28_000_000L);
        governor.observe(600_000_000L);
        governor.observe(28_000_000L);
        assertFalse(governor.constrained());
        governor.observe(28_000_000L);
        assertTrue(governor.constrained());
        governor.observe(Long.MAX_VALUE);
        assertTrue(governor.constrained());
        governor.reset();
        assertFalse(governor.constrained());
        assertEquals(0L,governor.slowFrames());
    }

    @Test void pressureGuardOnlyChangesUploadQuotaWhenExplicitlyUsed() {
        ClientUploadFrameGovernor governor=new ClientUploadFrameGovernor();
        ClientChunkUploadBudget budget=new ClientChunkUploadBudget(2_000_000L,8);
        governor.observe(30_000_000L);
        governor.observe(30_000_000L);
        budget.beginModeAwareFrame(true,
                ClientChunkUploadBudget.VisibilityMode.SEALED_CAVE,
                128,governor.constrained());
        assertEquals(8,budget.frameMaxTasks());
        assertEquals(2_000_000L,budget.frameMaxNanos());
        budget.endFrame();
        for(int i=0;i<4;i++)governor.observe(16_000_000L);
        budget.beginModeAwareFrame(true,
                ClientChunkUploadBudget.VisibilityMode.SEALED_CAVE,
                128,governor.constrained());
        assertEquals(16,budget.frameMaxTasks());
        assertEquals(4_000_000L,budget.frameMaxNanos());
    }
}
