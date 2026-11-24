package org.realscale.neoforge.client.clipmap;

import org.realscale.neoforge.network.ClipmapHeightTilePayload;
import org.realscale.neoforge.network.ClipmapSurfaceProxyPayload;
import org.realscale.neoforge.network.ClipmapStatusPayload;

/**
 * Client-only delivery target. Payload CODECS are registered on the common
 * mod bus by GeometryClipmapNetwork so dedicated servers can encode them.
 *
 * <p>Do not subscribe this class to RegisterPayloadHandlersEvent: that would
 * double-register the same clientbound payloads.
 */
public final class GeometryClipmapClientPayloads {
    private GeometryClipmapClientPayloads() {}

    public static void acceptHeight(ClipmapHeightTilePayload payload) {
        GeometryClipmapClient.accept(payload);
    }

    public static void acceptProxies(ClipmapSurfaceProxyPayload payload) {
        GeometryClipmapClient.accept(payload);
    }

    public static void acceptStatus(ClipmapStatusPayload payload) {
        GeometryClipmapClient.accept(payload);
    }
}
