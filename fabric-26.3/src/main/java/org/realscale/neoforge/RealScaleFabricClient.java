package org.realscale.neoforge;

import net.fabricmc.api.ClientModInitializer;

/** Minimal client entrypoint for the first 26.3 worldgen port. */
public final class RealScaleFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Optional Studio, upload pacing, vehicle renderers and mixins are
        // intentionally deferred until their 26.3 client APIs are validated.
    }
}
