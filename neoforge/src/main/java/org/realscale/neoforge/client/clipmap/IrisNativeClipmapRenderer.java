package org.realscale.neoforge.client.clipmap;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.realscale.neoforge.network.ClipmapHeightTilePayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shader-pack-native Geometry Clipmap renderer for Iris 1.21.1.
 *
 * <p>This path deliberately does not create an OpenGL program or bind a custom
 * framebuffer. It is invoked from inside LevelRenderer.renderSectionLayer,
 * while Iris has selected the current terrain phase and G-buffer target. The
 * mesh is uploaded with Iris's TERRAIN vertex format (the block-terrain format
 * extended with Iris-specific attributes) and drawn with the
 * already-active vanilla/Iris terrain shader, so depth, normals, lightmap,
 * material routing, and shadow-map passes follow the normal terrain path.
 */
final class IrisNativeClipmapRenderer {
    private static final int LEVEL_COUNT = 6;
    private static final int MORPH_BAND_CELLS = 8;
    // Distant exterior surfaces have sky exposure but no reliable block-light sample.
    // FULL_SKY preserves the active day/night lightmap instead of forcing full-bright.
    private static final int SURFACE_SKY_LIGHT = LightTexture.FULL_SKY;
    private static final int NO_OVERLAY = OverlayTexture.NO_OVERLAY;
    private static final int INITIAL_BUFFER_BYTES = 1 << 20;

    private static VertexBuffer solidBuffer;
    private static VertexBuffer cutoutBuffer;
    private static VertexBuffer waterBuffer;

    private static String cachedDimension;
    private static long cachedRequestId = Long.MIN_VALUE;
    private static long cachedCenterX = Long.MIN_VALUE;
    private static long cachedCenterZ = Long.MIN_VALUE;
    private static int cachedNearChunks = -1;
    private static int cachedGridCells = -1;
    private static int cachedBaseSpacing = -1;
    private static int cachedSeaLevel = Integer.MIN_VALUE;
    private static List<ClipmapHeightTilePayload> cachedTiles = List.of();
    private static List<VisibleSurfaceProxy> cachedProxies = List.of();
    private static boolean missingChunkOffsetReported;
    private static boolean nativeDrawFailureReported;

    private IrisNativeClipmapRenderer() {}

