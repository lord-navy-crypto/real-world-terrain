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
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
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
    private static final int MAX_TEXTURED_DETAIL_LEVEL = 2;
    private static final int MORPH_BAND_CELLS = 8;
    // Distant exterior surfaces have sky exposure but no reliable block-light sample.
    // FULL_SKY preserves the active day/night lightmap instead of forcing full-bright.
    private static final int SURFACE_SKY_LIGHT = LightTexture.FULL_SKY;
    private static final int NO_OVERLAY = OverlayTexture.NO_OVERLAY;
    private static final int INITIAL_BUFFER_BYTES = 1 << 20;

    private static VertexBuffer solidBuffer;
    private static VertexBuffer farSolidBuffer;
    private static VertexBuffer cutoutBuffer;
    private static VertexBuffer proxySolidBuffer;
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
    // The diagnostic test JAR reports real depth-passing samples on the
    // user's GPU as well as CI. Poll asynchronously; never wait on the GPU.
    private static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(
            "realscale.experimental.geometryclipmap.debug", "true"));
    private static final boolean SMOKE_VISIBILITY_PROBE =
            Boolean.getBoolean("realscale.experimental.geometryclipmap.smokeCamera");
    private static boolean smokeVisibilityPassed;
    private static long lastSmokeProbeNanos;
    private static int pendingVisibilityQuery;
    private static int visibilityAttempts;
    private static double visibilityCameraX;
    private static double visibilityCameraY;
    private static double visibilityCameraZ;
    private static int visibilityProgram;
    private static int visibilityFramebuffer;

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
        if (tiles == null || tiles.isEmpty() || level == null
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

        if (safeProxies != cachedProxies) {
            rebuildProxies(safeProxies, centerX, centerZ);
        }

        VertexBuffer target = renderType == RenderType.solid()
                ? (solidBuffer != null ? solidBuffer : farSolidBuffer != null ? farSolidBuffer : proxySolidBuffer)
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
            // A selected ShaderInstance is not initialized by setupRenderState.
            // Sodium's own chunk renderer uses a different shader program; the
            // vanilla/Iris instance can therefore retain stale matrices/samplers.
            shader.setDefaultUniforms(VertexFormat.Mode.TRIANGLES,
                    frustumMatrix, projectionMatrix, Minecraft.getInstance().getWindow());
            shader.apply();
            shaderApplied = true;
            chunkOffset.set((float) (centerX - cameraX),
                    (float) -cameraY, (float) (centerZ - cameraZ));
            chunkOffset.upload();
            // Keep far terrain separate from the inner overlap and proxies.
            // A passing visibility query must come from LOD 2..6, not nearby blocks.
            if (renderType == RenderType.solid()) {
                if (solidBuffer != null) {
                    solidBuffer.bind();
                    solidBuffer.draw();
                }
                if (proxySolidBuffer != null) {
                    proxySolidBuffer.bind();
                    proxySolidBuffer.draw();
                }
                if (farSolidBuffer != null) {
                    farSolidBuffer.bind();
                    drawWithVisibilityProbe(farSolidBuffer, renderType, shadowPass, cameraX, cameraY, cameraZ);
                }
            } else {
                target.bind();
                target.draw();
            }
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

    private static void drawWithVisibilityProbe(VertexBuffer target,
            RenderType renderType, boolean shadowPass,
            double cameraX, double cameraY, double cameraZ) {
        if (shadowPass || renderType != RenderType.solid()
                || (!DEBUG && !SMOKE_VISIBILITY_PROBE)) {
            target.draw();
            return;
        }
        // Retrieve only completed queries. This is supported on OpenGL 4.1
        // (including Apple Silicon), and cannot block on an unfinished draw.
        if (pendingVisibilityQuery != 0
                && GL15.glGetQueryObjecti(pendingVisibilityQuery,
                        GL15.GL_QUERY_RESULT_AVAILABLE) != 0) {
            int samples = GL15.glGetQueryObjecti(pendingVisibilityQuery,
                    GL15.GL_QUERY_RESULT);
            GL15.glDeleteQueries(pendingVisibilityQuery);
            pendingVisibilityQuery = 0;
            smokeVisibilityPassed = samples > 0;
            System.out.printf(java.util.Locale.ROOT,
                    "[RealScale Iris Clipmap] FAR_SOLID_VISIBLE_SAMPLES=%d "
                            + "cameraY=%.1f cameraX=%.1f cameraZ=%.1f program=%d drawFramebuffer=%d attempt=%d levels=%d center=(%d,%d)%n",
                    samples, visibilityCameraY, visibilityCameraX, visibilityCameraZ,
                    visibilityProgram, visibilityFramebuffer, visibilityAttempts,
                    cachedTiles.size(), cachedCenterX, cachedCenterZ);
        }

        long now = System.nanoTime();
        if (pendingVisibilityQuery != 0 || smokeVisibilityPassed
                || (SMOKE_VISIBILITY_PROBE && cameraY < 2300.0)
                || (!SMOKE_VISIBILITY_PROBE && visibilityAttempts >= 4)
                || (lastSmokeProbeNanos != 0L && now - lastSmokeProbeNanos < 2_000_000_000L)
                || GL15.glGetQueryi(GL15.GL_SAMPLES_PASSED, GL15.GL_CURRENT_QUERY) != 0) {
            target.draw();
            return;
        }
        lastSmokeProbeNanos = now;
        visibilityAttempts++;
        visibilityCameraX = cameraX;
        visibilityCameraY = cameraY;
        visibilityCameraZ = cameraZ;
        visibilityProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        visibilityFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int query = GL15.glGenQueries();
        try {
            GL15.glBeginQuery(GL15.GL_SAMPLES_PASSED, query);
            try {
                target.draw();
            } finally {
                GL15.glEndQuery(GL15.GL_SAMPLES_PASSED);
            }
            pendingVisibilityQuery = query;
            query = 0;
        } finally {
            if (query != 0) GL15.glDeleteQueries(query);
        }
    }

    private static void resetVisibilityProbe() {
        if (pendingVisibilityQuery != 0) {
            GL15.glDeleteQueries(pendingVisibilityQuery);
            pendingVisibilityQuery = 0;
        }
        smokeVisibilityPassed = false;
        lastSmokeProbeNanos = 0L;
        visibilityAttempts = 0;
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
        resetVisibilityProbe();
    }

    private static boolean cacheMatches(Map<Integer, ClipmapHeightTilePayload> tiles,
            String dimension, long requestId, long centerX, long centerZ,
            int nearChunks, int seaLevel, List<VisibleSurfaceProxy> proxies) {
        ClipmapHeightTilePayload first = tiles.get(0);
        if (first == null) return false;
        if (!dimension.equals(cachedDimension) || requestId != cachedRequestId
                || centerX != cachedCenterX || centerZ != cachedCenterZ
                || nearChunks != cachedNearChunks || seaLevel != cachedSeaLevel
                || cachedTiles.size() != tiles.size()
                || cachedGridCells != first.gridCells()
                || cachedBaseSpacing != first.cellSpacingBlocks()) {
            return false;
        }
        for (int level = 0; level < tiles.size(); level++) {
            if (cachedTiles.get(level) != tiles.get(level)) return false;
        }
        return true;
    }

    private static boolean rebuild(Map<Integer, ClipmapHeightTilePayload> tiles,
            String dimension, long requestId, long centerX, long centerZ,
            int nearChunks, int seaLevel, List<VisibleSurfaceProxy> proxies) {
        long started = System.nanoTime();
        VertexBuffer nextSolid = null, nextFar = null, nextWater = null;
        try {
            List<ClipmapHeightTilePayload> snapshot = new ArrayList<>(LEVEL_COUNT);
            ClipmapHeightTilePayload first = tiles.get(0);
            if (first == null || first.gridCells() < 16) return false;
            int cells = first.gridCells();
            int baseSpacing = first.cellSpacingBlocks();

            for (int level = 0; level < tiles.size(); level++) {
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
                 ByteBufferBuilder farSolidBytes = new ByteBufferBuilder(INITIAL_BUFFER_BYTES);
                 ByteBufferBuilder waterBytes = new ByteBufferBuilder(INITIAL_BUFFER_BYTES)) {
                BufferBuilder solidBuilder = new BufferBuilder(
                        solidBytes, VertexFormat.Mode.TRIANGLES, irisTerrainFormat);
                BufferBuilder farSolidBuilder = new BufferBuilder(
                        farSolidBytes, VertexFormat.Mode.TRIANGLES, irisTerrainFormat);
                BufferBuilder waterBuilder = new BufferBuilder(
                        waterBytes, VertexFormat.Mode.TRIANGLES, irisTerrainFormat);

                for (int level = 0; level < snapshot.size(); level++) {
                    appendTerrain(snapshot.get(level), level,
                            nearChunks, seaLevel, sprites,
                            level == 0 ? solidBuilder : farSolidBuilder, waterBuilder);
                }
                nextSolid = upload(solidBuilder);
                nextFar = upload(farSolidBuilder);
                nextWater = upload(waterBuilder);
            }

            // Publish only a fully uploaded replacement; old buffers survive a failed build.
            resetVisibilityProbe();
            disposeBuffers();
            solidBuffer = nextSolid;
            farSolidBuffer = nextFar;
            waterBuffer = nextWater;
            nextSolid = nextFar = nextWater = null;
            cachedDimension = dimension;
            cachedRequestId = requestId;
            cachedCenterX = centerX;
            cachedCenterZ = centerZ;
            cachedNearChunks = nearChunks;
            cachedGridCells = cells;
            cachedBaseSpacing = baseSpacing;
            cachedSeaLevel = seaLevel;
            cachedTiles = List.copyOf(snapshot);
            cachedProxies = List.of();

            if (DEBUG) {
                System.out.printf(java.util.Locale.ROOT,
                        "[RealScale Iris Clipmap] nativeMeshesBuilt=%.1fms levels=%d proxies=%d solid=%s cutout=%s water=%s heightRanges=%s center=(%d,%d)%n",
                        (System.nanoTime() - started) / 1_000_000.0,
                        snapshot.size(), 0, solidBuffer != null,
                        cutoutBuffer != null, waterBuffer != null,
                        heightRangeSummary(snapshot), centerX, centerZ);
            }
            return solidBuffer != null || farSolidBuffer != null || cutoutBuffer != null || waterBuffer != null;
        } catch (RuntimeException failure) {
            close(nextSolid);
            close(nextFar);
            close(nextWater);
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

    private static void rebuildProxies(List<VisibleSurfaceProxy> proxies, long centerX, long centerZ) {
        long started = System.nanoTime();
        VertexBuffer nextSolid = null, nextCutout = null;
        try {
            if (!proxies.isEmpty()) {
                VertexFormat irisTerrainFormat = IrisRenderCompatibility.terrainVertexFormat();
                if (irisTerrainFormat == null) return;
                Sprites sprites = Sprites.load(Minecraft.getInstance());
                try (ByteBufferBuilder solidBytes = new ByteBufferBuilder(INITIAL_BUFFER_BYTES);
                     ByteBufferBuilder cutoutBytes = new ByteBufferBuilder(INITIAL_BUFFER_BYTES)) {
                    BufferBuilder solid = new BufferBuilder(solidBytes, VertexFormat.Mode.TRIANGLES, irisTerrainFormat);
                    BufferBuilder cutout = new BufferBuilder(cutoutBytes, VertexFormat.Mode.TRIANGLES, irisTerrainFormat);
                    for (VisibleSurfaceProxy proxy : proxies) {
                        appendProxy(proxy, centerX, centerZ, sprites, solid, cutout);
                    }
                    nextSolid = upload(solid);
                    nextCutout = upload(cutout);
                }
            }
            close(proxySolidBuffer);
            close(cutoutBuffer);
            proxySolidBuffer = nextSolid;
            cutoutBuffer = nextCutout;
            cachedProxies = proxies;
            if (DEBUG) System.out.printf(java.util.Locale.ROOT,
                    "[RealScale Iris Clipmap] nativeProxyMeshesBuilt=%.1fms proxies=%d center=(%d,%d)%n",
                    (System.nanoTime() - started) / 1_000_000.0, proxies.size(), centerX, centerZ);
        } catch (RuntimeException failure) {
            close(nextSolid);
            close(nextCutout);
            System.err.println("[RealScale Iris Clipmap] proxy mesh build failed safely: " + failure.getMessage());
        }
    }

    private static void appendTerrain(ClipmapHeightTilePayload tile, int level,
            int nearChunks, int seaLevel, Sprites sprites,
            BufferBuilder solid, BufferBuilder water) {
        int cells = tile.gridCells();
        int side = cells + 1;
        int baseCount = side * side;
        int spacing = tile.cellSpacingBlocks();
        int baseSpacing = spacing >> level;
        if (baseSpacing <= 0) {
            throw new IllegalStateException("clipmap LOD has an invalid base spacing");
        }
        int holeRadius = level == 0
                ? Math.toIntExact((nearChunks * 16L
                        - ((long) tile.cellSpacingBlocks() << (LEVEL_COUNT - 1)) / 2L)
                        / spacing)
                : cells / 4;
        int[] indices = ClipmapRingMesh.surfaceIndices(cells, holeRadius);
        short[] encodedHeights = tile.heights();
        float[] surfaceHeights = new float[baseCount];
        for (int gridZ = 0; gridZ <= cells; gridZ++) {
            for (int gridX = 0; gridX <= cells; gridX++) {
                int gridIndex = gridZ * side + gridX;
                surfaceHeights[gridIndex] = isInsideHole(
                        gridX, gridZ, cells, holeRadius)
                        ? Float.NaN
                        : morphedHeight(encodedHeights, cells,
                                tile.minHeight(), tile.maxHeight(),
                                gridX, gridZ, level);
            }
        }
        // The native Iris path must shade the actual surface smoothly. A
        // two-sample footprint suppresses cell-scale ridges without changing
        // the sampled terrain heights or the distant silhouette.
        float[] smoothNormals = ClipmapSurfaceNormals.compute(
                surfaceHeights, cells, spacing, holeRadius, 2);

        int[] gridX = new int[3];
        int[] gridZ = new int[3];
        float[] topHeight = new float[3];
        float[] x = new float[3];
        float[] y = new float[3];
        float[] z = new float[3];
        float[] faceNormalScratch = new float[3];
        float[] triangleUvs = new float[6];
        int[] vertexColors = new int[3];
        for (int p = 0; p < indices.length; p += 3) {
            float minimumHeight = Float.POSITIVE_INFINITY;
            float maximumHeight = Float.NEGATIVE_INFINITY;

            for (int vertex = 0; vertex < 3; vertex++) {
                int index = indices[p + vertex];
                int gridIndex = index;
                gridX[vertex] = gridIndex % side;
                gridZ[vertex] = gridIndex / side;
                int heightIndex = gridZ[vertex] * side + gridX[vertex];
                topHeight[vertex] = surfaceHeights[heightIndex];
                minimumHeight = Math.min(minimumHeight, topHeight[vertex]);
                maximumHeight = Math.max(maximumHeight, topHeight[vertex]);
                x[vertex] = (gridX[vertex] - cells * 0.5f) * spacing;
                z[vertex] = (gridZ[vertex] - cells * 0.5f) * spacing;
            }

            // Shoreline triangles may straddle sea level. Draw a water plane
            // over the submerged portion, and preserve the actual terrain
            // heights so the depth buffer clips water against dry land.
            if (minimumHeight < seaLevel) {
                for (int vertex = 0; vertex < 3; vertex++) {
                    y[vertex] = seaLevel + 0.025f;
                }
                // Water is deliberately flat-shaded and uses one stable color.
                for (int vertex = 0; vertex < 3; vertex++) {
                    vertexColors[vertex] = ClipmapSurfacePalette.waterColor();
                }
                ClipmapTriangleUv.computeCellAlignedInto(gridX, gridZ, triangleUvs);
                emitTriangle(water, sprites.water(), vertexColors, 190,
                        x, y, z, gridX, gridZ, side, triangleUvs, smoothNormals,
                        false, true, faceNormalScratch);
            }
            if (maximumHeight >= seaLevel) {
                for (int vertex = 0; vertex < 3; vertex++) {
                    y[vertex] = topHeight[vertex];
                }
                // Blend material classes per vertex rather than assigning one
                // hard material to the whole triangle. This removes visible
                // color facets at LOD scale while preserving broad landforms.
                for (int vertex = 0; vertex < 3; vertex++) {
                    int normalIndex = (gridZ[vertex] * side + gridX[vertex]) * 3;
                    long worldX = tile.originX() + (long) gridX[vertex] * spacing;
                    long worldZ = tile.originZ() + (long) gridZ[vertex] * spacing;
                    vertexColors[vertex] = ClipmapSurfacePalette.texturedTintAt(
                            Math.max(topHeight[vertex], seaLevel),
                            smoothNormals[normalIndex + 1], seaLevel, false, worldX, worldZ);
                }
                ClipmapTriangleUv.computeCellAlignedInto(gridX, gridZ, triangleUvs);
                float averageHeight = (topHeight[0] + topHeight[1] + topHeight[2]) / 3.0f;
                float averageNormalY = 0.0f;
                for (int vertex = 0; vertex < 3; vertex++) {
                    int normalIndex = (gridZ[vertex] * side + gridX[vertex]) * 3;
                    averageNormalY += smoothNormals[normalIndex + 1] / 3.0f;
                }
                // Keep authored block textures where they can resolve on screen.
                // At coarse LODs, one 16x16 tile per 32-128 block cell aliases
                // into visible speckle. Use a neutral atlas texel there and let
                // the world-anchored macro palette carry broad biome variation.
                TextureAtlasSprite materialSprite = level > MAX_TEXTURED_DETAIL_LEVEL
                        ? sprites.neutral()
                        : sprites.forMaterial(ClipmapSurfacePalette.materialAt(
                                averageHeight, averageNormalY, seaLevel));
                emitTriangle(solid, materialSprite, vertexColors, 255,
                        x, y, z, gridX, gridZ, side, triangleUvs, smoothNormals,
                        true, false, faceNormalScratch);
            }
        }
    }

    private static boolean isInsideHole(int x, int z, int cells, int halfHole) {
        int center = cells / 2;
        return halfHole > 0
                && Math.abs(x - center) < halfHole
                && Math.abs(z - center) < halfHole;
    }

    private static float morphedHeight(short[] samples, int cells,
            int minHeight, int maxHeight, int x, int z, int level) {
        float height = sampleHeight(samples, cells, minHeight, maxHeight, x, z);
        if (level >= LEVEL_COUNT - 1) return height;

        int edgeCells = Math.min(Math.min(x, z),
                Math.min(cells - x, cells - z));
        if (edgeCells >= MORPH_BAND_CELLS) return height;

        // The parent ring deliberately leaves its interior unsampled. Build
        // the 2:1 parent surface from this tile's valid even-grid samples,
        // which are aligned with the adjacent coarser ring at the outer edge.
        int parentX = (x / 2) * 2;
        int parentZ = (z / 2) * 2;
        float h00 = sampleHeight(samples, cells, minHeight, maxHeight, parentX, parentZ);
        float h10 = sampleHeight(samples, cells, minHeight, maxHeight, parentX + 2, parentZ);
        float h01 = sampleHeight(samples, cells, minHeight, maxHeight, parentX, parentZ + 2);
        float h11 = sampleHeight(samples, cells, minHeight, maxHeight, parentX + 2, parentZ + 2);
        float parent = (float) ClipmapMorphMath.parentHeight(x, z, h00, h10, h01, h11);
        float morph = 1.0f - smoothstep(0.0f, MORPH_BAND_CELLS, edgeCells);
        return lerp(height, parent, morph);
    }

    private static float sampleHeight(short[] samples, int cells,
            int minHeight, int maxHeight, int x, int z) {
        int side = cells + 1;
        // IrisNativeClipmapRenderer stores a bounded row-major array, not the
        // toroidal GL texture used by the fallback renderer. Parent samples at
        // the outermost morph edge must clamp, never wrap to the opposite edge.
        int clampedX = Math.max(0, Math.min(cells, x));
        int clampedZ = Math.max(0, Math.min(cells, z));
        float normalized = (samples[clampedZ * side + clampedX] & 0xFFFF) / 65535.0f;
        return minHeight + normalized * (maxHeight - minHeight);
    }

    private static void emitTriangle(BufferBuilder builder, TextureAtlasSprite sprite,
            int[] rgb, int alpha, float[] x, float[] y, float[] z,
            int[] gridX, int[] gridZ, int side, float[] triangleUvs,
            float[] smoothNormals, boolean useSmoothNormals,
            boolean flatUp, float[] faceNormalScratch) {
        if (!useSmoothNormals && !flatUp) {
            triangleNormalInto(x, y, z, faceNormalScratch);
        }
        for (int vertex = 0; vertex < 3; vertex++) {
            float nx;
            float ny;
            float nz;
            if (flatUp) {
                nx = 0.0f;
                ny = 1.0f;
                nz = 0.0f;
            } else if (useSmoothNormals) {
                int normalIndex = (gridZ[vertex] * side + gridX[vertex]) * 3;
                nx = smoothNormals[normalIndex];
                ny = smoothNormals[normalIndex + 1];
                nz = smoothNormals[normalIndex + 2];
            } else {
                nx = faceNormalScratch[0];
                ny = faceNormalScratch[1];
                nz = faceNormalScratch[2];
            }
            // The uniform white-concrete sprite uses a small inset UV patch.
            // Coordinates vary across the triangle so Iris can build a valid
            // tangent frame for PBR shaders without reintroducing texture grain.
            putVertex(builder, sprite, rgb[vertex], alpha,
                    x[vertex], y[vertex], z[vertex],
                    triangleUvs[vertex * 2], triangleUvs[vertex * 2 + 1], nx, ny, nz);
        }
        // TRIANGLES keeps Iris's per-vertex normals intact. Its QUADS
        // extension path recalculates one face normal and would erase the
        // smooth clipmap normals supplied above.
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
    }

    private static void emitDoubleSidedQuad(BufferBuilder builder,
            TextureAtlasSprite sprite, int rgb, float[][] p) {
        emitQuad(builder, sprite, rgb, p[0], p[1], p[2], p[3]);
        emitQuad(builder, sprite, rgb, p[3], p[2], p[1], p[0]);
    }

    private static void emitQuad(BufferBuilder builder, TextureAtlasSprite sprite,
            int rgb, float[] a, float[] b, float[] c, float[] d) {
        float[] normal = quadNormal(a, b, c);
        // Split proxy quads into two real triangles so every buffer uses
        // triangle topology and Iris preserves per-vertex normals.
        putVertex(builder, sprite, rgb, 255, a[0], a[1], a[2], 0.0f, 0.0f, normal);
        putVertex(builder, sprite, rgb, 255, b[0], b[1], b[2], 16.0f, 0.0f, normal);
        putVertex(builder, sprite, rgb, 255, c[0], c[1], c[2], 16.0f, 16.0f, normal);
        putVertex(builder, sprite, rgb, 255, a[0], a[1], a[2], 0.0f, 0.0f, normal);
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
        putVertex(builder, sprite, rgb, alpha, x, y, z, u, v,
                normal[0], normal[1], normal[2]);
    }

    private static void putVertex(BufferBuilder builder, TextureAtlasSprite sprite,
            int rgb, int alpha, float x, float y, float z,
            float u, float v, float nx, float ny, float nz) {
        builder.addVertex(x, y, z)
                .setColor((rgb >>> 16) & 0xFF, (rgb >>> 8) & 0xFF,
                        rgb & 0xFF, alpha)
                .setUv(sprite.getU(u), sprite.getV(v))
                .setLight(SURFACE_SKY_LIGHT)
                .setNormal(nx, ny, nz);
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
        close(farSolidBuffer);
        close(cutoutBuffer);
        close(proxySolidBuffer);
        close(waterBuffer);
        solidBuffer = null;
        farSolidBuffer = null;
        cutoutBuffer = null;
        proxySolidBuffer = null;
        waterBuffer = null;
    }

    private static void close(VertexBuffer buffer) {
        if (buffer != null) buffer.close();
    }

    private record Sprites(TextureAtlasSprite grass, TextureAtlasSprite stone,
            TextureAtlasSprite sand, TextureAtlasSprite snow,
            TextureAtlasSprite water, TextureAtlasSprite leaves,
            TextureAtlasSprite wood, TextureAtlasSprite bricks,
            TextureAtlasSprite black, TextureAtlasSprite neutral,
            TextureAtlasSprite grassTop, TextureAtlasSprite stoneTop,
            TextureAtlasSprite sandTop, TextureAtlasSprite snowTop) {
        private static Sprites load(Minecraft minecraft) {
            BlockModelShaper shaper = minecraft.getBlockRenderer().getBlockModelShaper();
            BlockState grassState = Blocks.GRASS_BLOCK.defaultBlockState();
            BlockState stoneState = Blocks.STONE.defaultBlockState();
            BlockState sandState = Blocks.SAND.defaultBlockState();
            BlockState snowState = Blocks.SNOW_BLOCK.defaultBlockState();
            return new Sprites(
                    particle(shaper, grassState),
                    particle(shaper, stoneState),
                    particle(shaper, sandState),
                    particle(shaper, snowState),
                    particle(shaper, Blocks.WATER.defaultBlockState()),
                    particle(shaper, Blocks.OAK_LEAVES.defaultBlockState()),
                    particle(shaper, Blocks.OAK_LOG.defaultBlockState()),
                    particle(shaper, Blocks.STONE_BRICKS.defaultBlockState()),
                    particle(shaper, Blocks.BLACK_CONCRETE.defaultBlockState()),
                    particle(shaper, Blocks.WHITE_CONCRETE.defaultBlockState()),
                    topFace(shaper, grassState),
                    topFace(shaper, stoneState),
                    topFace(shaper, sandState),
                    topFace(shaper, snowState));
        }

        private TextureAtlasSprite forMaterial(int material) {
            return switch (material) {
                case ClipmapSurfacePalette.MATERIAL_ROCK -> stoneTop;
                case ClipmapSurfacePalette.MATERIAL_SAND -> sandTop;
                case ClipmapSurfacePalette.MATERIAL_SNOW -> snowTop;
                default -> grassTop;
            };
        }

        private static TextureAtlasSprite topFace(BlockModelShaper shaper, BlockState state) {
            try {
                List<BakedQuad> quads = shaper.getBlockModel(state)
                        .getQuads(state, Direction.UP, RandomSource.create(0L));
                for (BakedQuad quad : quads) {
                    if (quad.getDirection() == Direction.UP) return quad.getSprite();
                }
            } catch (RuntimeException ignored) {
                // A resource pack may supply a custom model without a top quad;
                // the stable particle sprite is a safe visual fallback.
            }
            return particle(shaper, state);
        }

        private static TextureAtlasSprite particle(BlockModelShaper shaper, BlockState state) {
            return shaper.getParticleIcon(state);
        }
    }
}
