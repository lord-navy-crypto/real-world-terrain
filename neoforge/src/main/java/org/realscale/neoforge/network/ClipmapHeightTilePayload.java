package org.realscale.neoforge.network;

import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * One quantized visible-surface height grid. Payload size is bounded and never
 * contains block states, cave interiors, or complete far-distance chunks.
 */
public record ClipmapHeightTilePayload(
        String dimensionKey,
        int nearRenderDistanceChunks,
        int gridCells,
        int levelIndex,
        int cellSpacingBlocks,
        long originX,
        long originZ,
        int minHeight,
        int maxHeight,
        short[] heights) implements CustomPacketPayload {

    private static final int MAX_GRID_CELLS = 256;
    private static final int MAX_DIMENSION_KEY_LENGTH = 256;

    public static final Type<ClipmapHeightTilePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    "realscale", "clipmap_height_tile"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClipmapHeightTilePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ClipmapHeightTilePayload decode(RegistryFriendlyByteBuf buffer) {
                    String dimension = buffer.readUtf(MAX_DIMENSION_KEY_LENGTH);
                    int near = buffer.readVarInt();
                    int cells = buffer.readVarInt();
                    int level = buffer.readVarInt();
                    int spacing = buffer.readVarInt();
                    long originX = buffer.readLong();
                    long originZ = buffer.readLong();
                    int min = buffer.readInt();
                    int max = buffer.readInt();
                    int count = buffer.readVarInt();
                    validate(dimension, near, cells, level, spacing, min, max, count);
                    short[] values = new short[count];
                    for (int i = 0; i < count; i++) {
                        values[i] = buffer.readShort();
                    }
                    return new ClipmapHeightTilePayload(dimension, near, cells,
                            level, spacing, originX, originZ, min, max, values);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer,
                        ClipmapHeightTilePayload value) {
                    short[] values = value.heights();
                    validate(value.dimensionKey(), value.nearRenderDistanceChunks(),
                            value.gridCells(), value.levelIndex(),
                            value.cellSpacingBlocks(), value.minHeight(),
                            value.maxHeight(), values.length);
                    buffer.writeUtf(value.dimensionKey(), MAX_DIMENSION_KEY_LENGTH);
                    buffer.writeVarInt(value.nearRenderDistanceChunks());
                    buffer.writeVarInt(value.gridCells());
                    buffer.writeVarInt(value.levelIndex());
                    buffer.writeVarInt(value.cellSpacingBlocks());
                    buffer.writeLong(value.originX());
                    buffer.writeLong(value.originZ());
                    buffer.writeInt(value.minHeight());
                    buffer.writeInt(value.maxHeight());
                    buffer.writeVarInt(values.length);
                    for (short sample : values) buffer.writeShort(sample);
                }
            };

    public ClipmapHeightTilePayload {
        heights = heights.clone();
        validate(dimensionKey, nearRenderDistanceChunks, gridCells, levelIndex,
                cellSpacingBlocks, minHeight, maxHeight, heights.length);
    }

    @Override
    public short[] heights() {
        return heights.clone();
    }

    private static void validate(String dimension, int near, int cells,
            int level, int spacing, int min, int max, int count) {
        if (dimension == null || dimension.isBlank()
                || dimension.length() > MAX_DIMENSION_KEY_LENGTH) {
            throw new IllegalArgumentException("Invalid clipmap dimension key");
        }
        if (near < 2 || near > 64) {
            throw new IllegalArgumentException("Invalid clipmap near render distance");
        }
        if (cells < 16 || cells > MAX_GRID_CELLS || (cells & (cells - 1)) != 0) {
            throw new IllegalArgumentException("Invalid clipmap grid resolution");
        }
        if (level < 0 || level > 7 || spacing < 1 || spacing > 4096) {
            throw new IllegalArgumentException("Invalid clipmap level or cell spacing");
        }
        if (max <= min) {
            throw new IllegalArgumentException("Invalid clipmap height range");
        }
        int expected = Math.multiplyExact(cells + 1, cells + 1);
        if (count != expected) {
            throw new IllegalArgumentException("Clipmap sample count mismatch: " + count
                    + " != " + expected);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