    static boolean renderLayer(RenderType renderType,
            Map<Integer, ClipmapHeightTilePayload> tiles,
            String dimension, long requestId, long centerX, long centerZ,
            int nearChunks, List<VisibleSurfaceProxy> proxies, Level level,
            double cameraX, double cameraY, double cameraZ,
            Matrix4f frustumMatrix, Matrix4f projectionMatrix,
            boolean shadowPass) {
        if (renderType != RenderType.solid()
                && renderType != RenderType.cutout()
                && renderType != RenderType.translucent()) {
            return false;
        }
        if (renderType == RenderType.translucent() && shadowPass) {
            // Iris does not render water into the shadow terrain phase.
            return false;
        }
        if (tiles == null || tiles.size() < LEVEL_COUNT || level == null
                || dimension == null || dimension.isBlank() || requestId <= 0L
                || frustumMatrix == null || projectionMatrix == null) {
            return false;
        }

        RenderSystem.assertOnRenderThread();
        List<VisibleSurfaceProxy> safeProxies = proxies == null ? List.of() : proxies;
        int seaLevel = level.getSeaLevel();
        if (!cacheMatches(tiles, dimension, requestId, centerX, centerZ,
                nearChunks, seaLevel, safeProxies)) {
            if (!rebuild(tiles, dimension, requestId, centerX, centerZ,
                    nearChunks, seaLevel, safeProxies)) {
                return false;
            }
        }

        VertexBuffer target = renderType == RenderType.solid()
                ? solidBuffer
                : renderType == RenderType.cutout() ? cutoutBuffer : waterBuffer;
        if (target == null) return false;

        // Sodium unbinds its GL program before dispatching NeoForge's
        // AFTER_* terrain-stage event. Re-enter the matching RenderType state
        // here so Iris selects its ShaderMap terrain ShaderInstance for this phase.
        renderType.setupRenderState();
        ShaderInstance shader = RenderSystem.getShader();
        if (shader == null || shader.CHUNK_OFFSET == null) {
            renderType.clearRenderState();
            if (!missingChunkOffsetReported) {
                missingChunkOffsetReported = true;
                System.err.println("[RealScale Iris Clipmap] Active Iris terrain shader has no ChunkOffset uniform; native draw skipped safely");
            }
            return false;
        }

        // GeometryClipmapClient extends the shared main-frame projection at
        // AFTER_SKY and keeps Iris's captured G-buffer matrix synchronized until
        // AFTER_LEVEL. Never change projection per layer: the depth written here
        // must be interpreted with the same matrix during Iris's later composites.
        Uniform chunkOffset = shader.CHUNK_OFFSET;
        boolean shaderApplied = false;
        try {
            // RenderType.setupRenderState selects the Iris ShaderInstance but,
            // unlike BufferUploader.drawWithShader, does not call apply().
            // Direct VertexBuffer draws must bind the program explicitly before
            // any Uniform upload or GL_INVALID_OPERATION (program not linked)
            // is raised by the driver.
            shader.apply();
            shaderApplied = true;
            chunkOffset.set((float) (centerX - cameraX),
                    (float) -cameraY, (float) (centerZ - cameraZ));
            chunkOffset.upload();
            target.bind();
            target.draw();
            return true;
        } catch (RuntimeException | LinkageError failure) {
            if (!nativeDrawFailureReported) {
                nativeDrawFailureReported = true;
                System.err.println("[RealScale Iris Clipmap] native terrain draw failed safely: "
                        + failure.getClass().getSimpleName() + ": " + failure.getMessage());
            }
            return false;
        } finally {
            VertexBuffer.unbind();
            try {
                if (shaderApplied) {
                    chunkOffset.set(0.0f, 0.0f, 0.0f);
                    chunkOffset.upload();
                    shader.clear();
                }
            } finally {
                renderType.clearRenderState();
            }
        }
    }

    static void dispose() {
        RenderSystem.assertOnRenderThread();
        disposeBuffers();
        cachedDimension = null;
        cachedRequestId = Long.MIN_VALUE;
        cachedCenterX = Long.MIN_VALUE;
        cachedCenterZ = Long.MIN_VALUE;
        cachedNearChunks = -1;
        cachedGridCells = -1;
        cachedBaseSpacing = -1;
        cachedSeaLevel = Integer.MIN_VALUE;
        cachedTiles = List.of();
        cachedProxies = List.of();
        missingChunkOffsetReported = false;
        nativeDrawFailureReported = false;
    }

    private static boolean cacheMatches(Map<Integer, ClipmapHeightTilePayload> tiles,
            String dimension, long requestId, long centerX, long centerZ,
            int nearChunks, int seaLevel, List<VisibleSurfaceProxy> proxies) {
        ClipmapHeightTilePayload first = tiles.get(0);
        if (first == null) return false;
        if (!dimension.equals(cachedDimension) || requestId != cachedRequestId
                || centerX != cachedCenterX || centerZ != cachedCenterZ
                || nearChunks != cachedNearChunks || seaLevel != cachedSeaLevel
                || proxies != cachedProxies || cachedTiles.size() != LEVEL_COUNT
                || cachedGridCells != first.gridCells()
                || cachedBaseSpacing != first.cellSpacingBlocks()) {
            return false;
        }
        for (int level = 0; level < LEVEL_COUNT; level++) {
            if (cachedTiles.get(level) != tiles.get(level)) return false;
        }
        return true;
    }

