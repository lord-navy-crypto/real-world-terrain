package org.realscale.neoforge.compat.dhchunky;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight launch-lifetime counters for the embedded DH/Chunky bridge.
 * No timers, polling threads, configuration writes, player-data inspection,
 * chunk scheduler changes, or per-chunk log spam.
 *
 * A native listener bind is NOT proof that distant GPU LOD rendered.
 */
public final class ChunkyBridgeMetrics {
    private static final AtomicLong providerProbes = new AtomicLong();
    private static final AtomicLong failedProbes = new AtomicLong();
    private static final AtomicLong deferredCallbacks = new AtomicLong();
    private static final AtomicLong backoffSkips = new AtomicLong();
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
    public static void backoffSkipped() { backoffSkips.incrementAndGet(); }
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
            "RealScale DH-Chunky Guard (embedded) enabled=%s probes=%d notReady=%d deferred=%d "
                + "backoffSkips=%d readySignals=%d nativeBindsSinceLaunch=%d "
                + "avgProbeUs=%.1f maxProbeUs=%.1f GPU_LOD=unverified",
            enabled, checks, failedProbes.get(), deferredCallbacks.get(), backoffSkips.get(),
            readyTransitions.get(), binds, avgUs, maxUs);
    }

    public static String advice() {
        if (nativeBindings.get() == 0L && deferredCallbacks.get() > 0L) {
            return "RealScale DH-Chunky Guard: Chunky was not ready; native DH listener binding "
                + "has not yet been observed. Load or generate another chunk, then check status.";
        }
        if (nativeBindings.get() == 0L) {
            return "RealScale DH-Chunky Guard: no native DH listener bind observed in this process. "
                + "The embedded guard activates only for Distant Horizons 3.3.3 + Chunky 1.4.23.";
        }
        return "RealScale DH-Chunky Guard: native DH-to-Chunky listener binding was observed. "
            + "This does NOT verify rendered LOD, Iris, Sodium, or shaderpack compatibility.";
    }
}
