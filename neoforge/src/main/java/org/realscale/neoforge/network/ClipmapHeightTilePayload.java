package org.realscale.neoforge.network;

import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.nio.ShortBuffer;

/**
 * One quantized visible-surface height grid. Payload size is bounded and never
 * contains block states, cave interiors, or complete far-distance chunks.
 */
public record ClipmapHeightTilePayload(
        String dimensionKey,
        long requestId,
        int nearRenderDistanceChunks,
        int gridCells,
        int levelIndex,
        int cellSpacingBlocks,
        long originX,
        long originZ,
        int minHeight,
        int maxHeight,
        short[] heights,
        byte[] biomeMaterials) implements CustomPacketPayload {

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
                    long requestId = buffer.readLong();
                    int near = buffer.readVarInt();
                    int cells = buffer.readVarInt();
                    int level = buffer.readVarInt();
                    int spacing = buffer.readVarInt();
                    long originX = buffer.readLong();
                    long originZ = buffer.readLong();
                    int min = buffer.readInt();
                    int max = buffer.readInt();
                    int count=buffer.readVarInt(), materialCount=buffer.readVarInt();
                    validate(dimension,requestId,near,cells,level,spacing,min,max,count,materialCount);
                    short[] values=new short[count];for(int i=0;i<count;i++)values[i]=buffer.readShort();
                    byte[] materials=new byte[materialCount];buffer.readBytes(materials);
                    return new ClipmapHeightTilePayload(dimension,requestId,near,cells,level,spacing,originX,originZ,min,max,values,materials);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer,
                        ClipmapHeightTilePayload value) {
                    short[] values=value.heights;byte[] materials=value.biomeMaterials;
                    validate(value.dimensionKey(),value.requestId(),value.nearRenderDistanceChunks(),
                            value.gridCells(),value.levelIndex(),value.cellSpacingBlocks(),
                            value.minHeight(),value.maxHeight(),values.length,materials.length);
                    buffer.writeUtf(value.dimensionKey(), MAX_DIMENSION_KEY_LENGTH);
                    buffer.writeLong(value.requestId());
                    buffer.writeVarInt(value.nearRenderDistanceChunks());
                    buffer.writeVarInt(value.gridCells());
                    buffer.writeVarInt(value.levelIndex());
                    buffer.writeVarInt(value.cellSpacingBlocks());
                    buffer.writeLong(value.originX());
                    buffer.writeLong(value.originZ());
                    buffer.writeInt(value.minHeight());
                    buffer.writeInt(value.maxHeight());
                    buffer.writeVarInt(values.length);buffer.writeVarInt(materials.length);
                    for(short sample:values)buffer.writeShort(sample);buffer.writeBytes(materials);
                }
            };

    public ClipmapHeightTilePayload(String dimensionKey,long requestId,int nearRenderDistanceChunks,
            int gridCells,int levelIndex,int cellSpacingBlocks,long originX,long originZ,
            int minHeight,int maxHeight,short[] heights){
        this(dimensionKey,requestId,nearRenderDistanceChunks,gridCells,levelIndex,cellSpacingBlocks,
                originX,originZ,minHeight,maxHeight,heights,new byte[heights.length]);
    }
    public ClipmapHeightTilePayload{
        heights=heights.clone();biomeMaterials=biomeMaterials.clone();
        validate(dimensionKey,requestId,nearRenderDistanceChunks,gridCells,levelIndex,cellSpacingBlocks,
                minHeight,maxHeight,heights.length,biomeMaterials.length);
    }
    @Override public short[] heights(){return heights.clone();}
    @Override public byte[] biomeMaterials(){return biomeMaterials.clone();}
    public byte biomeMaterialAt(int index){
        if(index<0||index>=biomeMaterials.length)throw new IndexOutOfBoundsException("biome material index outside tile");
        return biomeMaterials[index];
    }

    /**
     * Copies immutable samples into a caller-owned buffer without exposing or
     * cloning the internal array.
     */
    public void copyHeightsTo(ShortBuffer destination) {
        if (destination == null || destination.remaining() < heights.length) {
            throw new IllegalArgumentException("destination buffer is too small for clipmap heights");
        }
        destination.put(heights);
    }

    /**
     * Copies one tightly packed row-major rectangle without exposing the
     * packet's backing array. The destination receives {@code width * height}
     * samples and can be passed directly to glTexSubImage2D.
     */
    public void copyHeightsRegionTo(int x, int z, int width, int height,
            ShortBuffer destination) {
        int side = gridCells + 1;
        if (x < 0 || z < 0 || width < 1 || height < 1
                || width > side || height > side
                || x > side - width || z > side - height) {
            throw new IllegalArgumentException("height region is outside the tile");
        }
        int required = Math.multiplyExact(width, height);
        if (destination == null || destination.remaining() < required) {
            throw new IllegalArgumentException("destination buffer is too small for height region");
        }
        for (int row = 0; row < height; row++) {
            destination.put(heights, (z + row) * side + x, width);
        }
    }

    private static void validate(String dimension,long requestId,int near,int cells,int level,
            int spacing,int min,int max,int count,int materialCount){
        if (requestId <= 0L) {
            throw new IllegalArgumentException("Clipmap request ID must be positive");
        }
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
        if(count!=expected)throw new IllegalArgumentException("Clipmap sample count mismatch: "+count+" != "+expected);
        if(materialCount!=expected)throw new IllegalArgumentException("Clipmap biome-material count mismatch: "+materialCount+" != "+expected);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
