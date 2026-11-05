package org.realscale.neoforge;

import java.util.Locale;

/**
 * Bounded, allocation-free-on-record frame INTERVAL profiler.
 *
 * Observations are consecutive RenderFrameEvent.Post timestamps, not exact
 * GPU draw duration. They include frame pacing, VSync, mods and scheduling.
 * Disabled unless the client has explicitly requested performance profiling.
 *
 * The 0.5 ms histogram has one overflow bin (>=256 ms). Report percentile
 * overflow as 256+ rather than presenting an inaccurate upper bound.
 */
public final class ClientFrameIntervalStats {
    private static final long BUCKET_NANOS = 500_000L;
    private static final int LAST_FINITE_BIN = 511;
    private static final int OVERFLOW_BIN = 512;
    private final long[] histogram = new long[OVERFLOW_BIN + 1];
    private long frames, totalNanos, maxNanos, over50ms;

    public void add(long intervalNanos) {
        if (intervalNanos <= 0) return;
        int bin=(int)Math.min(OVERFLOW_BIN,(intervalNanos - 1L)/BUCKET_NANOS);
        histogram[bin]++;
        frames++;
        totalNanos = Long.MAX_VALUE-totalNanos<intervalNanos ?
                Long.MAX_VALUE : totalNanos + intervalNanos;
        maxNanos=Math.max(maxNanos,intervalNanos);
        if (intervalNanos>50_000_000L) over50ms++;
    }

    public long frames() { return frames; }
    public long slowFramesOver50ms() { return over50ms; }
    public double fps() {
        if (frames==0 || totalNanos==0) return 0;
        return frames*1_000_000_000.0/totalNanos;
    }
    public double maxFrameMs() { return maxNanos/1_000_000.0; }

    /**
     * Upper bound of the percentile's bucket, or NaN on histogram overflow.
     * Explicitly approximate; frame deltas do NOT measure GPU-only work.
     */
    public double percentileUpperMs(double quantile) {
        if (quantile <= 0 || quantile > 1)
            throw new IllegalArgumentException("Percentile must be in (0,1]");
        if (frames==0) return 0;
        long threshold=(long)Math.ceil(frames*quantile);
        long accumulated=0;
        for (int i=0; i<histogram.length; i++) {
            accumulated+=histogram[i];
            if (accumulated>=threshold)
                return i==OVERFLOW_BIN ? Double.NaN : (i+1)*0.5;
        }
        throw new AssertionError("Histogram count mismatch");
    }

    /** The inverse of the 99th percentile frame interval, an approximate 1%-low FPS. */
    public double onePercentLowFpsLowerBound() {
        double upperMs=percentileUpperMs(0.99);
        return upperMs<=0 || Double.isNaN(upperMs) ?
                0 : 1000.0/upperMs;
    }

    private static String frameMs(double milliseconds) {
        return Double.isNaN(milliseconds) ? "256+" :
                String.format(Locale.ROOT, "%.1f", milliseconds);
    }

    public String report(String scene, String mode) {
        return String.format(Locale.ROOT,
                "scene=%s mode=%s frames=%d mean_fps=%.2f"+
                " p50_interval_ms=%s p95_interval_ms=%s"+
                " p99_interval_ms=%s low1pct_fps_lower=%.2f"+
                " slow_over_50ms=%d max_interval_ms=%.2f"+
                " method=post_to_post_including_frame_pacing_not_gpu",
                scene,mode,frames(),fps(),
                frameMs(percentileUpperMs(.50)),
                frameMs(percentileUpperMs(.95)),
                frameMs(percentileUpperMs(.99)),
                onePercentLowFpsLowerBound(),slowFramesOver50ms(),
                maxFrameMs());
    }
}
