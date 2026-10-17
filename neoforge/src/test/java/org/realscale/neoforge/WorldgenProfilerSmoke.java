package org.realscale.neoforge;

/** JVM-level profiler smoke; run both with and without the opt-in property. */
public final class WorldgenProfilerSmoke {
    private WorldgenProfilerSmoke() {}
    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }

    public static void main(String[] args) {
        var section = WorldgenProfiler.Section.RIVER;
        if (!WorldgenProfiler.ENABLED) {
            for (int i = 0; i < 10000; i++) {
                long began = WorldgenProfiler.begin();
                check(began == 0L, "Disabled clock should not run");
                WorldgenProfiler.finish(section, began);
            }
            check(WorldgenProfiler.calls(section) == 0L, "Disabled probe must collect nothing");
            System.out.println("TVA_WORLDGEN_PROFILER_DISABLED_PASS");
            return;
        }
        for (int i = 0; i < 4100; i++) {
            long began = WorldgenProfiler.begin();
            WorldgenProfiler.finish(section, began);
        }
        check(WorldgenProfiler.calls(section) == 4100L, "Probe lost samples");
        check(WorldgenProfiler.calls(WorldgenProfiler.Section.GRAND_ECOLOGY) == 0L,
                "Counters must remain isolated by feature");
        String snapshot = WorldgenProfiler.snapshot(section);
        check(snapshot.contains("calls=4100"), "Missing profile counts: " + snapshot);
        check(snapshot.contains("p95_bucket_upper_us="), "Missing latency histogram");
        check(WorldgenProfiler.bucketForNanos(0) == 0, "Zero bucket");
        check(WorldgenProfiler.bucketForNanos(1000) == 1, "One microsecond bucket");
        check(WorldgenProfiler.bucketForNanos(3000) == 2, "Three microsecond bucket");
        check(WorldgenProfiler.bucketForNanos(3000000000L) == 22, "Large sample bucket");
        System.out.println("TVA_WORLDGEN_PROFILER_ENABLED_PASS: " + snapshot);
    }
}