    private static boolean rebuild(Map<Integer, ClipmapHeightTilePayload> tiles,
            String dimension, long requestId, long centerX, long centerZ,
            int nearChunks, int seaLevel, List<VisibleSurfaceProxy> proxies) {
        long started = System.nanoTime();
        disposeBuffers();
        try {
            List<ClipmapHeightTilePayload> snapshot = new ArrayList<>(LEVEL_COUNT);
            ClipmapHeightTilePayload first = tiles.get(0);
            if (first == null || first.gridCells() < 16) return false;
            int cells = first.gridCells();
            int baseSpacing = first.cellSpacingBlocks();

            for (int level = 0; level < LEVEL_COUNT; level++) {
                ClipmapHeightTilePayload tile = tiles.get(level);
                if (tile == null || tile.levelIndex() != level
                        || tile.requestId() != requestId
                        || !dimension.equals(tile.dimensionKey())
                        || tile.gridCells() != cells
                        || tile.nearRenderDistanceChunks() != nearChunks
                        || tile.cellSpacingBlocks() != (baseSpacing << level)
                        || tile.minHeight() >= tile.maxHeight()) {
                    throw new IllegalStateException("inconsistent six-level Iris clipmap snapshot at level " + level);
                }
                snapshot.add(tile);
            }

            VertexFormat irisTerrainFormat = IrisRenderCompatibility.terrainVertexFormat();
            if (irisTerrainFormat == null) {
                throw new IllegalStateException("Iris terrain vertex format is unavailable");
            }
            Sprites sprites = Sprites.load(Minecraft.getInstance());
            try (ByteBufferBuilder solidBytes = new ByteBufferBuilder(INITIAL_BUFFER_BYTES);
                 ByteBufferBuilder cutoutBytes = new ByteBufferBuilder(INITIAL_BUFFER_BYTES);
                 ByteBufferBuilder waterBytes = new ByteBufferBuilder(INITIAL_BUFFER_BYTES)) {
                BufferBuilder solidBuilder = new BufferBuilder(
                        solidBytes, VertexFormat.Mode.QUADS, irisTerrainFormat);
                BufferBuilder cutoutBuilder = new BufferBuilder(
                        cutoutBytes, VertexFormat.Mode.QUADS, irisTerrainFormat);
                BufferBuilder waterBuilder = new BufferBuilder(
                        waterBytes, VertexFormat.Mode.QUADS, irisTerrainFormat);

                for (int level = 0; level < LEVEL_COUNT; level++) {
                    appendTerrain(snapshot.get(level), level, nearChunks, seaLevel,
                            sprites, solidBuilder, waterBuilder);
                }
                for (VisibleSurfaceProxy proxy : proxies) {
                    appendProxy(proxy, centerX, centerZ, sprites,
                            solidBuilder, cutoutBuilder);
                }

                solidBuffer = upload(solidBuilder);
                cutoutBuffer = upload(cutoutBuilder);
                waterBuffer = upload(waterBuilder);
            }

            cachedDimension = dimension;
            cachedRequestId = requestId;
            cachedCenterX = centerX;
            cachedCenterZ = centerZ;
            cachedNearChunks = nearChunks;
            cachedGridCells = cells;
            cachedBaseSpacing = baseSpacing;
            cachedSeaLevel = seaLevel;
            cachedTiles = List.copyOf(snapshot);
            cachedProxies = proxies;

            if (Boolean.getBoolean("realscale.experimental.geometryclipmap.debug")) {
                System.out.printf(java.util.Locale.ROOT,
                        "[RealScale Iris Clipmap] nativeMeshesBuilt=%.1fms levels=%d proxies=%d solid=%s cutout=%s water=%s heightRanges=%s%n",
                        (System.nanoTime() - started) / 1_000_000.0,
                        LEVEL_COUNT, proxies.size(), solidBuffer != null,
                        cutoutBuffer != null, waterBuffer != null,
                        heightRangeSummary(snapshot));
            }
            return solidBuffer != null || cutoutBuffer != null || waterBuffer != null;
        } catch (RuntimeException failure) {
            disposeBuffers();
            cachedDimension = null;
            cachedTiles = List.of();
            cachedProxies = List.of();
            System.err.println("[RealScale Iris Clipmap] native mesh build failed safely: "
                    + failure.getClass().getSimpleName() + ": " + failure.getMessage());
            return false;
        }
    }

