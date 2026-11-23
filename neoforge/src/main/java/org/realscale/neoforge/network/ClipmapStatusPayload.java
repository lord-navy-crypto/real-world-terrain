package org.realscale.neoforge.network;

import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Explicit server-to-client state for the distant-surface data stream. */
public record ClipmapStatusPayload(String dimensionKey, long requestId, int state,
        int completedLevels, int totalLevels, int queueDepth, String detail)
        implements CustomPacketPayload {
    public static final int REQUEST_ACCEPTED = 0;
    public static final int UNSUPPORTED_WORLD = 1;
    public static final int QUEUED = 2;
    public static final int SAMPLING = 3;
    public static final int SNAPSHOT_SENT = 4;
    public static final int BUSY = 5;
    public static final int FAILED = 6;
    public static final int STALLED = 7;
    public static final int LEVEL_COUNT = 6;
    private static final int MAX_TEXT_LENGTH = 256;

    public static final Type<ClipmapStatusPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("realscale", "clipmap_status"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClipmapStatusPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ClipmapStatusPayload decode(RegistryFriendlyByteBuf buffer) {
                    String dimension = buffer.readUtf(MAX_TEXT_LENGTH);
                    long request = buffer.readLong();
                    int state = buffer.readVarInt();
                    int completed = buffer.readVarInt();
                    int total = buffer.readVarInt();
                    int queue = buffer.readVarInt();
                    String detail = buffer.readUtf(MAX_TEXT_LENGTH);
                    try {
                        return new ClipmapStatusPayload(dimension, request, state,
                                completed, total, queue, detail);
                    } catch (IllegalArgumentException invalid) {
                        throw new DecoderException("Invalid RealScale clipmap status", invalid);
                    }
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, ClipmapStatusPayload value) {
                    try {
                        validate(value.dimensionKey(), value.requestId(), value.state(),
                                value.completedLevels(), value.totalLevels(),
                                value.queueDepth(), value.detail());
                    } catch (IllegalArgumentException invalid) {
                        throw new EncoderException("Invalid RealScale clipmap status", invalid);
                    }
                    buffer.writeUtf(value.dimensionKey(), MAX_TEXT_LENGTH);
                    buffer.writeLong(value.requestId());
                    buffer.writeVarInt(value.state());
                    buffer.writeVarInt(value.completedLevels());
                    buffer.writeVarInt(value.totalLevels());
                    buffer.writeVarInt(value.queueDepth());
                    buffer.writeUtf(value.detail(), MAX_TEXT_LENGTH);
                }
            };

    public ClipmapStatusPayload {
        validate(dimensionKey, requestId, state, completedLevels, totalLevels, queueDepth, detail);
    }

    private static void validate(String dimension, long requestId, int state,
            int completed, int total, int queue, String detail) {
        if (dimension == null || dimension.isBlank() || dimension.length() > MAX_TEXT_LENGTH)
            throw new IllegalArgumentException("Invalid clipmap status dimension");
        if (requestId <= 0L) throw new IllegalArgumentException("Invalid clipmap status request ID");
        if (state < REQUEST_ACCEPTED || state > STALLED)
            throw new IllegalArgumentException("Unknown clipmap status state: " + state);
        if (total != LEVEL_COUNT || completed < 0 || completed > total)
            throw new IllegalArgumentException("Invalid clipmap status progress");
        if (queue < 0 || queue > 1024) throw new IllegalArgumentException("Invalid clipmap queue depth");
        if (detail == null || detail.length() > MAX_TEXT_LENGTH)
            throw new IllegalArgumentException("Invalid clipmap status detail");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
