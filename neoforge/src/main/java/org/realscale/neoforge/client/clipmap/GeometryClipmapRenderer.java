package org.realscale.neoforge.client.clipmap;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.Map;
import java.util.List;
import java.util.Locale;

/**
 * OpenGL 4.1 Geometry Clipmap with procedural exterior shading.
 *
 * <p>It draws six nested surface rings from compact R16 height textures.
 * The mesh is static; toroidal height textures receive only newly exposed
 * strips when the server sends a snapped window. All GL work is restricted
 * to the render thread.
 */
final class GeometryClipmapRenderer {
    private static final int LEVEL_COUNT = 6;
    private static final boolean DEBUG =
            Boolean.getBoolean("realscale.experimental.geometryclipmap.debug");
    private static long debugWindowStartNanos;
    private static long debugFrameCount;
    private static long debugTotalRenderNanos;
    private static long debugMaxRenderNanos;
    private static long debugUploadedBytes;
    private static long debugProxyCount;
    private static final String VERTEX_SHADER = """
            #version 410 core
            layout(location = 0) in vec3 aGridAndSkirt;
            uniform mat4 uViewProjection;
            uniform sampler2D uHeightMap;
            uniform vec2 uOrigin[6];
            uniform vec2 uNoiseOrigin[6];
            uniform float uCameraY;
            uniform float uSpacing[6];
            uniform float uGridCells;
            uniform float uHeightMin;
            uniform float uHeightMax;
            uniform float uSeaLevel;
            uniform float uSkirtDepth[6];
            uniform float uMorphBandCells[6];
            uniform float uInnerHalfCells[6];
            uniform vec2 uTextureOffset[6];
            uniform int uLevel;
            out float vHeight;
            out float vOcean;
            out float vDistance;
            out vec3 vPosition;
            out vec2 vNoisePosition;
            out vec3 vSmoothNormal;
            out float vNormalBlend;

            float surfaceHeightAt(vec2 sampleUv) {
                float encoded = texture(uHeightMap, sampleUv).r;
                return max(mix(uHeightMin, uHeightMax, encoded), uSeaLevel);
            }

            void main() {
                vec2 grid = aGridAndSkirt.xy;
                int levelIndex = clamp(uLevel, 0, 5);
                float levelSpacing = uSpacing[levelIndex];
                float levelSkirtDepth = uSkirtDepth[levelIndex];
                float levelMorphBand = uMorphBandCells[levelIndex];
                vec2 levelOrigin = uOrigin[levelIndex];
                vec2 levelNoiseOrigin = uNoiseOrigin[levelIndex];
                vec2 levelTextureOffset = uTextureOffset[levelIndex];
                float side = uGridCells + 1.0;
                vec2 textureGrid = mod(grid + levelTextureOffset, side);
                vec2 uv = (textureGrid + vec2(0.5)) / side;
                float h = mix(uHeightMin, uHeightMax, texture(uHeightMap, uv).r);
                float edgeCells = min(min(grid.x, grid.y),
                    min(uGridCells - grid.x, uGridCells - grid.y));
                float holeEdgeCells = max(abs(grid.x - uGridCells * 0.5),
                    abs(grid.y - uGridCells * 0.5)) - uInnerHalfCells[levelIndex];
                // Use neighboring height texels for smooth normals. The inner
                // hole and outer perimeter are excluded because wrapped
                // neighbors at those boundaries are not reliable.
                vec2 texel = vec2(1.0 / side);
                float hLeft = surfaceHeightAt(uv - vec2(texel.x, 0.0));
                float hRight = surfaceHeightAt(uv + vec2(texel.x, 0.0));
                float hDown = surfaceHeightAt(uv - vec2(0.0, texel.y));
                float hUp = surfaceHeightAt(uv + vec2(0.0, texel.y));
                vSmoothNormal = normalize(vec3(
                    hLeft - hRight, 2.0 * levelSpacing, hDown - hUp));
                float normalSafeCells = min(holeEdgeCells,
                    edgeCells - levelMorphBand);
                vNormalBlend = smoothstep(0.0, 2.0, normalSafeCells);

                // A fine outer boundary must converge on the 2x parent grid.
                // These parent heights are sampled at even vertices of this
                // SAME R16 texture; no additional worldgen or network data.
                if (levelMorphBand > 0.0 && edgeCells < levelMorphBand) {
                    vec2 parentGrid = floor(grid * 0.5) * 2.0;
                    vec2 parentTextureGrid = mod(parentGrid + levelTextureOffset, side);
                    vec2 parentUV = (parentTextureGrid + vec2(0.5)) / side;
                    float pixelStep = 2.0 / side;
                    vec2 blend = (grid - parentGrid) * 0.5;
                    float h00 = texture(uHeightMap, parentUV).r;
                    float h10 = texture(uHeightMap,
                        parentUV + vec2(pixelStep, 0.0)).r;
                    float h01 = texture(uHeightMap,
                        parentUV + vec2(0.0, pixelStep)).r;
                    float h11 = texture(uHeightMap,
                        parentUV + vec2(pixelStep, pixelStep)).r;
                    float parentHeight = mix(mix(h00, h10, blend.x),
                        mix(h01, h11, blend.x), blend.y);
                    parentHeight = mix(uHeightMin, uHeightMax, parentHeight);
                    float morph = 1.0 - smoothstep(0.0,
                        levelMorphBand, edgeCells);
                    h = mix(h, parentHeight, morph);
                }
                float ocean = step(h, uSeaLevel);
                float surfaceY = mix(h, uSeaLevel, ocean)
                    - aGridAndSkirt.z * levelSkirtDepth;

                vec2 worldXZ = levelOrigin + grid * levelSpacing;
                vec3 relative = vec3(worldXZ.x, surfaceY - uCameraY, worldXZ.y);
                vHeight = h;
                vOcean = ocean;
                vPosition = relative;
                vDistance = length(relative.xz);
                // World-aligned coordinates are filtered in the fragment
                // stage using screen-space derivatives, preventing coarse
                // outer LOD vertices from aliasing into visible color speckle.
                vNoisePosition = levelNoiseOrigin + grid * levelSpacing;
                gl_Position = uViewProjection * vec4(relative, 1.0);
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #version 410 core
            in float vHeight;
            in float vOcean;
            in float vDistance;
            in vec3 vPosition;
            in vec2 vNoisePosition;
            in vec3 vSmoothNormal;
            in float vNormalBlend;
            uniform float uFogStart;
            uniform float uFogEnd;
            uniform float uNearClipBlocks;
            uniform float uSeaLevel;
            uniform vec3 uSunDirection;
            uniform float uAmbientLight;
            uniform float uDirectLight;
            uniform vec3 uFogColor;
            out vec4 fragColor;

            float hashCell(vec2 p) {
                vec3 p3 = fract(vec3(p.x, p.y, p.x) * 0.1031);
                p3 += dot(p3, p3.yzx + 33.33);
                return fract((p3.x + p3.y) * p3.z);
            }
            float valueNoise(vec2 p, float period) {
                vec2 i = floor(p);
                vec2 f = fract(p);
                f = f * f * (3.0 - 2.0 * f);
                float a = hashCell(mod(i, period));
                float b = hashCell(mod(i + vec2(1.0, 0.0), period));
                float c = hashCell(mod(i + vec2(0.0, 1.0), period));
                float d = hashCell(mod(i + vec2(1.0, 1.0), period));
                return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
            }

            // Fade noise to neutral before its world-space cells become
            // sub-pixel. This suppresses aliasing, shimmer and distant grain.
            float filteredValueNoise(vec2 worldXZ, float cellSize, float period) {
                vec2 p = worldXZ / cellSize;
                float footprint = max(length(dFdx(p)), length(dFdy(p)));
                float weight = 1.0 - smoothstep(0.20, 0.65, footprint);
                if (weight <= 0.001) return 0.5;
                return mix(0.5, valueNoise(p, period), weight);
            }

            void main() {
                // Leave almost all real near chunks to the vanilla renderer,
                // retaining one chunk of overlap so no hole opens at the seam.
                if (length(vPosition.xz) < uNearClipBlocks) discard;

                // One texture lookup in the vertex shader; derive mesh normals
                // from GPU derivatives rather than sampling four extra heights.
                vec3 rawNormal = cross(dFdx(vPosition), dFdy(vPosition));
                float normalLength = length(rawNormal);
                vec3 faceNormal = normalLength > 0.00001
                    ? rawNormal / normalLength : vec3(0.0, 1.0, 0.0);
                if (faceNormal.y < 0.0) faceNormal = -faceNormal;
                vec3 normal = normalize(mix(faceNormal,
                    normalize(vSmoothNormal), clamp(vNormalBlend, 0.0, 1.0)));
                float slope = 1.0 - clamp(normal.y, 0.0, 1.0);

                // Broad, derivative-filtered fields retain natural regional
                // variation without high-frequency per-vertex material noise.
                float macro = filteredValueNoise(vNoisePosition, 2048.0, 128.0);
                float regional = filteredValueNoise(vNoisePosition, 512.0, 512.0);
                float moisture = clamp(0.72 * macro + 0.28 * regional, 0.0, 1.0);

                vec3 dryGrass = vec3(0.37, 0.34, 0.22);
                vec3 grass = vec3(0.18, 0.34, 0.16);
                vec3 rock = vec3(0.42, 0.40, 0.37);
                vec3 sand = vec3(0.61, 0.55, 0.38);
                vec3 snow = vec3(0.84, 0.88, 0.91);
                vec3 water = vec3(0.065, 0.23, 0.33);

                vec3 land = mix(dryGrass, grass, smoothstep(0.15, 0.85, moisture));
                float rockCover = max(smoothstep(0.16, 0.55, slope),
                    smoothstep(uSeaLevel + 197.0, uSeaLevel + 737.0, vHeight) * 0.6);
                land = mix(land, rock, rockCover);
                float beach = (1.0 - smoothstep(uSeaLevel + 2.0, uSeaLevel + 25.0, vHeight))
                    * (1.0 - smoothstep(0.10, 0.34, slope));
                land = mix(land, sand, 0.85 * beach);
                float snowCover = smoothstep(uSeaLevel + 677.0, uSeaLevel + 1157.0,
                    vHeight + (macro - 0.5) * 130.0);
                land = mix(land, snow, snowCover);

                // Terrain shape, slope and biome palette carry the scene;
                // material noise remains subtle instead of dominating it.
                float variation = mix(0.97, 1.03, regional);
                land *= variation;
                vec3 color = mix(land, water * mix(0.97, 1.03, macro), vOcean);
                float shallowShore = vOcean *
                    (1.0 - smoothstep(0.0, 5.0, uSeaLevel - vHeight));
                color = mix(color, vec3(0.52, 0.64, 0.61),
                    shallowShore * 0.23);
                float diffuse = max(dot(normal, uSunDirection), 0.0);
                color *= uAmbientLight + uDirectLight * diffuse;

                float fog = smoothstep(uFogStart, uFogEnd, vDistance);
                fragColor = vec4(mix(color, uFogColor, fog), 1.0);
            }
            """;

