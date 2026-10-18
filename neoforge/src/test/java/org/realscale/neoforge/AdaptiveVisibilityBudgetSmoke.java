package org.realscale.neoforge;

/**
 * RealScale's opt-in advisory classifier regression. No client or GPU needed.
 * Crucial acceptance: high-Y sealed caves must not be forced into surface,
 * low-Y open canyon surfaces must not be forced into cave, and ambiguous
 * portals immediately restore all normal work.
 */
public final class AdaptiveVisibilityBudgetSmoke {
    private AdaptiveVisibilityBudgetSmoke() {}
    private static void yes(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static AdaptiveVisibilityBudget.Observation open() {
        return new AdaptiveVisibilityBudget.Observation(
                true,true,true,false,false,false);
    }
    private static AdaptiveVisibilityBudget.Observation cave() {
        return new AdaptiveVisibilityBudget.Observation(
                true,false,false,true,false,false);
    }
    private static AdaptiveVisibilityBudget.Observation portal() {
        return new AdaptiveVisibilityBudget.Observation(
                true,false,true,true,true,false);
    }
    public static void main(String[] args) {
        var tracker=new AdaptiveVisibilityBudget.Tracker();
        var T=AdaptiveVisibilityBudget.Mode.TRANSITION;
        var S=AdaptiveVisibilityBudget.Mode.OPEN_SURFACE;
        var C=AdaptiveVisibilityBudget.Mode.SEALED_CAVE;
        yes(tracker.mode()==T,"Default unknown environment must remain conservative");
        yes(tracker.observe(open())==T,"No instant surface classification");
        yes(tracker.observe(open())==T,"Surface needs sustained exposure");
        yes(tracker.observe(open())==S,"Stable open surface should classify");
        yes(tracker.observe(cave())==T,"Entering a cave must first fall back");
        yes(tracker.observe(cave())==T,"Cave needs sustained enclosure");
        yes(tracker.observe(cave())==C,"Confirmed sealed cave should classify");
        yes(tracker.observe(portal())==T,"Skylight/open cave exit must fail open");
        yes(tracker.observe(open())==T,"One open sample insufficient");
        yes(tracker.observe(open())==T,"Still waiting for stable open samples");
        yes(tracker.observe(open())==S,"Return to surface after stable exposure");
        yes(tracker.observe(new AdaptiveVisibilityBudget.Observation(
                false,false,false,true,false,false))==T,
                "Unloaded/unknown chunks must immediately disable advice");
        yes(tracker.observe(new AdaptiveVisibilityBudget.Observation(
                true,true,true,false,false,true))==T,
                "Fast travel or teleport must fall back to normal priorities");
        yes(tracker.observe(new AdaptiveVisibilityBudget.Observation(
                true,false,false,false,false,false))==T,
                "Open cavern without positive roof evidence is ambiguous");
        yes(tracker.samples()==13,"Unexpected sampling");
        yes(tracker.transitions()==6,"No extra mode oscillations");

        // Pure observation classification does not use camera or surface Y.
        for(int y:new int[]{-64,0,70,240,800,1500,2031}) {
            yes(AdaptiveVisibilityBudget.classify(open())==S,
                    "High/low surface misclassified at "+y);
            yes(AdaptiveVisibilityBudget.classify(cave())==C,
                    "High/low cave misclassified at "+y);
        }

        // Never prioritize a background job ahead of a visible update.
        for (var mode:AdaptiveVisibilityBudget.Mode.values()) {
            int urgent=AdaptiveVisibilityBudget.priority(
                    mode,AdaptiveVisibilityBudget.Task.VISIBLE_OR_PLAYER_CHANGED);
            yes(urgent==100,"All visible updates must remain urgent");
            for (var task:AdaptiveVisibilityBudget.Task.values()) {
                int p=AdaptiveVisibilityBudget.priority(mode,task);
                yes(p>=20 && p<=urgent,
                    "A task was starved or outranked a visible Section");
            }
        }
        yes(AdaptiveVisibilityBudget.priority(S,
                AdaptiveVisibilityBudget.Task.DISTANT_SURFACE_MESH)
             >AdaptiveVisibilityBudget.priority(S,
                AdaptiveVisibilityBudget.Task.NEAR_CAVE_MESH),
             "Open horizon work should be preferred when exposed");
        yes(AdaptiveVisibilityBudget.priority(C,
                AdaptiveVisibilityBudget.Task.NEAR_CAVE_MESH)
             >AdaptiveVisibilityBudget.priority(C,
                AdaptiveVisibilityBudget.Task.OPTIONAL_HORIZON_LOD),
             "Enclosed cave work should be preferred when sheltered");
        yes(AdaptiveVisibilityBudget.priority(T,
                AdaptiveVisibilityBudget.Task.DISTANT_SURFACE_MESH)
             ==AdaptiveVisibilityBudget.priority(T,
                AdaptiveVisibilityBudget.Task.NEAR_CAVE_MESH),
             "A cave mouth must preserve both work classes");

        tracker.reset();
        yes(tracker.mode()==T && tracker.transitions()==0 && tracker.samples()==0,
            "World switch must reset prior visibility state");
        System.out.println("REAL_SCALE_ADAPTIVE_VISIBILITY_SMOKE_PASS");
    }
}
