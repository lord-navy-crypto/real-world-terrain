package org.realscale.neoforge.network;

import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.realscale.neoforge.client.clipmap.VisibleSurfaceProxy;

import java.util.ArrayList;
import java.util.List;

/** Bounded exterior-only feature proxies; never contains hidden interiors or block arrays. */
public record ClipmapSurfaceProxyPayload(
        String dimensionKey,
        long requestId,
        long centerX,
        long centerZ,
        List<VisibleSurfaceProxy> proxies) implements CustomPacketPayload {
    public static final int MAX_PROXIES = 4096;
    private static final int MAX_DIMENSION_KEY_LENGTH = 256;

    public static final Type<ClipmapSurfaceProxyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    "realscale", "clipmap_surface_proxies"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClipmapSurfaceProxyPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ClipmapSurfaceProxyPayload decode(RegistryFriendlyByteBuf buffer) {
                    String dimension = buffer.readUtf(MAX_DIMENSION_KEY_LENGTH);
                    long requestId = buffer.readLong();
                    if (requestId <= 0L) {
                        throw new DecoderException("Invalid clipmap request ID");
                    }
                    long centerX = buffer.readLong();
                    long centerZ = buffer.readLong();
                    int count = buffer.readVarInt();
                    if (count < 0 || count > MAX_PROXIES) {
                        throw new DecoderException("Invalid clipmap proxy count: " + count);
                    }
                    List<VisibleSurfaceProxy> values = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        int x = buffer.readInt();
                        int y = buffer.readInt();
                        int z = buffer.readInt();
                        float radius = buffer.readFloat();
                        float height = buffer.readFloat();
                        int rgb = buffer.readInt();
                        int type = buffer.readVarInt();
                        try {
                            values.add(new VisibleSurfaceProxy(
                                    x, y, z, radius, height, rgb, type));
                        } catch (IllegalArgumentException invalid) {
                            throw new DecoderException("Invalid visible-surface proxy", invalid);
                        }
                    }
                    return new ClipmapSurfaceProxyPayload(
                            dimension, requestId, centerX, centerZ, values);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer,
                        ClipmapSurfaceProxyPayload value) {
                    if (value.requestId() <= 0L) {
                        throw new EncoderException("Invalid clipmap request ID");
                    }
                    if (value.proxies().size() > MAX_PROXIES) {
                        throw new EncoderException("Too many clipmap surface proxies");
                    }
                    buffer.writeUtf(value.dimensionKey(), MAX_DIMENSION_KEY_LENGTH);
                    buffer.writeLong(value.requestId());
                    buffer.writeLong(value.centerX());
                    buffer.writeLong(value.centerZ());
                    buffer.writeVarInt(value.proxies().size());
                    for (VisibleSurfaceProxy proxy : value.proxies()) {
                        buffer.writeInt(proxy.x());
                        buffer.writeInt(proxy.y());
                        buffer.writeInt(proxy.z());
                        buffer.writeFloat(proxy.radius());
                        buffer.writeFloat(proxy.height());
                        buffer.writeInt(proxy.rgb());
                        buffer.writeVarInt(proxy.type());
                    }
                }
            };

    public ClipmapSurfaceProxyPayload {
        if (requestId <= 0L) {
            throw new IllegalArgumentException("Clipmap request ID must be positive");
        }
        if (dimensionKey == null || dimensionKey.isBlank()
                || dimensionKey.length() > MAX_DIMENSION_KEY_LENGTH) {
            throw new IllegalArgumentException("Invalid clipmap dimension key");
        }
        proxies = List.copyOf(proxies);
        if (proxies.size() > MAX_PROXIES) {
            throw new IllegalArgumentException("Too many clipmap surface proxies");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
