package org.realscale.neoforge;

/**
 * Small, exact per-render-frame scheduler for already-built vanilla section uploads.
 *
 * Never drops a queued Runnable: the caller leaves remaining queue entries for
 * Minecraft's next render frame. One task always completes before deciding
 * whether to stop. No terrain/block/render-distance modification.
 *
 * Mutable only on the physical client's render thread.
 */
public final class ClientChunkUploadBudget {
    private final long maxNanos;
    private final int maxTasks;
    private boolean inFrame;
    private int tasks;
    private long spentNanos;
    private long throttledCalls;
    private long totalTasks;
    private long frameMaxNanos;
    private int frameMaxTasks;
    private long transitionBypasses;
    private long backlogRescueFrames;
    private long adaptiveReducedFrames;
    private boolean predictiveThisFrame;
    private long estimatedTaskNanos;
    private long predictiveStops;

    /** Explicit local categories; this pure-Java scheduler has no client dependencies. */
    public enum VisibilityMode { TRANSITION, OPEN_SURFACE, SEALED_CAVE }

    public ClientChunkUploadBudget(long maxNanos,int maxTasks) {
        if(maxNanos<100_000L || maxNanos>20_000_000L ||
                maxTasks<1 || maxTasks>64)
            throw new IllegalArgumentException("Invalid client upload budget");
        this.maxNanos=maxNanos;
        this.maxTasks=maxTasks;
        frameMaxNanos=maxNanos;
        frameMaxTasks=maxTasks;
    }

    public void beginFrame(boolean gameplay) {
        inFrame=gameplay;
        tasks=0;
        spentNanos=0L;
        frameMaxNanos=maxNanos;
        frameMaxTasks=maxTasks;
        predictiveThisFrame=false;
    }

    /**
     * Opt-in mode-aware scheduling for the EXISTING vanilla FIFO upload queue.
     * TRANSITION/unknown bypasses this Mixin completely: Minecraft drains its
     * own queue immediately so cave mouths and teleports are not throttled.
     *
     * The queue does not expose a stable RenderSection identity per Runnable,
     * so this deliberately changes only per-frame throughput, never order,
     * visibility, individual Section priority, or mesh contents.
     */
    public void beginModeAwareFrame(boolean gameplay,VisibilityMode mode,
            int pendingUploads) {
        beginModeAwareFrame(gameplay,mode,pendingUploads,false);
    }

    /**
     * The frame-cost governor may temporarily disable the 2x upload boost.
     * This never blocks the first task, mutates the vanilla queue, or changes
     * the transition/unknown fail-open bypass.
     */
    public void beginModeAwareFrame(boolean gameplay,VisibilityMode mode,
            int pendingUploads,boolean framePressure) {
        beginModeAwareFrame(gameplay,mode,pendingUploads,framePressure,false);
    }

    /**
     * Predictive limiting is a THIRD explicitly gated experiment: estimate
     * the next FIFO Runnable using an allocation-free 3:1 moving average.
     * Never refuse the first task of an active frame, even after a slow one.
     */
    public void beginModeAwareFrame(boolean gameplay,VisibilityMode mode,
            int pendingUploads,boolean framePressure,boolean predictive) {
        if(!gameplay) {
            beginFrame(false);
            return;
        }
        if(mode==null || mode==VisibilityMode.TRANSITION) {
            beginFrame(false);
            transitionBypasses++;
            return;
        }
        beginFrame(true);
        predictiveThisFrame=predictive;
        int multiplier=mode==VisibilityMode.SEALED_CAVE?2:1;
        // A large old backlog must make forward progress; bounded catch-up
        // avoids permanent delayed terrain without changing FIFO order.
        if(pendingUploads>=64) {
            multiplier=Math.max(2,multiplier);
            backlogRescueFrames++;
        }
        // A slow client should not add more upload work to an overloaded
        // render frame. Keep the existing base quota and FIFO forward progress.
        if(framePressure) {
            multiplier=1;
            adaptiveReducedFrames++;
        }
        frameMaxTasks=Math.min(64,maxTasks*multiplier);
        frameMaxNanos=Math.min(10_000_000L,maxNanos*multiplier);
    }

    /** End BEFORE menus/disconnects or future vanilla invocations. */
    public void endFrame(){inFrame=false;}
    public boolean active(){return inFrame;}

    /** Remaining slots are shared across repeated vanilla calls in one frame. */
    public boolean canTakeTask() {
        if(!inFrame || tasks>=frameMaxTasks || spentNanos>=frameMaxNanos)
            return false;
        if(predictiveThisFrame && tasks>0 && estimatedTaskNanos>0L
                && estimatedTaskNanos>frameMaxNanos-spentNanos) {
            predictiveStops++;
            return false;
        }
        return true;
    }

    /** Called even if upload Runnable takes longer than the entire allowance. */
    public void completed(long elapsedNanos) {
        if(!inFrame)throw new IllegalStateException("Upload outside measured frame");
        tasks++;
        totalTasks++;
        long positive=Math.max(0,elapsedNanos);
        spentNanos=Long.MAX_VALUE-spentNanos<positive ?
                Long.MAX_VALUE : spentNanos+positive;
        if(predictiveThisFrame && positive>0L) {
            // A single blocking upload cannot poison the estimate with
            // enormous (or overflowed) durations.
            long bounded=Math.min(20_000_000L,positive);
            estimatedTaskNanos=estimatedTaskNanos==0L?bounded:
                    (estimatedTaskNanos*3L+bounded)/4L;
        }
    }

    public void recordDeferred(){
        if(inFrame)throttledCalls++;
    }

    public int tasksThisFrame(){return tasks;}
    public long spentNanosThisFrame(){return spentNanos;}
    public long deferredCalls(){return throttledCalls;}
    public long totalTasks(){return totalTasks;}
    public long maxNanos(){return maxNanos;}
    public int maxTasks(){return maxTasks;}
    public long frameMaxNanos(){return frameMaxNanos;}
    public int frameMaxTasks(){return frameMaxTasks;}
    public long transitionBypasses(){return transitionBypasses;}
    public long backlogRescueFrames(){return backlogRescueFrames;}
    public long adaptiveReducedFrames(){return adaptiveReducedFrames;}
    public long predictiveStops(){return predictiveStops;}
    public long estimatedTaskNanos(){return estimatedTaskNanos;}
    /** World changes and menus discard obsolete task-cost estimates. */
    public void resetPrediction(){
        estimatedTaskNanos=0L;
        predictiveThisFrame=false;
    }

    public static ClientChunkUploadBudget fromProperties() {
        long ms=Long.getLong("realscale.experimental.client.upload_budget_us",2000L);
        int tasks=Integer.getInteger("realscale.experimental.client.upload_max_tasks",8);
        // Explicit hard caps prevent accidentally starving world appearance.
        return new ClientChunkUploadBudget(
                Math.clamp(ms,250L,10_000L)*1000L,
                Math.clamp(tasks,1,32));
    }
}