    private static final FloatBuffer MATRIX_UPLOAD = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer ORIGIN_UPLOAD = BufferUtils.createFloatBuffer(LEVEL_COUNT * 2);
    private static final FloatBuffer NOISE_ORIGIN_UPLOAD = BufferUtils.createFloatBuffer(LEVEL_COUNT * 2);
    private static final FloatBuffer TEXTURE_OFFSET_UPLOAD = BufferUtils.createFloatBuffer(LEVEL_COUNT * 2);
    private static final FloatBuffer SPACING_UPLOAD = BufferUtils.createFloatBuffer(LEVEL_COUNT);
    private static final FloatBuffer SKIRT_DEPTH_UPLOAD = BufferUtils.createFloatBuffer(LEVEL_COUNT);
    private static final FloatBuffer MORPH_BAND_UPLOAD = BufferUtils.createFloatBuffer(LEVEL_COUNT);
    private static final FloatBuffer INNER_HALF_CELLS_UPLOAD = BufferUtils.createFloatBuffer(LEVEL_COUNT);
    private static final Matrix4f MODEL_VIEW = new Matrix4f();
    private static final Matrix4f EXTENDED_PROJECTION = new Matrix4f();
    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
    private static boolean farPlaneDiagnosticLogged;
    private static int program;
    private static UniformLocations uniforms;
    private static ClipmapFogRange fogRange;
    private static ShortBuffer reusableHeightUpload;
    private static int vao;
    private static int vertexBuffer;
    private static int[] elementBuffers = new int[0];
    private static int[] textures = new int[0];
    private static int[] indexCounts = new int[0];
    private static int[] innerHalfCells = new int[0];
    private static int gridCells = -1;
    private static int nearChunks = -1;
    private static int baseSpacing = -1;
    private static final TextureWindow[] textureWindows = new TextureWindow[LEVEL_COUNT];

