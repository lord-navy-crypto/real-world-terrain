package org.realscale.compat.dhchunky;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Small launch-lifetime counters; zero timers, polls, background workers,
 * configuration writes, or per-chunk log spam. Does not inspect player data.
 *
 * A native listener being bound is NOT proof that distant GPU LOD has rendered.
 * v0.3 deliberately leaves the DH/Chunky scheduler completely upstream-owned.
 */
public final class ChunkyBridgeMetrics {
    private static final AtomicLong providerProbes = new AtomicLong();
    private static final AtomicLong failedProbes = new AtomicLong();
    private static final AtomicLong deferredCallbacks = new AtomicLong();
    private static final AtomicLong readyTransitions = new AtomicLong();
    private static final AtomicLong nativeBindings = new AtomicLong();
    private static final AtomicLong totalProbeNanos = new AtomicLong();
    private static final AtomicLong worstProbeNanos = new AtomicLong();

    private ChunkyBridgeMetrics() {}

    public static void probe(boolean available, long elapsedNanos) {
        providerProbes.incrementAndGet();
        if (!available) failedProbes.incrementAndGet();
        long bounded = Math.max(0L, elapsedNanos);
        totalProbeNanos.addAndGet(bounded);
        worstProbeNanos.accumulateAndGet(bounded, Math::max);
    }

    public static void deferred() { deferredCallbacks.incrementAndGet(); }
    public static void ready() { readyTransitions.incrementAndGet(); }
    public static void bound() { nativeBindings.incrementAndGet(); }

    public static String status() {
        long checks = providerProbes.get();
        long binds = nativeBindings.get();
        double avgUs = checks == 0L ? 0.0 : totalProbeNanos.get() / (1000.0 * checks);
        double maxUs = worstProbeNanos.get() / 1000.0;
        boolean enabled = Boolean.parseBoolean(System.getProperty(
            "realscale.dhchunky.guard.enabled", "true"));
        return String.format(Locale.ROOT,
            "DHChunkyGuard v0.3.1 enabled=%s probes=%d notReady=%d deferred=%d "
                + "readySignals=%d nativeBindsSinceLaunch=%d avgProbeUs=%.1f maxProbeUs=%.1f "
                + "GPU_LOD=unverified",
            enabled, checks, failedProbes.get(), deferredCallbacks.get(),
            readyTransitions.get(), binds, avgUs, maxUs);
    }

    public static String advice() {
        if (nativeBindings.get() == 0L && deferredCallbacks.get() > 0L) {
            return "DHChunkyGuard: Chunky was not ready; native DH listener bind not yet observed. "
                + "Generate or load another chunk, then check status. Do not assume GPU LOD is broken.";
        }
        if (nativeBindings.get() == 0L) {
            return "DHChunkyGuard: no native DH listener bind observed in this process. "
                + "Check exact DH 3.3.3 / Chunky 1.4.23 versions and latest.log.";
        }
        return "DHChunkyGuard: native DH-to-Chunky listener binding observed. "
            + "This does NOT verify rendered LOD. For blank mountains check DH rendering, "
            + "distant generation and active Iris shaderpack compatibility separately.";
    }
}
