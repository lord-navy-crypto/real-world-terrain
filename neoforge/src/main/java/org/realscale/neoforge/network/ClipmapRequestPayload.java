package org.realscale.neoforge.network;

import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client request to enable the opt-in RealScale far-surface stream. */
public record ClipmapRequestPayload(int nearRenderDistanceChunks)
        implements CustomPacketPayload {
    public static final Type<ClipmapRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    "realscale", "clipmap_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClipmapRequestPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ClipmapRequestPayload decode(RegistryFriendlyByteBuf buffer) {
                    int chunks = buffer.readVarInt();
                    if (chunks < 2 || chunks > 64) {
                        throw new DecoderException("Invalid RealScale clipmap render distance: " + chunks);
                    }
                    return new ClipmapRequestPayload(chunks);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer,
                        ClipmapRequestPayload value) {
                    int chunks = value.nearRenderDistanceChunks();
                    if (chunks < 2 || chunks > 64) {
                        throw new EncoderException("Invalid RealScale clipmap render distance: " + chunks);
                    }
                    buffer.writeVarInt(chunks);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
