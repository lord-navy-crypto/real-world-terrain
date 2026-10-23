package org.realscale.compat.dhchunky.mixin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import org.realscale.compat.dhchunky.ChunkyBridgeMetrics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Distant Horizons 3.3.3's AbstractChunkyAccessor sets listenerBound=true
 * BEFORE it calls its NeoForge ChunkyAccessor.bindOnGenerationProgressEvent().
 *
 * During integrated singleplayer's early full chunk events, Chunky 1.4.23's
 * ChunkyProvider.get() can throw "Chunky is not loaded." because its provider
 * registration has not happened yet. The uncaught exception shuts the world
 * down. Prevent the premature first-time call WITHOUT mutating DH's flag so
 * its very next chunk event can retry after Chunky registers.
 *
 * Targets a PROVEN method descriptor from the pinned official DH 3.3.3 JAR.
 * No RealScale, Minecraft density function or DH rendering code is changed.
 */
@Mixin(
    targets = "com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.AbstractChunkyAccessor",
    remap = false
)
public abstract class ChunkyLifecycleSetupMixin {
    @Shadow(remap = false) private boolean listenerBound;
    @Unique private static final AtomicBoolean realscale$warned = new AtomicBoolean();
    @Unique private static final AtomicBoolean realscale$hookSeen = new AtomicBoolean();
    @Unique private static volatile Method realscale$providerGet;
    // JVM flags are immutable for this mod lifetime. Avoid system property
    // parsing on every Minecraft full-chunk event after DH has bound.
    @Unique private static final boolean realscale$guardEnabled =
            Boolean.parseBoolean(System.getProperty(
                    "realscale.dhchunky.guard.enabled", "true"));
    @Unique private boolean realscale$boundConfirmed;

    @Inject(method = "tryRunFirstTimeSetup()V", at = @At("HEAD"),
            cancellable = true, remap = false)
    private void realscale$deferChunkyBindingUntilReady(CallbackInfo ci) {
        // Explicit opt-out for A/B verification or a future upstream DH fix.
        // After native DH binding this injection is still called on every
        // full-chunk event. Its normal-gameplay hot path must be one cheap
        // field test, without System.getProperty, reflection or allocations.
        if (listenerBound) return;
        if (!realscale$guardEnabled) return;
        if (realscale$hookSeen.compareAndSet(false, true)) {
            System.err.println("REALSCALE_DH_CHUNKY_GUARD_HOOK_ENTERED");
        }

        // Timed only during the pre-bind handshake, never on ordinary chunk
        // updates after listenerBound flips. DH still owns its generator plan.
        long started = System.nanoTime();
        boolean providerReady = realscale$isChunkyProviderReady();
        ChunkyBridgeMetrics.probe(providerReady, System.nanoTime() - started);
        if (!providerReady) {
            ChunkyBridgeMetrics.deferred();
            // Cancel BEFORE DH flips listenerBound; retry at next real chunk
            // update. This also ensures an exception cannot escape chunk load.
            if (realscale$warned.compareAndSet(false, true)) {
                System.err.println(
                    "REALSCALE_DH_CHUNKY_GUARD_DEFERRED: Chunky provider not ready; "
                    + "leaving DH listener unbound until next chunk event");
            }
            ci.cancel();
        } else if (realscale$warned.get()) {
            ChunkyBridgeMetrics.ready();
            System.err.println(
                "REALSCALE_DH_CHUNKY_GUARD_READY: Chunky provider is now registered; "
                + "allowing native DH event registration");
            realscale$warned.set(false);
        }
    }

    /** Only report success AFTER Distant Horizons' original listener binding returns. */
    @Inject(method = "tryRunFirstTimeSetup()V", at = @At("RETURN"), remap = false)
    private void realscale$confirmNativeDhListener(CallbackInfo ci) {
        if (!realscale$boundConfirmed && listenerBound) {
            realscale$boundConfirmed = true;
            ChunkyBridgeMetrics.bound();
            System.err.println("REALSCALE_DH_CHUNKY_GUARD_BOUND: native DH listener initialized");
        }
    }

    /** Resolve ChunkyProvider.get once; never repeatedly scan classes on chunk updates. */
    @Unique
    private static Method realscale$resolveChunkyGetter() throws ReflectiveOperationException {
        Method getter = realscale$providerGet;
        if (getter != null) return getter;
        synchronized (realscale$hookSeen) {
            getter = realscale$providerGet;
            if (getter == null) {
                Class<?> provider = Class.forName("org.popcraft.chunky.ChunkyProvider");
                getter = provider.getMethod("get");
                realscale$providerGet = getter;
            }
        }
        return getter;
    }

    @Unique
    private static boolean realscale$isChunkyProviderReady() {
        // Avoid directly linking Chunky classes from this standalone addon,
        // which makes class initialization robust while game registries load.
        try {
            return realscale$resolveChunkyGetter().invoke(null) != null;
        } catch (InvocationTargetException ex) {
            Throwable root = ex.getCause();
            if (root instanceof IllegalStateException &&
                    "Chunky is not loaded.".equals(root.getMessage())) {
                return false; // The one confirmed and narrowly handled error.
            }
            if (root instanceof Error error) throw error;
            if (root instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException(
                "Unexpected ChunkyProvider.get() invocation error", root);
        } catch (ReflectiveOperationException ex) {
            // Intentionally fail instead of masking unknown version/API changes.
            throw new IllegalStateException(
                "Chunky 1.4.23 provider API was not found; check exact mods", ex);
        }
    }
}
