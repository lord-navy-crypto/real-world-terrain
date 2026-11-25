package org.realscale.neoforge;

/**
 * Fixed-memory, single-client-thread measurements of the OPTIONAL exposure
 * detector's own cost. Not Minecraft FPS, mesh rebuilds or GPU visibility.
 * No allocations on recordSample() and no background worker.
 */
public final class AdaptiveVisibilityTelemetry {
    private final long[] histogram = new long[24]; // log2 microseconds
    private long totalSamples, unknownSamples, totalNanos, maxNanos;
    private final long[] modeSamples = new long[AdaptiveVisibilityBudget.Mode.values().length];

    /** Called only when -Drealscale.client.visibility.profile=true. */
    public void recordSample(AdaptiveVisibilityBudget.Mode mode,
            boolean known, long elapsedNanos) {
        if (mode == null) throw new IllegalArgumentException("Missing mode");
        long nanos = Math.max(0L, elapsedNanos);
        totalSamples++;
        if (!known) unknownSamples++;
        modeSamples[mode.ordinal()]++;
        // Saturate rather than overflow in extremely long diagnostics.
        if (nanos > Long.MAX_VALUE - totalNanos) totalNanos=Long.MAX_VALUE;
        else totalNanos += nanos;
        maxNanos=Math.max(maxNanos,nanos);
        long micros=nanos/1000L;
        int bucket=micros==0 ? 0 :
                (int)Math.min(histogram.length-1,64-Long.numberOfLeadingZeros(micros));
        histogram[bucket]++;
    }

    public long samples() { return totalSamples; }
    public long unknownSamples() { return unknownSamples; }
    public long samplesInMode(AdaptiveVisibilityBudget.Mode mode) {
        return modeSamples[mode.ordinal()];
    }
    public long maxMicroseconds() { return maxNanos/1000L; }
    public long averageMicroseconds() {
        return totalSamples==0 ? 0 : Math.round(totalNanos/(double)totalSamples/1000.0);
    }
    /** Upper bound of a coarse microsecond logarithmic timing bucket. */
    public long p95BucketUpperMicroseconds() {
        if(totalSamples==0)return 0;
        long threshold=(long)Math.ceil(totalSamples*0.95),observed=0;
        for(int i=0;i<histogram.length;i++){
            observed+=histogram[i];
            if(observed>=threshold)
                return i==0 ? 0L : (1L<<i)-1L;
        }
        return (1L<<(histogram.length-1))-1L;
    }
    public String summary() {
        return "detector_samples="+samples()+
                " unknown="+unknownSamples()+
                " surface="+samplesInMode(AdaptiveVisibilityBudget.Mode.OPEN_SURFACE)+
                " cave="+samplesInMode(AdaptiveVisibilityBudget.Mode.SEALED_CAVE)+
                " transition="+samplesInMode(AdaptiveVisibilityBudget.Mode.TRANSITION)+
                " mean_probe_us="+averageMicroseconds()+
                " p95_bucket_upper_us="+p95BucketUpperMicroseconds()+
                " max_probe_us="+maxMicroseconds()+
                " (probe overhead only, NOT FPS or GPU time)";
    }
}