    private static String heightRangeSummary(List<ClipmapHeightTilePayload> tiles) {
        StringBuilder summary = new StringBuilder();
        for (ClipmapHeightTilePayload tile : tiles) {
            short[] samples = tile.heights();
            int sampleMin = 0xFFFF;
            int sampleMax = 0;
            long sum = 0L;
            long sumSquares = 0L;
            for (short raw : samples) {
                int sample = Short.toUnsignedInt(raw);
                sampleMin = Math.min(sampleMin, sample);
                sampleMax = Math.max(sampleMax, sample);
                sum += sample;
                sumSquares += (long) sample * sample;
            }
            double mean = sum / (double) samples.length;
            double variance = Math.max(0.0,
                    sumSquares / (double) samples.length - mean * mean);
            if (summary.length() > 0) summary.append(',');
            summary.append('L').append(tile.levelIndex()).append('[')
                    .append(tile.minHeight()).append("..").append(tile.maxHeight())
                    .append(";u16=").append(sampleMin).append("..").append(sampleMax)
                    .append(";sd=").append(String.format(java.util.Locale.ROOT, "%.0f",
                            Math.sqrt(variance)))
                    .append(']');
        }
        return summary.toString();
    }

    private static VertexBuffer upload(BufferBuilder builder) {
        MeshData mesh = builder.build();
        if (mesh == null) return null;
        VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        try (mesh) {
            buffer.bind();
            buffer.upload(mesh);
            VertexBuffer.unbind();
            return buffer;
        } catch (RuntimeException | Error failure) {
            VertexBuffer.unbind();
            buffer.close();
            throw failure;
        }
    }

    private static void appendTerrain(ClipmapHeightTilePayload tile, int level,
            int nearChunks, int seaLevel, Sprites sprites,
            BufferBuilder solid, BufferBuilder water) {
        int cells = tile.gridCells();
        int side = cells + 1;
        int baseCount = side * side;
        int spacing = tile.cellSpacingBlocks();
        int holeRadius = level == 0
                ? Math.toIntExact((nearChunks * 16L
                        - ((long) tile.cellSpacingBlocks() << (LEVEL_COUNT - 1)) / 2L)
                        / spacing)
                : cells / 4;
        int[] indices = ClipmapRingMesh.indices(cells, holeRadius);
        short[] heights = tile.heights();
        float skirtDepth = Math.min(32.0f, Math.max(4.0f, spacing * 0.125f));

        int[] gridX = new int[3];
        int[] gridZ = new int[3];
        int[] layer = new int[3];
        float[] topHeight = new float[3];
        float[] x = new float[3];
        float[] y = new float[3];
        float[] z = new float[3];
        float[] normal = new float[3];
        for (int p = 0; p < indices.length; p += 3) {
            boolean skirt = false;
            float averageHeight = 0.0f;

            for (int vertex = 0; vertex < 3; vertex++) {
                int index = indices[p + vertex];
                layer[vertex] = index / baseCount;
                int gridIndex = index % baseCount;
                gridX[vertex] = gridIndex % side;
                gridZ[vertex] = gridIndex / side;
                skirt |= layer[vertex] != 0;
                topHeight[vertex] = morphedHeight(heights, cells,
                        tile.minHeight(), tile.maxHeight(),
                        gridX[vertex], gridZ[vertex], level);
                averageHeight += topHeight[vertex] / 3.0f;
                x[vertex] = (gridX[vertex] - cells * 0.5f) * spacing;
                z[vertex] = (gridZ[vertex] - cells * 0.5f) * spacing;
            }

            if (!skirt && averageHeight <= seaLevel) {
                for (int vertex = 0; vertex < 3; vertex++) {
                    y[vertex] = seaLevel + 0.025f;
                }
                triangleNormalInto(x, y, z, normal);
                emitTriangle(water, sprites.water(), 0xAFC6D8, 190,
                        x, y, z, gridX, gridZ, layer, normal);
            } else if (averageHeight > seaLevel) {
                for (int vertex = 0; vertex < 3; vertex++) {
                    y[vertex] = Math.max(topHeight[vertex], seaLevel)
                            - layer[vertex] * skirtDepth;
                }
                triangleNormalInto(x, y, z, normal);
                TextureAtlasSprite materialSprite;
                int materialRgb;
                if (averageHeight >= seaLevel + 677.0f) {
                    materialSprite = sprites.snow();
                    materialRgb = 0xF1F4F6;
                } else if (averageHeight >= seaLevel + 197.0f || normal[1] < 0.90f) {
                    materialSprite = sprites.stone();
                    materialRgb = 0xD0D0CC;
                } else if (averageHeight <= seaLevel + 25.0f && normal[1] > 0.94f) {
                    materialSprite = sprites.sand();
                    materialRgb = 0xE8D9B0;
                } else {
                    materialSprite = sprites.grass();
                    materialRgb = 0xB5D39D;
                }
                emitTriangle(solid, materialSprite, materialRgb, 255,
                        x, y, z, gridX, gridZ, layer, normal);
            }
        }
    }

