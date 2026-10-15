package org.realscale.neoforge;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.LongAdder;

/**
 * Opt-in low-allocation worldgen feature timing for RealScale.
 *
 * <p>Enable by setting JVM property -Drealscale.worldgen.profile=true on
 * the actual Minecraft JVM (not merely Gradle). Disabled by default. All
 * counters are process-bounded, with one fixed histogram per feature type.
 *
 * <p>Times measure the feature's place() invocation, NOT complete chunk
 * generation, terrain noise, Minecraft rendering, or server TPS.
 */
final class WorldgenProfiler {
    static final boolean ENABLED = Boolean.getBoolean("realscale.worldgen.profile");
    private static final long LOG_INTERVAL = 4096L;

    enum Section { RIVER, GRAND_ECOLOGY, SKY_ISLAND }
    private static final Metrics[] METRICS = {
            new Metrics(), new Metrics(), new Metrics()
    };

    static {
        if (ENABLED) {
            // Short fixed-seed runs may never reach 4096 feature calls.
            // Emit final snapshots on graceful JVM shutdown as well.
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                for (Section section : Section.values()) {
                    if (calls(section) > 0)
                        System.err.println("[RealScale worldgen profile] " + snapshot(section));
                }
            }, "realscale-worldgen-profile-summary"));
        }
    }

    static long begin() {
        return ENABLED ? System.nanoTime() : 0L;
    }

    static void finish(Section section, long began) {
        if (!ENABLED) return;
        long elapsed = Math.max(0, System.nanoTime() - began);
        Metrics m = METRICS[section.ordinal()];
        m.totalNanos.add(elapsed);
        m.maxNanos.accumulateAndGet(elapsed, Math::max);
        int bucket = bucketForNanos(elapsed);
        m.buckets.incrementAndGet(bucket);
        long n = m.count.incrementAndGet();
        // Logs happen only in diagnostic mode and are deliberately infrequent.
        if ((n & (LOG_INTERVAL - 1)) == 0)
            System.err.println("[RealScale worldgen profile] " + snapshot(section));
    }

    static String snapshot(Section section) {
        Metrics m = METRICS[section.ordinal()];
        long n = m.count.get();
        double meanMicros = n == 0 ? 0 : (m.totalNanos.sum() / (double) n) / 1000.0;
        // Histogram is logarithmic: p95 is a coarse upper bound, not an exact value.
        long p95UpperMicros = p95UpperMicros(m, n);
        return section.name() + " calls=" + n
                + " mean_us=" + Math.round(meanMicros)
                + " p95_bucket_upper_us=" + p95UpperMicros
                + " max_us=" + m.maxNanos.get() / 1000;
    }

    static long calls(Section section) {
        return METRICS[section.ordinal()].count.get();
    }

    static int bucketForNanos(long nanos) {
        long micros = nanos / 1000;
        if (micros <= 0) return 0;
        return Math.min(30, 64 - Long.numberOfLeadingZeros(micros));
    }

    private static long p95UpperMicros(Metrics m, long n) {
        if (n == 0) return 0;
        long target = (long) Math.ceil(n * 0.95);
        long observed = 0;
        for (int i = 0; i < m.buckets.length(); i++) {
            observed += m.buckets.get(i);
            if (observed >= target)
                return i == 0 ? 0 : (1L << i) - 1L;
        }
        return (1L << 30) - 1L;
    }

    private static final class Metrics {
        final AtomicLong count = new AtomicLong();
        final LongAdder totalNanos = new LongAdder();
        final AtomicLong maxNanos = new AtomicLong();
        final AtomicLongArray buckets = new AtomicLongArray(31);
    }

    private WorldgenProfiler() {}
}
