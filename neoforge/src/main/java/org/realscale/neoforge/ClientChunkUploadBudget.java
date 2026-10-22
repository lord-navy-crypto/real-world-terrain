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

    public ClientChunkUploadBudget(long maxNanos,int maxTasks) {
        if(maxNanos<100_000L || maxNanos>20_000_000L ||
                maxTasks<1 || maxTasks>64)
            throw new IllegalArgumentException("Invalid client upload budget");
        this.maxNanos=maxNanos;
        this.maxTasks=maxTasks;
    }

    public void beginFrame(boolean gameplay) {
        inFrame=gameplay;
        tasks=0;
        spentNanos=0L;
    }

    /** End BEFORE menus/disconnects or future vanilla invocations. */
    public void endFrame(){inFrame=false;}
    public boolean active(){return inFrame;}

    /** Remaining slots are shared across repeated vanilla calls in one frame. */
    public boolean canTakeTask() {
        return inFrame && tasks<maxTasks && spentNanos<maxNanos;
    }

    /** Called even if upload Runnable takes longer than the entire allowance. */
    public void completed(long elapsedNanos) {
        if(!inFrame)throw new IllegalStateException("Upload outside measured frame");
        tasks++;
        totalTasks++;
        long positive=Math.max(0,elapsedNanos);
        spentNanos=Long.MAX_VALUE-spentNanos<positive ?
                Long.MAX_VALUE : spentNanos+positive;
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

    public static ClientChunkUploadBudget fromProperties() {
        long ms=Long.getLong("realscale.experimental.client.upload_budget_us",2000L);
        int tasks=Integer.getInteger("realscale.experimental.client.upload_max_tasks",8);
        // Explicit hard caps prevent accidentally starving world appearance.
        return new ClientChunkUploadBudget(
                Math.clamp(ms,250L,10_000L)*1000L,
                Math.clamp(tasks,1,32));
    }
}
