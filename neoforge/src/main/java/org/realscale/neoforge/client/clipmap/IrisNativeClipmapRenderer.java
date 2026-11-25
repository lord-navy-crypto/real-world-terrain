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
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.realscale.neoforge.network.ClipmapHeightTilePayload;

import java.util.ArrayList;
import java.util.Arrays;
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
    // Runtime markers: FAR_SOLID_VISIBLE_SAMPLES= and FAR_WATER_VISIBLE_SAMPLES=.
    private static final VisibilityProbe SOLID_VISIBILITY_PROBE =
            new VisibilityProbe("FAR_SOLID", false);
    // Ocean and lake surfaces are valid far-field output too. Keep their
    // diagnostic query smoke-only so normal gameplay does not add extra GPU queries.
    private static final VisibilityProbe WATER_VISIBILITY_PROBE =
            new VisibilityProbe("FAR_WATER", true);

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
                    drawWithVisibilityProbe(farSolidBuffer, SOLID_VISIBILITY_PROBE,
                            shadowPass, cameraX, cameraY, cameraZ);
                }
            } else {
                target.bind();
                if (renderType == RenderType.translucent()) {
                    drawWithVisibilityProbe(target, WATER_VISIBILITY_PROBE,
                            shadowPass, cameraX, cameraY, cameraZ);
                } else {
                    target.draw();
                }
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
            VisibilityProbe probe, boolean shadowPass,
            double cameraX, double cameraY, double cameraZ) {
        probe.draw(target, shadowPass, cameraX, cameraY, cameraZ);
    }

    private static void resetVisibilityProbe() {
        SOLID_VISIBILITY_PROBE.reset();
        WATER_VISIBILITY_PROBE.reset();
    }

    private static final class VisibilityProbe {
        private final String label;
        private final boolean smokeOnly;
        private int pendingQuery;
        private int attempts;
        private long lastProbeNanos;
        private double cameraX;
        private double cameraY;
        private double cameraZ;
        private int program;
        private int framebuffer;
        private int levels;
        private long centerX;
        private long centerZ;
        private boolean passed;

        private VisibilityProbe(String label, boolean smokeOnly) {
            this.label = label;
            this.smokeOnly = smokeOnly;
        }

        private void draw(VertexBuffer target, boolean shadowPass,
                double cameraX, double cameraY, double cameraZ) {
            if (shadowPass || (smokeOnly && !SMOKE_VISIBILITY_PROBE)
                    || (!DEBUG && !SMOKE_VISIBILITY_PROBE)) {
                target.draw();
                return;
            }

            // Each render layer owns its query. Solid terrain can be absent
            // over an ocean while the translucent far-water surface is valid.
            if (pendingQuery != 0
                    && GL15.glGetQueryObjecti(pendingQuery,
                            GL15.GL_QUERY_RESULT_AVAILABLE) != 0) {
                int samples = GL15.glGetQueryObjecti(pendingQuery,
                        GL15.GL_QUERY_RESULT);
                GL15.glDeleteQueries(pendingQuery);
                pendingQuery = 0;
                passed = samples > 0;
                System.out.printf(java.util.Locale.ROOT,
                        "[RealScale Iris Clipmap] %s_VISIBLE_SAMPLES=%d "
                                + "cameraY=%.1f cameraX=%.1f cameraZ=%.1f program=%d "
                                + "drawFramebuffer=%d attempt=%d levels=%d center=(%d,%d)%n",
                        label, samples, this.cameraY, this.cameraX, this.cameraZ,
                        program, framebuffer, attempts, levels, centerX, centerZ);
            }

            long now = System.nanoTime();
            if (pendingQuery != 0 || passed
                    || (SMOKE_VISIBILITY_PROBE && cameraY < 2300.0)
                    || (!SMOKE_VISIBILITY_PROBE && attempts >= 4)
                    || (lastProbeNanos != 0L && now - lastProbeNanos < 2_000_000_000L)
                    || GL15.glGetQueryi(GL15.GL_SAMPLES_PASSED,
                            GL15.GL_CURRENT_QUERY) != 0) {
                target.draw();
                return;
            }

            lastProbeNanos = now;
            attempts++;
            this.cameraX = cameraX;
            this.cameraY = cameraY;
            this.cameraZ = cameraZ;
            program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            framebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            levels = cachedTiles.size();
            centerX = cachedCenterX;
            centerZ = cachedCenterZ;
            int query = GL15.glGenQueries();
            try {
                GL15.glBeginQuery(GL15.GL_SAMPLES_PASSED, query);
                try {
                    target.draw();
                } finally {
                    GL15.glEndQuery(GL15.GL_SAMPLES_PASSED);
                }
                pendingQuery = query;
                query = 0;
            } finally {
                if (query != 0) GL15.glDeleteQueries(query);
            }
        }

        private void reset() {
            if (pendingQuery != 0) {
                GL15.glDeleteQueries(pendingQuery);
                pendingQuery = 0;
            }
            attempts = 0;
            lastProbeNanos = 0L;
            passed = false;
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
        // Smooth normals are the far-field baseline. The first ring blends
        // these normals toward flat voxel tops as its stepped geometry fades in.
        float[] smoothNormals = ClipmapSurfaceNormals.compute(
                surfaceHeights, cells, spacing, holeRadius, 2);

        // Select the first ring that extends beyond the protected vanilla
        // overlap. This keeps the voxel transition active even in shorter
        // range presets where LOD 0 itself is fully hidden by vanilla chunks.
        int voxelLevel = ClipmapVoxelStyle.transitionLevel(
                cells, baseSpacing, nearChunks);
        float[] voxelHeights = level == voxelLevel ? new float[cells * cells] : null;
        float[] voxelBlends = level == voxelLevel ? new float[cells * cells] : null;
        int[] voxelColors = level == voxelLevel ? new int[cells * cells] : null;
        if (level == voxelLevel) {
            Arrays.fill(voxelHeights, Float.NaN);
            long clipmapCenterX = tile.originX() + (long) cells * spacing / 2L;
            long clipmapCenterZ = tile.originZ() + (long) cells * spacing / 2L;
            for (int cellZ = 0; cellZ < cells; cellZ++) {
                for (int cellX = 0; cellX < cells; cellX++) {
                    int sample = cellZ * side + cellX;
                    float h00 = surfaceHeights[sample];
                    float h10 = surfaceHeights[sample + 1];
                    float h01 = surfaceHeights[sample + side];
                    float h11 = surfaceHeights[sample + side + 1];
                    if (!Float.isFinite(h00) || !Float.isFinite(h10)
                            || !Float.isFinite(h01) || !Float.isFinite(h11)) {
                        continue;
                    }
                    int cell = cellZ * cells + cellX;
                    float averageHeight = (h00 + h10 + h01 + h11) * 0.25f;
                    long worldX = tile.originX() + (long) cellX * spacing + spacing / 2L;
                    long worldZ = tile.originZ() + (long) cellZ * spacing + spacing / 2L;
                    double distance = Math.hypot(worldX - clipmapCenterX,
                            worldZ - clipmapCenterZ);
                    float blend = ClipmapVoxelStyle.blend(
                            level, distance, nearChunks, baseSpacing, spacing, cells);
                    float averageNormalY = (
                            smoothNormals[sample * 3 + 1]
                            + smoothNormals[(sample + 1) * 3 + 1]
                            + smoothNormals[(sample + side) * 3 + 1]
                            + smoothNormals[(sample + side + 1) * 3 + 1]) * 0.25f;
                    voxelHeights[cell] = ClipmapVoxelStyle.quantizeHeight(averageHeight);
                    voxelBlends[cell] = blend;
                    if (blend > 0.001f) {
                        voxelColors[cell] = ClipmapSurfacePalette.terrainColorAt(
                                averageHeight, averageNormalY, seaLevel, false, worldX, worldZ);
                    }
                }
            }
        }

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

            int cellX = Math.min(gridX[0], Math.min(gridX[1], gridX[2]));
            int cellZ = Math.min(gridZ[0], Math.min(gridZ[1], gridZ[2]));
            int cellIndex = cellZ * cells + cellX;
            float voxelBlend = level == voxelLevel ? voxelBlends[cellIndex] : 0.0f;
            float voxelHeight = level == voxelLevel ? voxelHeights[cellIndex]
                    : (topHeight[0] + topHeight[1] + topHeight[2]) / 3.0f;
            if (level == voxelLevel && !Float.isFinite(voxelHeight)) {
                voxelHeight = (topHeight[0] + topHeight[1] + topHeight[2]) / 3.0f;
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
                // Far water must not repeat the high-frequency block-water atlas.
                // Keep a non-degenerate per-cell UV basis for Iris/PBR tangent
                // reconstruction, but sample only the flat neutral white sprite.
                // The stable water material remains in vertex color and alpha.
                ClipmapTriangleUv.computeCellAlignedInto(gridX, gridZ, triangleUvs);
                emitTriangle(water, sprites.neutral(), vertexColors, 190,
                        x, y, z, gridX, gridZ, side, triangleUvs, smoothNormals,
                        false, 0.0f, true, faceNormalScratch);
            }
            if (maximumHeight >= seaLevel) {
                for (int vertex = 0; vertex < 3; vertex++) {
                    y[vertex] = ClipmapVoxelStyle.blendHeight(
                            topHeight[vertex], voxelHeight, voxelBlend);
                }
                // Continuous palette colors fade toward one block-like cell
                // color together with the geometry and surface normals.
                for (int vertex = 0; vertex < 3; vertex++) {
                    int normalIndex = (gridZ[vertex] * side + gridX[vertex]) * 3;
                    long worldX = tile.originX() + (long) gridX[vertex] * spacing;
                    long worldZ = tile.originZ() + (long) gridZ[vertex] * spacing;
                    int continuousColor = ClipmapSurfacePalette.terrainColorAt(
                            Math.max(y[vertex], seaLevel),
                            smoothNormals[normalIndex + 1], seaLevel, false, worldX, worldZ);
                    int voxelColor = voxelBlend > 0.001f
                            ? voxelColors[cellIndex] : continuousColor;
                    vertexColors[vertex] = ClipmapVoxelStyle.blendColor(
                            continuousColor, voxelColor, voxelBlend);
                }
                // Never paint terrain surfaces with block/item-atlas artwork.
                // A neutral white sprite prevents texture aliasing, while a
                // per-cell UV basis keeps Iris/PBR tangent reconstruction valid.
                // The actual material comes from the procedural vertex palette.
                ClipmapTriangleUv.computeCellAlignedInto(gridX, gridZ, triangleUvs);
                emitTriangle(solid, sprites.neutral(), vertexColors, 255,
                        x, y, z, gridX, gridZ, side, triangleUvs, smoothNormals,
                        true, voxelBlend, false, faceNormalScratch);
            }
        }

        // Add vertical risers between neighbouring quantized cells. The wall
        // height shrinks with the same blend as the top surface, disappearing
        // before the first fully continuous coarse LOD.
        appendVoxelWalls(tile, level, voxelLevel, seaLevel, sprites.neutral(),
                surfaceHeights, voxelHeights, voxelBlends, solid);
    }

    private static void appendVoxelWalls(ClipmapHeightTilePayload tile, int level,
            int voxelLevel, int seaLevel, TextureAtlasSprite neutral,
            float[] surfaceHeights, float[] voxelHeights, float[] voxelBlends,
            BufferBuilder solid) {
        if (level != voxelLevel || voxelHeights == null || voxelBlends == null) return;
        int cells = tile.gridCells();
        int side = cells + 1;
        int spacing = tile.cellSpacingBlocks();
        for (int cellZ = 0; cellZ < cells; cellZ++) {
            for (int cellX = 0; cellX < cells; cellX++) {
                int cell = cellZ * cells + cellX;
                if (!Float.isFinite(voxelHeights[cell])) continue;
                if (cellX + 1 < cells) {
                    appendVoxelWallX(tile, seaLevel, neutral, surfaceHeights,
                            voxelHeights, voxelBlends, cellX, cellZ, side, spacing, solid);
                }
                if (cellZ + 1 < cells) {
                    appendVoxelWallZ(tile, seaLevel, neutral, surfaceHeights,
                            voxelHeights, voxelBlends, cellX, cellZ, side, spacing, solid);
                }
            }
        }
    }

    private static void appendVoxelWallX(ClipmapHeightTilePayload tile, int seaLevel,
            TextureAtlasSprite neutral, float[] surfaceHeights,
            float[] voxelHeights, float[] voxelBlends,
            int cellX, int cellZ, int side, int spacing, BufferBuilder solid) {
        int left = cellZ * tile.gridCells() + cellX;
        int right = left + 1;
        if (!Float.isFinite(voxelHeights[right])
                || Math.max(voxelBlends[left], voxelBlends[right]) <= 0.001f) return;

        int edgeX = cellX + 1;
        int sample0 = cellZ * side + edgeX;
        int sample1 = (cellZ + 1) * side + edgeX;
        float raw0 = surfaceHeights[sample0];
        float raw1 = surfaceHeights[sample1];
        if (!Float.isFinite(raw0) || !Float.isFinite(raw1)) return;

        float a0 = ClipmapVoxelStyle.blendHeight(
                raw0, voxelHeights[left], voxelBlends[left]);
        float a1 = ClipmapVoxelStyle.blendHeight(
                raw1, voxelHeights[left], voxelBlends[left]);
        float b0 = ClipmapVoxelStyle.blendHeight(
                raw0, voxelHeights[right], voxelBlends[right]);
        float b1 = ClipmapVoxelStyle.blendHeight(
                raw1, voxelHeights[right], voxelBlends[right]);
        float difference0 = a0 - b0;
        float difference1 = a1 - b1;
        if (Math.max(Math.abs(difference0), Math.abs(difference1)) < 0.5f) return;

        float upper0 = Math.max(a0, b0);
        float upper1 = Math.max(a1, b1);
        float lower0 = Math.min(a0, b0);
        float lower1 = Math.min(a1, b1);
        boolean positiveNormal = difference0 + difference1 >= 0.0f;
        float x = (edgeX - tile.gridCells() * 0.5f) * spacing;
        float z0 = (cellZ - tile.gridCells() * 0.5f) * spacing;
        float z1 = z0 + spacing;
        long worldX = tile.originX() + (long) edgeX * spacing;
        long worldZ = tile.originZ() + (long) cellZ * spacing + spacing / 2L;
        int color = ClipmapSurfacePalette.terrainColorAt(
                (upper0 + upper1 + lower0 + lower1) * 0.25f,
                0.0f, seaLevel, true, worldX, worldZ);
        emitVoxelWallX(solid, neutral, color, x, z0, z1,
                upper0, upper1, lower0, lower1, positiveNormal);
    }

    private static void appendVoxelWallZ(ClipmapHeightTilePayload tile, int seaLevel,
            TextureAtlasSprite neutral, float[] surfaceHeights,
            float[] voxelHeights, float[] voxelBlends,
            int cellX, int cellZ, int side, int spacing, BufferBuilder solid) {
        int north = cellZ * tile.gridCells() + cellX;
        int south = north + tile.gridCells();
        if (!Float.isFinite(voxelHeights[south])
                || Math.max(voxelBlends[north], voxelBlends[south]) <= 0.001f) return;

        int edgeZ = cellZ + 1;
        int sample0 = edgeZ * side + cellX;
        int sample1 = edgeZ * side + cellX + 1;
        float raw0 = surfaceHeights[sample0];
        float raw1 = surfaceHeights[sample1];
        if (!Float.isFinite(raw0) || !Float.isFinite(raw1)) return;

        float a0 = ClipmapVoxelStyle.blendHeight(
                raw0, voxelHeights[north], voxelBlends[north]);
        float a1 = ClipmapVoxelStyle.blendHeight(
                raw1, voxelHeights[north], voxelBlends[north]);
        float b0 = ClipmapVoxelStyle.blendHeight(
                raw0, voxelHeights[south], voxelBlends[south]);
        float b1 = ClipmapVoxelStyle.blendHeight(
                raw1, voxelHeights[south], voxelBlends[south]);
        float difference0 = a0 - b0;
        float difference1 = a1 - b1;
        if (Math.max(Math.abs(difference0), Math.abs(difference1)) < 0.5f) return;

        float upper0 = Math.max(a0, b0);
        float upper1 = Math.max(a1, b1);
        float lower0 = Math.min(a0, b0);
        float lower1 = Math.min(a1, b1);
        boolean positiveNormal = difference0 + difference1 >= 0.0f;
        float z = (edgeZ - tile.gridCells() * 0.5f) * spacing;
        float x0 = (cellX - tile.gridCells() * 0.5f) * spacing;
        float x1 = x0 + spacing;
        long worldX = tile.originX() + (long) cellX * spacing + spacing / 2L;
        long worldZ = tile.originZ() + (long) edgeZ * spacing;
        int color = ClipmapSurfacePalette.terrainColorAt(
                (upper0 + upper1 + lower0 + lower1) * 0.25f,
                0.0f, seaLevel, true, worldX, worldZ);
        emitVoxelWallZ(solid, neutral, color, x0, x1, z,
                upper0, upper1, lower0, lower1, positiveNormal);
    }

    private static void emitVoxelWallX(BufferBuilder builder, TextureAtlasSprite sprite,
            int rgb, float x, float z0, float z1,
            float upper0, float upper1, float lower0, float lower1,
            boolean positiveNormal) {
        float nx = positiveNormal ? 1.0f : -1.0f;
        if (positiveNormal) {
            emitWallTriangle(builder, sprite, rgb, x, upper0, z0,
                    x, upper1, z1, x, lower1, z1, nx, 0.0f, 0.0f);
            emitWallTriangle(builder, sprite, rgb, x, upper0, z0,
                    x, lower1, z1, x, lower0, z0, nx, 0.0f, 0.0f);
        } else {
            emitWallTriangle(builder, sprite, rgb, x, upper0, z0,
                    x, lower1, z1, x, upper1, z1, nx, 0.0f, 0.0f);
            emitWallTriangle(builder, sprite, rgb, x, upper0, z0,
                    x, lower0, z0, x, lower1, z1, nx, 0.0f, 0.0f);
        }
    }

    private static void emitVoxelWallZ(BufferBuilder builder, TextureAtlasSprite sprite,
            int rgb, float x0, float x1, float z,
            float upper0, float upper1, float lower0, float lower1,
            boolean positiveNormal) {
        float nz = positiveNormal ? 1.0f : -1.0f;
        if (positiveNormal) {
            emitWallTriangle(builder, sprite, rgb, x0, upper0, z,
                    x1, lower1, z, x1, upper1, z, 0.0f, 0.0f, nz);
            emitWallTriangle(builder, sprite, rgb, x0, upper0, z,
                    x0, lower0, z, x1, lower1, z, 0.0f, 0.0f, nz);
        } else {
            emitWallTriangle(builder, sprite, rgb, x0, upper0, z,
                    x1, upper1, z, x1, lower1, z, 0.0f, 0.0f, nz);
            emitWallTriangle(builder, sprite, rgb, x0, upper0, z,
                    x1, lower1, z, x0, lower0, z, 0.0f, 0.0f, nz);
        }
    }

    private static void emitWallTriangle(BufferBuilder builder, TextureAtlasSprite sprite,
            int rgb, float x0, float y0, float z0,
            float x1, float y1, float z1, float x2, float y2, float z2,
            float nx, float ny, float nz) {
        putVertex(builder, sprite, rgb, 255, x0, y0, z0,
                8.0f, 8.0f, nx, ny, nz);
        putVertex(builder, sprite, rgb, 255, x1, y1, z1,
                8.0f, 8.0f, nx, ny, nz);
        putVertex(builder, sprite, rgb, 255, x2, y2, z2,
                8.0f, 8.0f, nx, ny, nz);
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
            float normalBlend, boolean flatUp, float[] faceNormalScratch) {
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
                float blend = Math.max(0.0f, Math.min(1.0f, normalBlend));
                nx = smoothNormals[normalIndex] * (1.0f - blend);
                ny = smoothNormals[normalIndex + 1] * (1.0f - blend) + blend;
                nz = smoothNormals[normalIndex + 2] * (1.0f - blend);
                float inverseLength = 1.0f / (float) Math.sqrt(
                        nx * nx + ny * ny + nz * nz);
                nx *= inverseLength;
                ny *= inverseLength;
                nz *= inverseLength;
            } else {
                nx = faceNormalScratch[0];
                ny = faceNormalScratch[1];
                nz = faceNormalScratch[2];
            }
            // Terrain samples one neutral atlas texel at every LOD. Its actual
            // material is carried by the vertex palette, avoiding atlas aliasing.
            putVertex(builder, sprite, rgb[vertex], alpha,
                    x[vertex], y[vertex], z[vertex],
                    triangleUvs[vertex * 2], triangleUvs[vertex * 2 + 1], nx, ny, nz);
        }
        // TRIANGLES keeps Iris's per-vertex normals intact. Its QUADS
        // extension path recalculates one face normal and would erase the
        // smooth-to-voxel normal transition supplied above.
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
            TextureAtlasSprite leaves, TextureAtlasSprite wood,
            TextureAtlasSprite bricks, TextureAtlasSprite black,
            TextureAtlasSprite neutral) {
        private static Sprites load(Minecraft minecraft) {
            BlockModelShaper shaper = minecraft.getBlockRenderer().getBlockModelShaper();
            return new Sprites(
                    particle(shaper, Blocks.GRASS_BLOCK.defaultBlockState()),
                    particle(shaper, Blocks.STONE.defaultBlockState()),
                    particle(shaper, Blocks.OAK_LEAVES.defaultBlockState()),
                    particle(shaper, Blocks.OAK_LOG.defaultBlockState()),
                    particle(shaper, Blocks.STONE_BRICKS.defaultBlockState()),
                    particle(shaper, Blocks.BLACK_CONCRETE.defaultBlockState()),
                    particle(shaper, Blocks.WHITE_CONCRETE.defaultBlockState()));
        }

        private static TextureAtlasSprite particle(BlockModelShaper shaper, BlockState state) {
            return shaper.getParticleIcon(state);
        }
    }
}
