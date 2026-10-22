package org.realscale.compat.dhchunky.mixin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
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

    @Inject(method = "tryRunFirstTimeSetup()V", at = @At("HEAD"),
            cancellable = true, remap = false)
    private void realscale$deferChunkyBindingUntilReady(CallbackInfo ci) {
        // Explicit opt-out for A/B verification or a future upstream DH fix.
        if (!Boolean.parseBoolean(
                System.getProperty("realscale.dhchunky.guard.enabled", "true"))) {
            return;
        }
        // After first successful binding, this method is called for EVERY
        // full chunk update. Never reflect on every completed chunk.
        if (listenerBound) return;
        if (realscale$hookSeen.compareAndSet(false, true)) {
            System.err.println("REALSCALE_DH_CHUNKY_GUARD_HOOK_ENTERED");
        }

        if (!realscale$isChunkyProviderReady()) {
            // Cancel BEFORE DH flips listenerBound; retry at next real chunk
            // update. This also ensures an exception cannot escape chunk load.
            if (realscale$warned.compareAndSet(false, true)) {
                System.err.println(
                    "REALSCALE_DH_CHUNKY_GUARD_DEFERRED: Chunky provider not ready; "
                    + "leaving DH listener unbound until next chunk event");
            }
            ci.cancel();
        } else if (realscale$warned.get()) {
            System.err.println(
                "REALSCALE_DH_CHUNKY_GUARD_READY: Chunky provider is now registered; "
                + "allowing native DH event registration");
            realscale$warned.set(false);
        }
    }

    @Unique
    private static boolean realscale$isChunkyProviderReady() {
        // Avoid directly linking Chunky classes from this standalone addon,
        // which makes class initialization robust while game registries load.
        try {
            Class<?> provider = Class.forName("org.popcraft.chunky.ChunkyProvider");
            Method get = provider.getMethod("get");
            return get.invoke(null) != null;
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
