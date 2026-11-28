package org.realscale.neoforge.client.clipmap;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Thread-safe registry for worldgen features that expose distant exterior proxies. */
public final class SurfaceFeatureProxyRegistry {
    private static final CopyOnWriteArrayList<SurfaceFeatureProxyProvider> PROVIDERS =
            new CopyOnWriteArrayList<>();

    private SurfaceFeatureProxyRegistry() {}

    public static void register(SurfaceFeatureProxyProvider provider) {
        if (provider != null) PROVIDERS.addIfAbsent(provider);
    }

    public static boolean unregister(SurfaceFeatureProxyProvider provider) {
        return PROVIDERS.remove(provider);
    }

    public static void collect(SurfaceFeatureProxyProvider.Context context,
            List<VisibleSurfaceProxy> output) {
        for (SurfaceFeatureProxyProvider provider : PROVIDERS) {
            context.checkCurrent();
            if (output.size() >= ClipmapSurfaceProxyPayloadLimit.MAX_PROXIES) return;
            provider.collect(context, output);
        }
    }

    private static final class ClipmapSurfaceProxyPayloadLimit {
        private static final int MAX_PROXIES = 4096;
    }
}