    private static float morphedHeight(short[] samples, int cells,
            int minHeight, int maxHeight, int x, int z, int level) {
        float height = sampleHeight(samples, cells, minHeight, maxHeight, x, z);
        if (level >= LEVEL_COUNT - 1) return height;

        int edgeCells = Math.min(Math.min(x, z),
                Math.min(cells - x, cells - z));
        if (edgeCells >= MORPH_BAND_CELLS) return height;

        int parentX = (x / 2) * 2;
        int parentZ = (z / 2) * 2;
        float blendX = (x - parentX) * 0.5f;
        float blendZ = (z - parentZ) * 0.5f;
        float h00 = sampleHeight(samples, cells, minHeight, maxHeight, parentX, parentZ);
        float h10 = sampleHeight(samples, cells, minHeight, maxHeight, parentX + 2, parentZ);
        float h01 = sampleHeight(samples, cells, minHeight, maxHeight, parentX, parentZ + 2);
        float h11 = sampleHeight(samples, cells, minHeight, maxHeight, parentX + 2, parentZ + 2);
        float parent = lerp(lerp(h00, h10, blendX), lerp(h01, h11, blendX), blendZ);
        float morph = 1.0f - smoothstep(0.0f, MORPH_BAND_CELLS, edgeCells);
        return lerp(height, parent, morph);
    }

    private static float sampleHeight(short[] samples, int cells,
            int minHeight, int maxHeight, int x, int z) {
        int side = cells + 1;
        int wrappedX = Math.floorMod(x, side);
        int wrappedZ = Math.floorMod(z, side);
        float normalized = (samples[wrappedZ * side + wrappedX] & 0xFFFF) / 65535.0f;
        return minHeight + normalized * (maxHeight - minHeight);
    }

    private static void emitTriangle(BufferBuilder builder, TextureAtlasSprite sprite,
            int rgb, int alpha, float[] x, float[] y, float[] z,
            int[] gridX, int[] gridZ, int[] layer, float[] normal) {
        int minX = Math.min(gridX[0], Math.min(gridX[1], gridX[2]));
        int maxX = Math.max(gridX[0], Math.max(gridX[1], gridX[2]));
        int minZ = Math.min(gridZ[0], Math.min(gridZ[1], gridZ[2]));
        int maxZ = Math.max(gridZ[0], Math.max(gridZ[1], gridZ[2]));
        for (int vertex = 0; vertex < 3; vertex++) {
            float u = maxX == minX ? layer[vertex] * 16.0f
                    : (gridX[vertex] - minX) * 16.0f;
            float v = maxZ == minZ ? layer[vertex] * 16.0f
                    : (gridZ[vertex] - minZ) * 16.0f;
            putVertex(builder, sprite, rgb, alpha,
                    x[vertex], y[vertex], z[vertex], u, v, normal);
        }
        // RenderType.solid/cutout/translucent use QUADS. A repeated final
        // vertex represents one triangle without introducing a second face.
        float u = maxX == minX ? layer[2] * 16.0f : (gridX[2] - minX) * 16.0f;
        float v = maxZ == minZ ? layer[2] * 16.0f : (gridZ[2] - minZ) * 16.0f;
        putVertex(builder, sprite, rgb, alpha, x[2], y[2], z[2], u, v, normal);
    }

