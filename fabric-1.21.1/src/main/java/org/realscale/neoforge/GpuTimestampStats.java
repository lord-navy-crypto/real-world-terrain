package org.realscale.neoforge;

/** Fixed-memory statistics for asynchronous OpenGL timestamp spans. */
final class GpuTimestampStats {
    private static final long BUCKET_NANOS=500_000L;
    private static final int FINITE_BUCKETS=512,OVERFLOW_BUCKET=FINITE_BUCKETS;
    private final long[] histogram=new long[FINITE_BUCKETS+1];
    private long samples,totalNanos,maxNanos,over50ms;

    void add(long elapsedNanos) {
        if(elapsedNanos<=0)return;
        int bucket=(int)Math.min(OVERFLOW_BUCKET,(elapsedNanos-1L)/BUCKET_NANOS);
        histogram[bucket]++;
        samples++;
        totalNanos=Long.MAX_VALUE-totalNanos<elapsedNanos
                ?Long.MAX_VALUE:totalNanos+elapsedNanos;
        maxNanos=Math.max(maxNanos,elapsedNanos);
        if(elapsedNanos>50_000_000L)over50ms++;
    }
    long samples(){return samples;}
    long over50ms(){return over50ms;}
    double meanMs(){return samples==0?0.0:totalNanos/(double)samples/1_000_000.0;}
    double maxMs(){return maxNanos/1_000_000.0;}
    double percentileUpperMs(double q) {
        if(q<=0.0||q>1.0)throw new IllegalArgumentException("Percentile must be in (0,1]");
        if(samples==0)return 0.0;
        long target=(long)Math.ceil(samples*q),seen=0;
        for(int i=0;i<histogram.length;i++) {
            seen+=histogram[i];
            if(seen>=target)return i==OVERFLOW_BUCKET?Double.NaN:(i+1)*0.5;
        }
        throw new AssertionError("GPU timestamp histogram count mismatch");
    }
    void reset() {
        java.util.Arrays.fill(histogram,0L);
        samples=totalNanos=maxNanos=over50ms=0L;
    }
}
