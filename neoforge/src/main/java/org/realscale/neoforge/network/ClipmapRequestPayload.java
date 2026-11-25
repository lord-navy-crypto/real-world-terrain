package org.realscale.neoforge.network;

import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client request to enable the opt-in RealScale far-surface stream. */
public record ClipmapRequestPayload(
        int nearRenderDistanceChunks, int gridCells, long requestId)
        implements CustomPacketPayload {
    public static final Type<ClipmapRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    "realscale", "clipmap_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClipmapRequestPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ClipmapRequestPayload decode(RegistryFriendlyByteBuf buffer) {
                    int chunks = buffer.readVarInt();
                    int gridCells = buffer.readVarInt();
                    long requestId = buffer.readLong();
                    if (chunks < 2 || chunks > 64
                            || !isSupportedGridCells(gridCells) || requestId <= 0L) {
                        throw new DecoderException("Invalid RealScale clipmap request");
                    }
                    return new ClipmapRequestPayload(chunks, gridCells, requestId);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer,
                        ClipmapRequestPayload value) {
                    if (value.nearRenderDistanceChunks() < 2
                            || value.nearRenderDistanceChunks() > 64
                            || !isSupportedGridCells(value.gridCells())
                            || value.requestId() <= 0L) {
                        throw new EncoderException("Invalid RealScale clipmap request");
                    }
                    buffer.writeVarInt(value.nearRenderDistanceChunks());
                    buffer.writeVarInt(value.gridCells());
                    buffer.writeLong(value.requestId());
                }
            };

    public ClipmapRequestPayload {
        if (nearRenderDistanceChunks < 2 || nearRenderDistanceChunks > 64) {
            throw new IllegalArgumentException(
                    "Invalid RealScale clipmap render distance: " + nearRenderDistanceChunks);
        }
        if (!isSupportedGridCells(gridCells)) {
            throw new IllegalArgumentException(
                    "Invalid RealScale clipmap grid resolution: " + gridCells);
        }
        if (requestId <= 0L) {
            throw new IllegalArgumentException("Clipmap request ID must be positive");
        }
    }

    public static boolean isSupportedGridCells(int value) {
        return value == 64 || value == 128 || value == 256;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
