package org.realscale.neoforge;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ClientChunkUploadModeAwareTest {
    private ClientChunkUploadBudget newBudget() {
        return new ClientChunkUploadBudget(2_000_000L,8);
    }

    @Test void vanillaModeRetainsExactOriginalFrameLimits() {
        ClientChunkUploadBudget budget=newBudget();
        budget.beginFrame(true);
        assertEquals(8,budget.frameMaxTasks());
        assertEquals(2_000_000L,budget.frameMaxNanos());
        for(int n=0;n<8;n++) {
            assertTrue(budget.canTakeTask());
            budget.completed(100_000L);
        }
        assertFalse(budget.canTakeTask());
        budget.endFrame();
    }

    @Test void stableSurfaceUsesStandardBudgetAndCaveGetsDoubleCapacity() {
        ClientChunkUploadBudget budget=newBudget();
        budget.beginModeAwareFrame(true,
                ClientChunkUploadBudget.VisibilityMode.OPEN_SURFACE,0);
        assertEquals(8,budget.frameMaxTasks());
        assertEquals(2_000_000L,budget.frameMaxNanos());
        budget.beginModeAwareFrame(true,
                ClientChunkUploadBudget.VisibilityMode.SEALED_CAVE,0);
        assertEquals(16,budget.frameMaxTasks());
        assertEquals(4_000_000L,budget.frameMaxNanos());
    }

    @Test void uncertainTransitionsAlwaysUseOriginalVanillaDrain() {
        ClientChunkUploadBudget budget=newBudget();
        budget.beginModeAwareFrame(true,
                ClientChunkUploadBudget.VisibilityMode.TRANSITION,128);
        assertFalse(budget.active());
        assertFalse(budget.canTakeTask());
        assertEquals(1,budget.transitionBypasses());
        budget.beginModeAwareFrame(true,null,128);
        assertFalse(budget.active());
        assertEquals(2,budget.transitionBypasses());
    }

    @Test void backloggedSurfaceAndCaveMakeBoundedProgress() {
        ClientChunkUploadBudget budget=newBudget();
        budget.beginModeAwareFrame(true,
                ClientChunkUploadBudget.VisibilityMode.OPEN_SURFACE,63);
        assertEquals(8,budget.frameMaxTasks());
        budget.beginModeAwareFrame(true,
                ClientChunkUploadBudget.VisibilityMode.OPEN_SURFACE,64);
        assertEquals(16,budget.frameMaxTasks());
        assertEquals(4_000_000L,budget.frameMaxNanos());
        assertEquals(1,budget.backlogRescueFrames());
        for(int n=0;n<16;n++) {
            assertTrue(budget.canTakeTask());
            budget.completed(100_000L);
        }
        assertFalse(budget.canTakeTask());
        budget.beginModeAwareFrame(true,
                ClientChunkUploadBudget.VisibilityMode.SEALED_CAVE,1024);
        assertEquals(16,budget.frameMaxTasks());
        assertEquals(4_000_000L,budget.frameMaxNanos());
        assertEquals(2,budget.backlogRescueFrames());
    }

    @Test void limitsAreClampedAndMenusNeverInterceptUploads() {
        ClientChunkUploadBudget budget=new ClientChunkUploadBudget(10_000_000L,64);
        budget.beginModeAwareFrame(true,
                ClientChunkUploadBudget.VisibilityMode.SEALED_CAVE,Integer.MAX_VALUE);
        assertEquals(64,budget.frameMaxTasks());
        assertEquals(10_000_000L,budget.frameMaxNanos());
        budget.beginModeAwareFrame(false,
                ClientChunkUploadBudget.VisibilityMode.SEALED_CAVE,200);
        assertFalse(budget.active());
    }

    @Test void adaptivePressureDisablesTheExtraBoostButKeepsFifoProgress() {
        ClientChunkUploadBudget budget=newBudget();
        budget.beginModeAwareFrame(true,
                ClientChunkUploadBudget.VisibilityMode.SEALED_CAVE,128,true);
        assertTrue(budget.active());
        assertEquals(8,budget.frameMaxTasks());
        assertEquals(2_000_000L,budget.frameMaxNanos());
        assertEquals(1,budget.adaptiveReducedFrames());
        assertTrue(budget.canTakeTask());
        budget.completed(100_000L);
        assertEquals(1,budget.totalTasks());

        budget.beginModeAwareFrame(true,
                ClientChunkUploadBudget.VisibilityMode.OPEN_SURFACE,0,true);
        assertEquals(8,budget.frameMaxTasks());
        budget.beginModeAwareFrame(true,
                ClientChunkUploadBudget.VisibilityMode.TRANSITION,128,true);
        assertFalse(budget.active());
        assertEquals(1,budget.transitionBypasses());
    }

    @Test void optionalPredictionAvoidsStartingLikelyOverBudgetTask() {
        ClientChunkUploadBudget budget=newBudget();
        budget.beginModeAwareFrame(true,
                ClientChunkUploadBudget.VisibilityMode.OPEN_SURFACE,10,false,true);
        assertTrue(budget.canTakeTask());
        budget.completed(700_000L);
        assertEquals(700_000L,budget.estimatedTaskNanos());
        assertTrue(budget.canTakeTask());
        budget.completed(700_000L);
        assertEquals(700_000L,budget.estimatedTaskNanos());
        assertFalse(budget.canTakeTask());
        assertEquals(2,budget.tasksThisFrame());
        assertEquals(1L,budget.predictiveStops());
        // An ordinary mode-aware frame without the opt-in keeps the old
        // stopwatch-only semantics with the exact same accumulated cost.
        budget.beginModeAwareFrame(true,
                ClientChunkUploadBudget.VisibilityMode.OPEN_SURFACE,10);
        budget.completed(700_000L);
        budget.completed(700_000L);
        assertTrue(budget.canTakeTask());
    }

    @Test void predictionNeverPreventsFirstRunnableOrTransitionDrain() {
        ClientChunkUploadBudget budget=newBudget();
        budget.beginModeAwareFrame(true,
                ClientChunkUploadBudget.VisibilityMode.OPEN_SURFACE,1,false,true);
        budget.completed(9_000_000L);
        budget.beginModeAwareFrame(true,
                ClientChunkUploadBudget.VisibilityMode.OPEN_SURFACE,1,false,true);
        assertTrue(budget.canTakeTask());
        assertEquals(9_000_000L,budget.estimatedTaskNanos());
        budget.beginModeAwareFrame(true,
                ClientChunkUploadBudget.VisibilityMode.TRANSITION,128,false,true);
        assertFalse(budget.active());
        budget.resetPrediction();
        assertEquals(0L,budget.estimatedTaskNanos());
    }

    @Test void predictiveTaskEstimateIsBoundedAndClearedAcrossWorlds() {
        ClientChunkUploadBudget budget=newBudget();
        budget.beginModeAwareFrame(true,
                ClientChunkUploadBudget.VisibilityMode.SEALED_CAVE,8,false,true);
        budget.completed(Long.MAX_VALUE);
        assertEquals(20_000_000L,budget.estimatedTaskNanos());
        budget.resetPrediction();
        assertEquals(0L,budget.estimatedTaskNanos());
        budget.beginModeAwareFrame(true,
                ClientChunkUploadBudget.VisibilityMode.SEALED_CAVE,8,false,true);
        assertTrue(budget.canTakeTask());
        budget.completed(100_000L);
        assertEquals(100_000L,budget.estimatedTaskNanos());
    }

    @Test void perFrameQuotaResetsAndNoFinishedTaskIsLost() {
        ClientChunkUploadBudget budget=newBudget();
        budget.beginModeAwareFrame(true,
                ClientChunkUploadBudget.VisibilityMode.OPEN_SURFACE,0);
        assertTrue(budget.canTakeTask());
        budget.completed(3_000_000L);
        assertFalse(budget.canTakeTask());
        assertEquals(1,budget.totalTasks());
        budget.endFrame();
        budget.beginModeAwareFrame(true,
                ClientChunkUploadBudget.VisibilityMode.OPEN_SURFACE,0);
        assertTrue(budget.canTakeTask());
        assertEquals(0,budget.tasksThisFrame());
        budget.completed(1L);
        assertEquals(2,budget.totalTasks());
    }
}