    private static void triangleNormalInto(float[] x, float[] y, float[] z,
            float[] destination) {
        float ax = x[1] - x[0];
        float ay = y[1] - y[0];
        float az = z[1] - z[0];
        float bx = x[2] - x[0];
        float by = y[2] - y[0];
        float bz = z[2] - z[0];
        float nx = ay * bz - az * by;
        float ny = az * bx - ax * bz;
        float nz = ax * by - ay * bx;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1.0e-6f || !Float.isFinite(length)) {
            destination[0] = 0.0f;
            destination[1] = 1.0f;
            destination[2] = 0.0f;
            return;
        }
        destination[0] = nx / length;
        destination[1] = ny / length;
        destination[2] = nz / length;
    }

    private static float[] triangleNormal(float[] x, float[] y, float[] z) {
        float[] normal = new float[3];
        triangleNormalInto(x, y, z, normal);
        return normal;
    }

    private static void appendProxy(VisibleSurfaceProxy proxy,
            long centerX, long centerZ, Sprites sprites,
            BufferBuilder solid, BufferBuilder cutout) {
        float x = (float) ((long) proxy.x() - centerX);
        float y = proxy.y();
        float z = (float) ((long) proxy.z() - centerZ);
        float radius = proxy.radius();
        float height = proxy.height();
        switch (proxy.type()) {
            case VisibleSurfaceProxy.TREE_CANOPY -> {
                float trunkRadius = Math.max(0.22f, radius * 0.12f);
                emitBox(solid, sprites.wood(), 0x806044,
                        x - trunkRadius, y, z - trunkRadius,
                        x + trunkRadius, y + height * 0.43f, z + trunkRadius);
                float canopyBottom = y + height * 0.18f;
                float canopyTop = y + height;
                emitDoubleSidedQuad(cutout, sprites.leaves(), proxy.rgb(),
                        new float[][]{
                                {x - radius, canopyBottom, z},
                                {x + radius, canopyBottom, z},
                                {x + radius, canopyTop, z},
                                {x - radius, canopyTop, z}
                        });
                emitDoubleSidedQuad(cutout, sprites.leaves(), proxy.rgb(),
                        new float[][]{
                                {x, canopyBottom, z - radius},
                                {x, canopyBottom, z + radius},
                                {x, canopyTop, z + radius},
                                {x, canopyTop, z - radius}
                        });
                float diagonal = radius * 0.70710678f;
                emitDoubleSidedQuad(cutout, sprites.leaves(), proxy.rgb(),
                        new float[][]{
                                {x - diagonal, canopyBottom, z - diagonal},
                                {x + diagonal, canopyBottom, z + diagonal},
                                {x + diagonal, canopyTop, z + diagonal},
                                {x - diagonal, canopyTop, z - diagonal}
                        });
            }
            case VisibleSurfaceProxy.FLOATING_ISLAND -> appendIsland(
                    solid, sprites, x, y, z, radius, height);
            case VisibleSurfaceProxy.BUILDING_EXTERIOR -> emitBox(
                    solid, sprites.bricks(), proxy.rgb(),
                    x - radius, y, z - radius,
                    x + radius, y + height, z + radius);
            case VisibleSurfaceProxy.CAVE_MOUTH -> appendCaveMouth(
                    solid, sprites, x, y, z, radius, height, proxy.rgb());
            default -> { }
        }
    }

    private static void appendIsland(BufferBuilder builder, Sprites sprites,
            float x, float y, float z, float radius, float height) {
        final int sides = 8;
        float bottomRadius = radius * 0.58f;
        for (int i = 0; i < sides; i++) {
            double a0 = Math.PI * 2.0 * i / sides;
            double a1 = Math.PI * 2.0 * (i + 1) / sides;
            float topX0 = x + (float) Math.cos(a0) * radius;
            float topZ0 = z + (float) Math.sin(a0) * radius;
            float topX1 = x + (float) Math.cos(a1) * radius;
            float topZ1 = z + (float) Math.sin(a1) * radius;
            float bottomX0 = x + (float) Math.cos(a0) * bottomRadius;
            float bottomZ0 = z + (float) Math.sin(a0) * bottomRadius;
            float bottomX1 = x + (float) Math.cos(a1) * bottomRadius;
            float bottomZ1 = z + (float) Math.sin(a1) * bottomRadius;
            emitDoubleSidedQuad(builder, sprites.stone(), 0x9B978F,
                    new float[][]{
                            {topX0, y + height, topZ0},
                            {bottomX0, y, bottomZ0},
                            {bottomX1, y, bottomZ1},
                            {topX1, y + height, topZ1}
                    });
            float centerX = x;
            float centerZ = z;
            emitTriangleAsQuad(builder, sprites.grass(), 0xB5D39D,
                    new float[][]{
                            {centerX, y + height + 0.01f, centerZ},
                            {topX1, y + height + 0.01f, topZ1},
                            {topX0, y + height + 0.01f, topZ0}
                    });
        }
    }

    private static void appendCaveMouth(BufferBuilder builder, Sprites sprites,
            float x, float y, float z, float radius, float height, int rgb) {
        float left = x - radius * 0.55f;
        float right = x + radius * 0.55f;
        float top = y + height * 0.78f;
        float bottom = y + height * 0.08f;
        emitDoubleSidedQuad(builder, sprites.black(), 0x171717,
                new float[][]{
                        {left, bottom, z},
                        {right, bottom, z},
                        {right, top, z},
                        {left, top, z}
                });
        float rim = Math.max(0.35f, radius * 0.12f);
        emitBox(builder, sprites.stone(), rgb,
                left - rim, bottom, z - rim * 0.25f,
                left, top, z + rim * 0.25f);
        emitBox(builder, sprites.stone(), rgb,
                right, bottom, z - rim * 0.25f,
                right + rim, top, z + rim * 0.25f);
        emitBox(builder, sprites.stone(), rgb,
                left - rim, top, z - rim * 0.25f,
                right + rim, top + rim, z + rim * 0.25f);
    }

    private static void emitBox(BufferBuilder builder, TextureAtlasSprite sprite,
            int rgb, float x0, float y0, float z0,
            float x1, float y1, float z1) {
        emitDoubleSidedQuad(builder, sprite, rgb, new float[][]{
                {x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}});
        emitDoubleSidedQuad(builder, sprite, rgb, new float[][]{
                {x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}});
        emitDoubleSidedQuad(builder, sprite, rgb, new float[][]{
                {x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}});
        emitDoubleSidedQuad(builder, sprite, rgb, new float[][]{
                {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}});
        emitDoubleSidedQuad(builder, sprite, rgb, new float[][]{
                {x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}});
    }

    private static void emitTriangleAsQuad(BufferBuilder builder,
            TextureAtlasSprite sprite, int rgb, float[][] p) {
        float[] x = {p[0][0], p[1][0], p[2][0]};
        float[] y = {p[0][1], p[1][1], p[2][1]};
        float[] z = {p[0][2], p[1][2], p[2][2]};
        float[] normal = triangleNormal(x, y, z);
        putVertex(builder, sprite, rgb, 255, x[0], y[0], z[0], 0.0f, 0.0f, normal);
        putVertex(builder, sprite, rgb, 255, x[1], y[1], z[1], 16.0f, 0.0f, normal);
        putVertex(builder, sprite, rgb, 255, x[2], y[2], z[2], 16.0f, 16.0f, normal);
        putVertex(builder, sprite, rgb, 255, x[2], y[2], z[2], 16.0f, 16.0f, normal);
    }

    private static void emitDoubleSidedQuad(BufferBuilder builder,
            TextureAtlasSprite sprite, int rgb, float[][] p) {
        emitQuad(builder, sprite, rgb, p[0], p[1], p[2], p[3]);
        emitQuad(builder, sprite, rgb, p[3], p[2], p[1], p[0]);
    }

    private static void emitQuad(BufferBuilder builder, TextureAtlasSprite sprite,
            int rgb, float[] a, float[] b, float[] c, float[] d) {
        float[] normal = quadNormal(a, b, c);
        putVertex(builder, sprite, rgb, 255, a[0], a[1], a[2], 0.0f, 0.0f, normal);
        putVertex(builder, sprite, rgb, 255, b[0], b[1], b[2], 16.0f, 0.0f, normal);
        putVertex(builder, sprite, rgb, 255, c[0], c[1], c[2], 16.0f, 16.0f, normal);
        putVertex(builder, sprite, rgb, 255, d[0], d[1], d[2], 0.0f, 16.0f, normal);
    }

    private static float[] quadNormal(float[] a, float[] b, float[] c) {
        float[] x = {a[0], b[0], c[0]};
        float[] y = {a[1], b[1], c[1]};
        float[] z = {a[2], b[2], c[2]};
        return triangleNormal(x, y, z);
    }

    private static void putVertex(BufferBuilder builder, TextureAtlasSprite sprite,
            int rgb, int alpha, float x, float y, float z,
            float u, float v, float[] normal) {
        builder.addVertex(x, y, z)
                .setColor((rgb >>> 16) & 0xFF, (rgb >>> 8) & 0xFF,
                        rgb & 0xFF, alpha)
                .setUv(sprite.getU(u), sprite.getV(v))
                .setLight(SURFACE_SKY_LIGHT)
                .setNormal(normal[0], normal[1], normal[2]);
    }

    private static float lerp(float from, float to, float amount) {
        return from + (to - from) * amount;
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = Math.max(0.0f, Math.min(1.0f,
                (value - edge0) / (edge1 - edge0)));
        return t * t * (3.0f - 2.0f * t);
    }

    private static void disposeBuffers() {
        close(solidBuffer);
        close(cutoutBuffer);
        close(waterBuffer);
        solidBuffer = null;
        cutoutBuffer = null;
        waterBuffer = null;
    }

    private static void close(VertexBuffer buffer) {
        if (buffer != null) buffer.close();
    }

    private record Sprites(TextureAtlasSprite grass, TextureAtlasSprite stone,
            TextureAtlasSprite sand, TextureAtlasSprite snow,
            TextureAtlasSprite water, TextureAtlasSprite leaves,
            TextureAtlasSprite wood, TextureAtlasSprite bricks,
            TextureAtlasSprite black) {
        private static Sprites load(Minecraft minecraft) {
            BlockModelShaper shaper = minecraft.getBlockRenderer().getBlockModelShaper();
            return new Sprites(
                    particle(shaper, Blocks.GRASS_BLOCK.defaultBlockState()),
                    particle(shaper, Blocks.STONE.defaultBlockState()),
                    particle(shaper, Blocks.SAND.defaultBlockState()),
                    particle(shaper, Blocks.SNOW_BLOCK.defaultBlockState()),
                    particle(shaper, Blocks.WATER.defaultBlockState()),
                    particle(shaper, Blocks.OAK_LEAVES.defaultBlockState()),
                    particle(shaper, Blocks.OAK_LOG.defaultBlockState()),
                    particle(shaper, Blocks.STONE_BRICKS.defaultBlockState()),
                    particle(shaper, Blocks.BLACK_CONCRETE.defaultBlockState()));
        }

        private static TextureAtlasSprite particle(BlockModelShaper shaper, BlockState state) {
            return shaper.getParticleIcon(state);
        }
    }
}
