package org.realscale.neoforge;

/**
 * Tiny client-render-thread frame-pressure signal for the optional vanilla
 * upload pacing experiment. Never requests or inspects any Minecraft chunk.
 *
 * Measured durations are render-event Pre-to-Post wall time, not GPU time.
 * Two slow frames engage the guard; four healthy frames disengage it.
 * Large pauses and invalid deltas reset only the observation streak.
 */
public final class ClientUploadFrameGovernor {
    private static final long SLOW_FRAME_NANOS=28_000_000L;
    private static final long HEALTHY_FRAME_NANOS=18_000_000L;
    private static final long MAX_VALID_FRAME_NANOS=500_000_000L;
    private static final int SLOW_STREAK=2;
    private static final int HEALTHY_STREAK=4;

    private int slowStreak,healthyStreak;
    private boolean constrained;
    private long slowFrames;

    public void observe(long elapsedNanos) {
        if(elapsedNanos<=0 || elapsedNanos>MAX_VALID_FRAME_NANOS) {
            slowStreak=healthyStreak=0;
            return;
        }
        if(elapsedNanos>=SLOW_FRAME_NANOS) {
            slowFrames++;
            slowStreak=Math.min(SLOW_STREAK,slowStreak+1);
            healthyStreak=0;
            if(slowStreak==SLOW_STREAK)constrained=true;
        } else if(elapsedNanos<=HEALTHY_FRAME_NANOS) {
            healthyStreak=Math.min(HEALTHY_STREAK,healthyStreak+1);
            slowStreak=0;
            if(healthyStreak==HEALTHY_STREAK)constrained=false;
        } else {
            slowStreak=healthyStreak=0;
        }
    }

    public boolean constrained() {return constrained;}
    public long slowFrames() {return slowFrames;}

    public void reset() {
        slowStreak=healthyStreak=0;
        constrained=false;
        slowFrames=0L;
    }
}