    private GeometryClipmapRenderer() {}

    static boolean render(RenderLevelStageEvent event,
            Map<Integer, org.realscale.neoforge.network.ClipmapHeightTilePayload> tiles,
            String dimensionKey, List<VisibleSurfaceProxy> proxies,
            ClipmapEnvironment.State environment) {
        RenderSystem.assertOnRenderThread();
        if (tiles == null || tiles.isEmpty() || proxies == null
                || environment == null) return false;

        var first = tiles.get(0);
        if (first == null || !first.dimensionKey().equals(dimensionKey)) return false;
        int nextGridCells = first.gridCells();
        int nextNearChunks = first.nearRenderDistanceChunks();
        int nextBaseSpacing = first.cellSpacingBlocks();
        long nextRequestId = first.requestId();
        int nextMinHeight = first.minHeight();
        int nextMaxHeight = first.maxHeight();

        for (int i = 0; i < tiles.size(); i++) {
            var tile = tiles.get(i);
            long expectedSpacing = (long) nextBaseSpacing << i;
            if (tile == null || !dimensionKey.equals(tile.dimensionKey())
                    || tile.requestId() != nextRequestId
                    || tile.gridCells() != nextGridCells
                    || tile.nearRenderDistanceChunks() != nextNearChunks
                    || tile.cellSpacingBlocks() != expectedSpacing
                    || tile.minHeight() != nextMinHeight
                    || tile.maxHeight() != nextMaxHeight
                    || tile.levelIndex() != i) {
                return false;
            }
        }

        // Capture BEFORE initialization/texture uploads. Otherwise Minecraft's
        // previous VAO, texture unit, binding and pixel-store state may leak.
        long renderStartedNanos = DEBUG ? System.nanoTime() : 0L;
        long uploadedBytesThisFrame = 0L;
        boolean completedFrame = false;
        State state = State.capture();
        try {
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            if (program == 0 || gridCells != nextGridCells
                    || nearChunks != nextNearChunks || baseSpacing != nextBaseSpacing) {
                dispose();
                initialize(nextGridCells, nextNearChunks, nextBaseSpacing);
            }

            // R16 height grids have an odd width (129 texels = 258 bytes/row).
            // Default 4-byte GL unpack alignment would overread/skew every row.
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SWAP_BYTES, 0);
            for (int i = 0; i < tiles.size(); i++) {
                var tile = tiles.get(i);
                long bytes = updateTexture(i, tile);
                if (DEBUG) uploadedBytesThisFrame += bytes;
            }

            fogRange = ClipmapFogRange.forGrid(nextGridCells, nextBaseSpacing, tiles.size());
            draw(event, tiles, proxies, environment);
            completedFrame = true;
            return true;
        } finally {
            state.restore();
            if (DEBUG && completedFrame) {
                recordDebugFrame(System.nanoTime() - renderStartedNanos,
                        uploadedBytesThisFrame, proxies.size());
            }
        }
    }


    /**
     * Optional CPU-side submission telemetry. This deliberately does not use
     * glFinish, so it never stalls the GPU and is not a GPU-time measurement.
     */
    private static void recordDebugFrame(long elapsedNanos,
            long uploadedBytes, int proxyCount) {
        long now = System.nanoTime();
        if (debugWindowStartNanos == 0L) debugWindowStartNanos = now;
        debugFrameCount++;
        debugTotalRenderNanos += elapsedNanos;
        debugMaxRenderNanos = Math.max(debugMaxRenderNanos, elapsedNanos);
        debugUploadedBytes += uploadedBytes;
        debugProxyCount = Math.max(debugProxyCount, proxyCount);

        long windowNanos = now - debugWindowStartNanos;
        if (windowNanos < 5_000_000_000L) return;
        double seconds = windowNanos / 1_000_000_000.0;
        double averageMs = debugTotalRenderNanos / (double) debugFrameCount / 1_000_000.0;
        double maxMs = debugMaxRenderNanos / 1_000_000.0;
        double fps = debugFrameCount / seconds;
        double uploadKiB = debugUploadedBytes / 1024.0;
        System.out.printf(Locale.ROOT,
                "[RealScale Geometry Clipmap] client avgFPS=%.1f cpuSubmitAvg=%.2fms "
                        + "cpuSubmitMax=%.2fms upload=%.1fKiB/5s maxProxySet=%d%n",
                fps, averageMs, maxMs, uploadKiB, debugProxyCount);
        debugWindowStartNanos = now;
        debugFrameCount = 0L;
        debugTotalRenderNanos = 0L;
        debugMaxRenderNanos = 0L;
        debugUploadedBytes = 0L;
        debugProxyCount = 0L;
    }

    static void dispose() {
        RenderSystem.assertOnRenderThread();
        if (vao != 0) GL30.glDeleteVertexArrays(vao);
        if (vertexBuffer != 0) GL15.glDeleteBuffers(vertexBuffer);
        for (int buffer : elementBuffers) if (buffer != 0) GL15.glDeleteBuffers(buffer);
        for (int texture : textures) if (texture != 0) GL11.glDeleteTextures(texture);
        if (program != 0) GL20.glDeleteProgram(program);
        VisibleSurfaceProxyRenderer.dispose();
        program = 0;
        uniforms = null;
        fogRange = null;
        reusableHeightUpload = null;
        farPlaneDiagnosticLogged = false;
        vao = 0;
        vertexBuffer = 0;
        elementBuffers = new int[0];
        textures = new int[0];
        indexCounts = new int[0];
        innerHalfCells = new int[0];
        gridCells = -1;
        nearChunks = -1;
        baseSpacing = -1;
        for (int i = 0; i < textureWindows.length; i++) textureWindows[i] = null;
    }

    private static void initialize(int cells, int near, int spacing) {
        try {
        gridCells = cells;
        nearChunks = near;
        baseSpacing = spacing;
        program = linkProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        uniforms = UniformLocations.lookup(program);
        fogRange = ClipmapFogRange.forGrid(cells, spacing, LEVEL_COUNT);
        reusableHeightUpload = BufferUtils.createShortBuffer((cells + 1) * (cells + 1));
        vao = GL30.glGenVertexArrays();
        vertexBuffer = GL15.glGenBuffers();
        if (vao == 0 || vertexBuffer == 0) {
            throw new IllegalStateException("OpenGL failed to allocate Geometry Clipmap vertex resources");
        }
        elementBuffers = new int[LEVEL_COUNT];
        textures = new int[LEVEL_COUNT];
        indexCounts = new int[LEVEL_COUNT];
        innerHalfCells = new int[LEVEL_COUNT];

        int side = cells + 1;
        float[] meshVertices = ClipmapRingMesh.vertices(cells);
        FloatBuffer vertices = BufferUtils.createFloatBuffer(meshVertices.length);
        vertices.put(meshVertices).flip();

        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vertexBuffer);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STATIC_DRAW);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 3 * Float.BYTES, 0L);

        for (int level = 0; level < LEVEL_COUNT; level++) {
            long levelSpacing = (long) spacing << level;
            int holeRadius = level == 0
                    ? Math.toIntExact((near * 16L
                            - ((long) spacing << (LEVEL_COUNT - 1)) / 2L)
                            / levelSpacing)
                    : cells / 4;
            innerHalfCells[level] = holeRadius;
            int[] indices = ClipmapRingMesh.indices(cells, holeRadius);
            indexCounts[level] = indices.length;
            IntBuffer indexBuffer = BufferUtils.createIntBuffer(indices.length);
            indexBuffer.put(indices).flip();
            elementBuffers[level] = GL15.glGenBuffers();
            if (elementBuffers[level] == 0) {
                throw new IllegalStateException("OpenGL failed to allocate clipmap index buffer " + level);
            }
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, elementBuffers[level]);
            GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER,
                    indexBuffer, GL15.GL_STATIC_DRAW);

            textures[level] = GL11.glGenTextures();
            if (textures[level] == 0) {
                throw new IllegalStateException("OpenGL failed to allocate clipmap height texture " + level);
            }
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures[level]);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R16,
                    side, side, 0, GL11.GL_RED, GL11.GL_UNSIGNED_SHORT,
                    (ShortBuffer) null);
        }
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        } catch (RuntimeException | Error failure) {
            // A partial GL allocation must never look initialized on the next
            // frame. Release every object created so far and allow a clean retry.
            dispose();
            throw failure;
        }
    }

    /**
     * Updates a toroidal R16 texture. Small snapped camera moves preserve the
     * old world-space overlap in-place, uploading newly exposed outer strips
     * and the part of the former inner hole that is now exposed.
     * A full upload is used after teleports, incompatible snapshots, or large
     * jumps where many tiny transfers would cost more than one transfer.
     */
    private static long updateTexture(int level,
            org.realscale.neoforge.network.ClipmapHeightTilePayload tile) {
        TextureWindow previous = textureWindows[level];
        // Snapshots are immutable. Most frames have the SAME tile instance:
        // don't allocate a plan/record or bind the texture again.
        if (previous != null && previous.sourceTile() == tile) return 0L;
        boolean compatible = previous != null && previous.matches(tile);
        ClipmapTextureUpdatePlan.Plan plan = compatible
                ? ClipmapTextureUpdatePlan.between(tile.gridCells(),
                        tile.cellSpacingBlocks(),
                        previous.originX(), previous.originZ(),
                        previous.offsetX(), previous.offsetZ(),
                        tile.originX(), tile.originZ(), innerHalfCells[level])
                : ClipmapTextureUpdatePlan.fullUpload(tile.gridCells());

        if (!plan.rectangles().isEmpty()) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures[level]);
            for (ClipmapTextureUpdatePlan.Rect rectangle : plan.rectangles()) {
                uploadGridRectangle(tile, rectangle, plan.offsetX(), plan.offsetZ());
            }
        }
        textureWindows[level] = new TextureWindow(tile, tile.requestId(),
                tile.dimensionKey(), tile.nearRenderDistanceChunks(),
                tile.gridCells(), tile.cellSpacingBlocks(),
                tile.originX(), tile.originZ(),
                tile.minHeight(), tile.maxHeight(),
                plan.offsetX(), plan.offsetZ());
        return plan.uploadedSamples() * Short.BYTES;
    }

    /**
     * Copies a logical tile rectangle into its wrapped physical texture
     * rectangle. A rectangle can cross either ring seam, so it is split into
     * at most four tightly packed uploads.
     */
    private static void uploadGridRectangle(
            org.realscale.neoforge.network.ClipmapHeightTilePayload tile,
            ClipmapTextureUpdatePlan.Rect rectangle,
            int offsetX, int offsetZ) {
        int side = tile.gridCells() + 1;
        int destinationX = Math.floorMod(rectangle.x() + offsetX, side);
        int destinationZ = Math.floorMod(rectangle.z() + offsetZ, side);
        int firstWidth = Math.min(rectangle.width(), side - destinationX);
        int secondWidth = rectangle.width() - firstWidth;
        int firstHeight = Math.min(rectangle.height(), side - destinationZ);
        int secondHeight = rectangle.height() - firstHeight;

        int[] widths = {firstWidth, secondWidth};
        int[] heights = {firstHeight, secondHeight};
        int[] sourceX = {rectangle.x(), rectangle.x() + firstWidth};
        int[] sourceZ = {rectangle.z(), rectangle.z() + firstHeight};
        int[] targetX = {destinationX, 0};
        int[] targetZ = {destinationZ, 0};

        ShortBuffer buffer = reusableHeightUpload;
        for (int zPart = 0; zPart < 2; zPart++) {
            if (heights[zPart] == 0) continue;
            for (int xPart = 0; xPart < 2; xPart++) {
                if (widths[xPart] == 0) continue;
                buffer.clear();
                tile.copyHeightsRegionTo(sourceX[xPart], sourceZ[zPart],
                        widths[xPart], heights[zPart], buffer);
                buffer.flip();
                GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0,
                        targetX[xPart], targetZ[zPart],
                        widths[xPart], heights[zPart],
                        GL11.GL_RED, GL11.GL_UNSIGNED_SHORT, buffer);
            }
        }
    }

    private record TextureWindow(
            org.realscale.neoforge.network.ClipmapHeightTilePayload sourceTile,
            long requestId, String dimensionKey,
            int nearChunks, int gridCells, int spacing,
            long originX, long originZ, int minHeight, int maxHeight,
            int offsetX, int offsetZ) {
        private boolean matches(
                org.realscale.neoforge.network.ClipmapHeightTilePayload tile) {
            return requestId == tile.requestId()
                    && dimensionKey.equals(tile.dimensionKey())
                    && nearChunks == tile.nearRenderDistanceChunks()
                    && gridCells == tile.gridCells()
                    && spacing == tile.cellSpacingBlocks()
                    && minHeight == tile.minHeight()
                    && maxHeight == tile.maxHeight();
        }
    }

    private static void draw(RenderLevelStageEvent event,
        Map<Integer, org.realscale.neoforge.network.ClipmapHeightTilePayload> tiles,
        List<VisibleSurfaceProxy> proxies, ClipmapEnvironment.State environment) {
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();

        GL20.glUseProgram(program);
        Matrix4f modelView = MODEL_VIEW.set(event.getModelViewMatrix());
        modelView.setTranslation(0.0f, 0.0f, 0.0f);
        // Minecraft's projection far plane is tied to vanilla view distance
        // (normally 4x render-distance blocks). Reusing it silently clips the
        // clipmap's 10-16 km rings. Preserve the vanilla FOV/aspect/near plane,
        // but extend only the depth range far enough to include the fogged LOD
        // horizon. Without this, the sampler and GL draw calls can succeed
        // while every genuinely distant triangle is rejected by clip space.
        Matrix4f projection = EXTENDED_PROJECTION.set(event.getProjectionMatrix());
        float vanillaFarPlane = ClipmapProjectionMath.farPlaneBlocks(projection);
        float requiredFarPlane = Math.max(vanillaFarPlane,
                fogRange.endBlocks() * 1.5f);
        float actualFarPlane = ClipmapProjectionMath.extendFarPlane(
                projection, requiredFarPlane);
        if (DEBUG && !farPlaneDiagnosticLogged) {
            farPlaneDiagnosticLogged = true;
            System.out.printf(Locale.ROOT,
                    "[RealScale Geometry Clipmap] projectionFar vanilla=%.1f " +
                            "clipmap=%.1f fogEnd=%.1f blocks%n",
                    vanillaFarPlane, actualFarPlane, fogRange.endBlocks());
        }
        Matrix4f viewProjection = VIEW_PROJECTION.set(projection).mul(modelView);
        FloatBuffer matrix = MATRIX_UPLOAD;
        matrix.clear();
        viewProjection.get(matrix);
        matrix.flip();

        int loc = uniforms.viewProjection();
        GL20.glUniformMatrix4fv(loc, false, matrix);
        Camera camera = event.getCamera();
        double cameraX = camera.getPosition().x;
        double cameraY = camera.getPosition().y;
        double cameraZ = camera.getPosition().z;
        GL20.glUniform1f(uniforms.cameraY(),
                (float) cameraY);
        GL20.glUniform1f(uniforms.gridCells(),
                gridCells);
        var heightRange = tiles.get(0);
        GL20.glUniform1f(uniforms.heightMin(), heightRange.minHeight());
        GL20.glUniform1f(uniforms.heightMax(), heightRange.maxHeight());
        GL20.glUniform1f(uniforms.seaLevel(), environment.seaLevel());
        GL20.glUniform3f(uniforms.sunDirection(),
                environment.sunX(), environment.sunY(), environment.sunZ());
        GL20.glUniform1f(uniforms.ambientLight(), environment.ambientLight());
        GL20.glUniform1f(uniforms.directLight(), environment.directLight());
        GL20.glUniform1f(uniforms.fogStart(), fogRange.startBlocks());
        GL20.glUniform1f(uniforms.fogEnd(), fogRange.endBlocks());
        ClipmapFogColor.upload(uniforms.fogColor());
        GL20.glUniform1i(uniforms.heightMap(), 0);
        GL20.glUniform1f(uniforms.nearClipBlocks(),
                Math.max(0.0f, nearChunks * 16.0f - 16.0f));

        FloatBuffer originUpload = ORIGIN_UPLOAD;
        FloatBuffer noiseOriginUpload = NOISE_ORIGIN_UPLOAD;
        FloatBuffer textureOffsetUpload = TEXTURE_OFFSET_UPLOAD;
        FloatBuffer spacingUpload = SPACING_UPLOAD;
        FloatBuffer skirtDepthUpload = SKIRT_DEPTH_UPLOAD;
        FloatBuffer morphBandUpload = MORPH_BAND_UPLOAD;
        FloatBuffer innerHalfCellsUpload = INNER_HALF_CELLS_UPLOAD;
        originUpload.clear();
        noiseOriginUpload.clear();
        textureOffsetUpload.clear();
        spacingUpload.clear();
        skirtDepthUpload.clear();
        morphBandUpload.clear();
        innerHalfCellsUpload.clear();
        for (int level = 0; level < LEVEL_COUNT; level++) {
            var tile = tiles.get(level);
            if (tile == null) {
                originUpload.put(0).put(0); noiseOriginUpload.put(0).put(0);
                textureOffsetUpload.put(0).put(0); spacingUpload.put(1);
                skirtDepthUpload.put(0); morphBandUpload.put(0);
                innerHalfCellsUpload.put(0);
                continue;
            }
            TextureWindow textureWindow = textureWindows[level];
            originUpload.put((float) (tile.originX() - cameraX))
                    .put((float) (tile.originZ() - cameraZ));
            noiseOriginUpload.put((float) Math.floorMod(tile.originX(), 65536L))
                    .put((float) Math.floorMod(tile.originZ(), 65536L));
            textureOffsetUpload.put(textureWindow.offsetX())
                    .put(textureWindow.offsetZ());
            spacingUpload.put(tile.cellSpacingBlocks());
            skirtDepthUpload.put(Math.min(32.0f, Math.max(4.0f,
                    tile.cellSpacingBlocks() * 0.125f)));
            morphBandUpload.put(level == tiles.size() - 1 ? 0.0f : 8.0f);
            innerHalfCellsUpload.put(innerHalfCells[level]);
        }
        originUpload.flip();
        noiseOriginUpload.flip();
        textureOffsetUpload.flip();
        spacingUpload.flip();
        skirtDepthUpload.flip();
        morphBandUpload.flip();
        innerHalfCellsUpload.flip();
        GL20.glUniform2fv(uniforms.origin(), originUpload);
        GL20.glUniform2fv(uniforms.noiseOrigin(), noiseOriginUpload);
        GL20.glUniform2fv(uniforms.textureOffset(), textureOffsetUpload);
        GL20.glUniform1fv(uniforms.spacing(), spacingUpload);
        GL20.glUniform1fv(uniforms.skirtDepth(), skirtDepthUpload);
        GL20.glUniform1fv(uniforms.morphBandCells(), morphBandUpload);
        GL20.glUniform1fv(uniforms.innerHalfCells(), innerHalfCellsUpload);

        GL30.glBindVertexArray(vao);
        for (int level = tiles.size() - 1; level >= 0; level--) {
            GL20.glUniform1i(uniforms.level(), level);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures[level]);
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER,
                    elementBuffers[level]);
            GL11.glDrawElements(GL11.GL_TRIANGLES, indexCounts[level],
                    GL11.GL_UNSIGNED_INT, 0L);
        }
        GL30.glBindVertexArray(0);
        var centreTile = tiles.get(0);
        long centreX = centreTile.originX()
                + (long) (centreTile.gridCells() / 2) * centreTile.cellSpacingBlocks();
        long centreZ = centreTile.originZ()
                + (long) (centreTile.gridCells() / 2) * centreTile.cellSpacingBlocks();
        VisibleSurfaceProxyRenderer.draw(event, projection, proxies, centreX, centreZ,
                nearChunks, fogRange.startBlocks(), fogRange.endBlocks(),
                environment.ambientLight() + environment.directLight() * 0.65f);
    }

    private static int linkProgram(String vertexSource, String fragmentSource) {
        int vertex = 0;
        int fragment = 0;
        int linked = 0;
        boolean vertexAttached = false;
        boolean fragmentAttached = false;
        try {
            vertex = compileShader(GL20.GL_VERTEX_SHADER, vertexSource);
            fragment = compileShader(GL20.GL_FRAGMENT_SHADER, fragmentSource);
            linked = GL20.glCreateProgram();
            if (linked == 0) {
                throw new IllegalStateException("OpenGL failed to allocate Geometry Clipmap shader program");
            }
            GL20.glAttachShader(linked, vertex);
            vertexAttached = true;
            GL20.glAttachShader(linked, fragment);
            fragmentAttached = true;
            GL20.glLinkProgram(linked);
            if (GL20.glGetProgrami(linked, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                throw new IllegalStateException("Geometry Clipmap shader link failed: "
                        + GL20.glGetProgramInfoLog(linked));
            }
            GL20.glDetachShader(linked, vertex);
            vertexAttached = false;
            GL20.glDetachShader(linked, fragment);
            fragmentAttached = false;
            int result = linked;
            linked = 0;
            return result;
        } finally {
            if (linked != 0) {
                if (vertexAttached && vertex != 0) GL20.glDetachShader(linked, vertex);
                if (fragmentAttached && fragment != 0) GL20.glDetachShader(linked, fragment);
                GL20.glDeleteProgram(linked);
            }
            if (vertex != 0) GL20.glDeleteShader(vertex);
            if (fragment != 0) GL20.glDeleteShader(fragment);
        }
    }

    private static int compileShader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        if (shader == 0) {
            throw new IllegalStateException("OpenGL failed to allocate Geometry Clipmap shader object");
        }
        try {
            GL20.glShaderSource(shader, source);
            GL20.glCompileShader(shader);
            if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
                throw new IllegalStateException("Geometry Clipmap shader compile failed: "
                        + GL20.glGetShaderInfoLog(shader));
            }
            return shader;
        } catch (RuntimeException | Error failure) {
            GL20.glDeleteShader(shader);
            throw failure;
        }
    }


    /**
     * Resolved once when the program links. glGetUniformLocation on every
     * frame and every LOD ring caused avoidable driver calls.
     */
    private record UniformLocations(
            int viewProjection, int cameraY, int gridCells,
            int heightMin, int heightMax, int seaLevel,
            int sunDirection, int ambientLight, int directLight,
            int fogStart, int fogEnd, int fogColor,
            int heightMap, int nearClipBlocks, int origin,
            int spacing, int textureOffset, int noiseOrigin, int skirtDepth,
            int morphBandCells, int innerHalfCells, int level) {
        private static UniformLocations lookup(int program) {
            return new UniformLocations(
                    GL20.glGetUniformLocation(program, "uViewProjection"),
                    GL20.glGetUniformLocation(program, "uCameraY"),
                    GL20.glGetUniformLocation(program, "uGridCells"),
                    GL20.glGetUniformLocation(program, "uHeightMin"),
                    GL20.glGetUniformLocation(program, "uHeightMax"),
                    GL20.glGetUniformLocation(program, "uSeaLevel"),
                    GL20.glGetUniformLocation(program, "uSunDirection"),
                    GL20.glGetUniformLocation(program, "uAmbientLight"),
                    GL20.glGetUniformLocation(program, "uDirectLight"),
                    GL20.glGetUniformLocation(program, "uFogStart"),
                    GL20.glGetUniformLocation(program, "uFogEnd"),
                    GL20.glGetUniformLocation(program, "uFogColor"),
                    GL20.glGetUniformLocation(program, "uHeightMap"),
                    GL20.glGetUniformLocation(program, "uNearClipBlocks"),
                    GL20.glGetUniformLocation(program, "uOrigin[0]"),
                    GL20.glGetUniformLocation(program, "uSpacing[0]"),
                    GL20.glGetUniformLocation(program, "uTextureOffset[0]"),
                    GL20.glGetUniformLocation(program, "uNoiseOrigin[0]"),
                    GL20.glGetUniformLocation(program, "uSkirtDepth[0]"),
                    GL20.glGetUniformLocation(program, "uMorphBandCells[0]"),
                    GL20.glGetUniformLocation(program, "uInnerHalfCells[0]"),
                    GL20.glGetUniformLocation(program, "uLevel"));
        }
    }

    private record State(int program, int vao, int arrayBuffer,
            int pixelUnpackBuffer, int activeTexture, int texture0, int unpackAlignment,
            int unpackRowLength, int unpackSkipPixels, int unpackSkipRows,
            int unpackSwapBytes, int depthFunc,
            boolean depthTest, boolean blend, boolean cull,
            boolean depthMask) {
        private static State capture() {
            int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            int texture0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL13.glActiveTexture(active);
            return new State(
                    GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
                    GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),
                    GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING),
                    GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING),
                    active, texture0,
                    GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT),
                    GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH),
                    GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS),
                    GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS),
                    GL11.glGetInteger(GL11.GL_UNPACK_SWAP_BYTES),
                    GL11.glGetInteger(GL11.GL_DEPTH_FUNC),
                    GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                    GL11.glIsEnabled(GL11.GL_BLEND),
                    GL11.glIsEnabled(GL11.GL_CULL_FACE),
                    GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK));
        }

        private void restore() {
            GL20.glUseProgram(program);
            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, pixelUnpackBuffer);
            // Restore raw bindings via raw GL. RenderSystem may cache
            // texture state, while our texture upload uses direct LWJGL.
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture0);
            GL13.glActiveTexture(activeTexture);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, unpackAlignment);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, unpackRowLength);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, unpackSkipPixels);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, unpackSkipRows);
            GL11.glPixelStorei(GL11.GL_UNPACK_SWAP_BYTES, unpackSwapBytes);
            if (depthTest) RenderSystem.enableDepthTest();
            else RenderSystem.disableDepthTest();
            if (blend) RenderSystem.enableBlend();
            else RenderSystem.disableBlend();
            if (cull) RenderSystem.enableCull();
            else RenderSystem.disableCull();
            RenderSystem.depthFunc(depthFunc);
            RenderSystem.depthMask(depthMask);
        }
    }
}
