package org.realscale.neoforge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Pure state-machine tests for conservative underground Distant Horizons LOD visibility. */
final class AdaptiveVisibilityBudgetTest {
    private static final AdaptiveVisibilityBudget.Observation OPEN =
            new AdaptiveVisibilityBudget.Observation(true,true,true,false,false,false,false);
    private static final AdaptiveVisibilityBudget.Observation CAVE =
            new AdaptiveVisibilityBudget.Observation(true,false,false,true,false,false,false);
    private static final AdaptiveVisibilityBudget.Observation UNKNOWN =
            new AdaptiveVisibilityBudget.Observation(false,false,false,false,false,false,false);
    private static final AdaptiveVisibilityBudget.Observation RAVINE =
            new AdaptiveVisibilityBudget.Observation(true,true,true,false,true,false,false);

    @Test void caveLodsCullOnlyAfterThreeConfidentOpenSurfaceSamples() {
        AdaptiveVisibilityBudget.Tracker tracker=new AdaptiveVisibilityBudget.Tracker();
        assertFalse(AdaptiveVisibilityBudget.shouldCullDistantHorizonCaves(tracker.mode()));
        assertEquals(AdaptiveVisibilityBudget.Mode.TRANSITION,tracker.observe(OPEN));
        assertFalse(AdaptiveVisibilityBudget.shouldCullDistantHorizonCaves(tracker.mode()));
        assertEquals(AdaptiveVisibilityBudget.Mode.TRANSITION,tracker.observe(OPEN));
        assertFalse(AdaptiveVisibilityBudget.shouldCullDistantHorizonCaves(tracker.mode()));
        assertEquals(AdaptiveVisibilityBudget.Mode.OPEN_SURFACE,tracker.observe(OPEN));
        assertTrue(AdaptiveVisibilityBudget.shouldCullDistantHorizonCaves(tracker.mode()));
    }

    @Test void firstConfidentUndergroundSampleImmediatelyRevealsCaveLods() {
        AdaptiveVisibilityBudget.Tracker tracker=new AdaptiveVisibilityBudget.Tracker();
        tracker.observe(OPEN);tracker.observe(OPEN);tracker.observe(OPEN);
        assertTrue(AdaptiveVisibilityBudget.shouldCullDistantHorizonCaves(tracker.mode()));
        assertEquals(AdaptiveVisibilityBudget.Mode.TRANSITION,tracker.observe(CAVE));
        assertFalse(AdaptiveVisibilityBudget.shouldCullDistantHorizonCaves(tracker.mode()));
        assertEquals(AdaptiveVisibilityBudget.Mode.TRANSITION,tracker.observe(CAVE));
        assertEquals(AdaptiveVisibilityBudget.Mode.SEALED_CAVE,tracker.observe(CAVE));
        assertFalse(AdaptiveVisibilityBudget.shouldCullDistantHorizonCaves(tracker.mode()));
    }

    @Test void unknownDataAndRavinesAlwaysFailOpenImmediately() {
        for(AdaptiveVisibilityBudget.Observation uncertain : new AdaptiveVisibilityBudget.Observation[]{UNKNOWN,RAVINE}) {
            AdaptiveVisibilityBudget.Tracker tracker=new AdaptiveVisibilityBudget.Tracker();
            tracker.observe(OPEN);tracker.observe(OPEN);tracker.observe(OPEN);
            assertTrue(AdaptiveVisibilityBudget.shouldCullDistantHorizonCaves(tracker.mode()));
            assertEquals(AdaptiveVisibilityBudget.Mode.TRANSITION,tracker.observe(uncertain));
            assertFalse(AdaptiveVisibilityBudget.shouldCullDistantHorizonCaves(tracker.mode()));
        }
    }

    @Test void stableSamplingSlowsOnlyAfterClassificationAndPlayerMovementSpeedsItUp() {
        assertEquals(10,AdaptiveVisibilityBudget.samplingIntervalTicks(AdaptiveVisibilityBudget.Mode.TRANSITION,0.0));
        assertEquals(40,AdaptiveVisibilityBudget.samplingIntervalTicks(AdaptiveVisibilityBudget.Mode.OPEN_SURFACE,0.0));
        assertEquals(10,AdaptiveVisibilityBudget.samplingIntervalTicks(AdaptiveVisibilityBudget.Mode.OPEN_SURFACE,16.01));
        assertEquals(40,AdaptiveVisibilityBudget.samplingIntervalTicks(AdaptiveVisibilityBudget.Mode.SEALED_CAVE,4.0));
    }

    @Test void playerVisibleWorkAlwaysRetainsHighestPriority() {
        for(AdaptiveVisibilityBudget.Mode mode:AdaptiveVisibilityBudget.Mode.values())
            assertEquals(100,AdaptiveVisibilityBudget.priority(mode,AdaptiveVisibilityBudget.Task.VISIBLE_OR_PLAYER_CHANGED));
    }
}
