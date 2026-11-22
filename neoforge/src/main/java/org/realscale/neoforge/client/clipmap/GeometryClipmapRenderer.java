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
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.Map;
import java.util.List;

/**
 * OpenGL 4.1 Geometry Clipmap with procedural exterior shading.
 *
 * <p>It draws six nested surface rings from compact R16 height textures.
 * The mesh is static; height textures are replaced when the server sends a
 * new snapped window. All GL work is restricted to the render thread.
 */
final class GeometryClipmapRenderer {
    private static final int LEVEL_COUNT = 6;
    private static final int MIN_HEIGHT = -64;
    private static final int MAX_HEIGHT = 2032;
    private static final String VERTEX_SHADER = """
            #version 410 core
            layout(location = 0) in vec3 aGridAndSkirt;
            uniform mat4 uViewProjection;
            uniform sampler2D uHeightMap;
            uniform vec2 uOrigin;
            uniform vec2 uNoiseOrigin;
            uniform float uCameraY;
            uniform float uSpacing;
            uniform float uGridCells;
            uniform float uHeightMin;
            uniform float uHeightMax;
            uniform float uSeaLevel;
            uniform float uSkirtDepth;
            uniform float uMorphBandCells;
            out float vHeight;
            out float vOcean;
            out float vDistance;
            out vec3 vPosition;
            out vec2 vNoiseXZ;
            void main() {
                vec2 grid = aGridAndSkirt.xy;
                float side = uGridCells + 1.0;
                vec2 uv = (grid + vec2(0.5)) / side;
                float h = mix(uHeightMin, uHeightMax, texture(uHeightMap, uv).r);
                // A fine outer boundary must converge on the 2x parent grid.
                // These parent heights are sampled at even vertices of this
                // SAME R16 texture; no additional worldgen or network data.
                float edgeCells = min(min(grid.x, grid.y),
                    min(uGridCells - grid.x, uGridCells - grid.y));
                if (uMorphBandCells > 0.0 && edgeCells < uMorphBandCells) {
                    vec2 parentGrid = floor(grid * 0.5) * 2.0;
                    vec2 parentUV = (parentGrid + vec2(0.5)) / side;
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
                        uMorphBandCells, edgeCells);
                    h = mix(h, parentHeight, morph);
                }
                float ocean = step(h, uSeaLevel);
                float surfaceY = mix(h, uSeaLevel, ocean)
                    - aGridAndSkirt.z * uSkirtDepth;

                vec2 worldXZ = uOrigin + grid * uSpacing;
                vec3 relative = vec3(worldXZ.x, surfaceY - uCameraY, worldXZ.y);
                vHeight = h;
                vOcean = ocean;
                vPosition = relative;
                vDistance = length(relative.xz);
                vNoiseXZ = uNoiseOrigin + grid * uSpacing;
                gl_Position = uViewProjection * vec4(relative, 1.0);
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #version 410 core
            in float vHeight;
            in float vOcean;
            in float vDistance;
            in vec3 vPosition;
            in vec2 vNoiseXZ;
            uniform float uFogStart;
            uniform float uFogEnd;
            uniform float uNearClipBlocks;
            uniform float uSeaLevel;
            uniform vec3 uFogColor;
            out vec4 fragColor;

            // World-aligned, periodic detail. Each octave wraps at exactly
            // 65536 blocks to avoid float precision collapse at far coords.
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

            void main() {
                // Leave almost all real near chunks to the vanilla renderer,
                // retaining one chunk of overlap so no hole opens at the seam.
                if (length(vPosition.xz) < uNearClipBlocks) discard;

                // One texture lookup in the vertex shader; derive mesh normals
                // from GPU derivatives rather than sampling four extra heights.
                vec3 rawNormal = cross(dFdx(vPosition), dFdy(vPosition));
                float normalLength = length(rawNormal);
                vec3 normal = normalLength > 0.00001
                    ? rawNormal / normalLength : vec3(0.0, 1.0, 0.0);
                if (normal.y < 0.0) normal = -normal;
                float slope = 1.0 - clamp(normal.y, 0.0, 1.0);

                float macro = valueNoise(vNoiseXZ / 512.0, 128.0);
                float regional = valueNoise(vNoiseXZ / 128.0, 512.0);
                float detailFade = 1.0 - smoothstep(700.0, 4500.0, vDistance);
                float micro = detailFade > 0.0
                    ? valueNoise(vNoiseXZ / 32.0, 2048.0) : 0.5;
                float moisture = clamp(0.6 * macro + 0.4 * regional, 0.0, 1.0);

                vec3 dryGrass = vec3(0.37, 0.34, 0.22);
                vec3 grass = vec3(0.18, 0.34, 0.16);
                vec3 rock = vec3(0.42, 0.40, 0.37);
                vec3 sand = vec3(0.61, 0.55, 0.38);
                vec3 snow = vec3(0.84, 0.88, 0.91);
                vec3 water = vec3(0.065, 0.23, 0.33);

                vec3 land = mix(dryGrass, grass, smoothstep(0.15, 0.85, moisture));
                float rockCover = max(smoothstep(0.10, 0.45, slope),
                    smoothstep(260.0, 800.0, vHeight) * 0.6);
                land = mix(land, rock, rockCover);
                float beach = (1.0 - smoothstep(65.0, 88.0, vHeight))
                    * (1.0 - smoothstep(0.10, 0.34, slope));
                land = mix(land, sand, 0.85 * beach);
                float snowCover = smoothstep(740.0, 1220.0,
                    vHeight + (macro - 0.5) * 260.0);
                land = mix(land, snow, snowCover);

                float variation = mix(0.88, 1.12,
                    mix(regional, micro, detailFade * 0.55));
                land *= variation;
                vec3 color = mix(land, water * mix(0.90, 1.10, macro), vOcean);
                float shallowShore = vOcean *
                    (1.0 - smoothstep(0.0, 5.0, uSeaLevel - vHeight));
                color = mix(color, vec3(0.52, 0.64, 0.61),
                    shallowShore * 0.23);
                vec3 sun = normalize(vec3(0.38, 0.83, 0.40));
                color *= 0.60 + 0.40 * max(dot(normal, sun), 0.0);

                float fog = smoothstep(uFogStart, uFogEnd, vDistance);
                fragColor = vec4(mix(color, uFogColor, fog), 1.0);
            }
            """;

    private static final FloatBuffer MATRIX_UPLOAD = BufferUtils.createFloatBuffer(16);
    private static final Matrix4f MODEL_VIEW = new Matrix4f();
    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
    private static int program;
    private static UniformLocations uniforms;
    private static ClipmapFogRange fogRange;
    private static ShortBuffer reusableHeightUpload;
    private static int vao;
    private static int vertexBuffer;
    private static int[] elementBuffers = new int[0];
    private static int[] textures = new int[0];
    private static int[] indexCounts = new int[0];
    private static int gridCells = -1;
    private static int nearChunks = -1;
    private static int baseSpacing = -1;
    private static final org.realscale.neoforge.network.ClipmapHeightTilePayload[] uploaded =
            new org.realscale.neoforge.network.ClipmapHeightTilePayload[LEVEL_COUNT];

    private GeometryClipmapRenderer() {}

    static void render(RenderLevelStageEvent event,
            Map<Integer, org.realscale.neoforge.network.ClipmapHeightTilePayload> tiles,
            String dimensionKey, List<VisibleSurfaceProxy> proxies) {
        RenderSystem.assertOnRenderThread();
        if (tiles.size() < LEVEL_COUNT) return;

        var first = tiles.get(0);
        if (first == null || !first.dimensionKey().equals(dimensionKey)) return;
        int nextGridCells = first.gridCells();
        int nextNearChunks = first.nearRenderDistanceChunks();
        int nextBaseSpacing = first.cellSpacingBlocks();

        for (int i = 0; i < LEVEL_COUNT; i++) {
            var tile = tiles.get(i);
            if (tile == null || !dimensionKey.equals(tile.dimensionKey())
                    || tile.gridCells() != nextGridCells
                    || tile.nearRenderDistanceChunks() != nextNearChunks
                    || tile.levelIndex() != i) {
                return;
            }
        }

        // Capture BEFORE initialization/texture uploads. Otherwise Minecraft's
        // previous VAO, texture unit, binding and pixel-store state may leak.
        State state = State.capture();
        try {
            if (program == 0 || gridCells != nextGridCells
                    || nearChunks != nextNearChunks || baseSpacing != nextBaseSpacing) {
                dispose();
                initialize(nextGridCells, nextNearChunks, nextBaseSpacing);
            }

            // R16 height grids have an odd width (129 texels = 258 bytes/row).
            // Default 4-byte GL unpack alignment would overread/skew every row.
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            for (int i = 0; i < LEVEL_COUNT; i++) {
                var tile = tiles.get(i);
                if (uploaded[i] != tile) {
                    upload(i, tile);
                    uploaded[i] = tile;
                }
            }

            draw(event, tiles, proxies);
        } finally {
            state.restore();
        }
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
        vao = 0;
        vertexBuffer = 0;
        elementBuffers = new int[0];
        textures = new int[0];
        indexCounts = new int[0];
        gridCells = -1;
        nearChunks = -1;
        baseSpacing = -1;
        for (int i = 0; i < uploaded.length; i++) uploaded[i] = null;
    }

    private static void initialize(int cells, int near, int spacing) {
        gridCells = cells;
        nearChunks = near;
        baseSpacing = spacing;
        program = linkProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        uniforms = UniformLocations.lookup(program);
        fogRange = ClipmapFogRange.forGrid(cells, spacing, LEVEL_COUNT);
        reusableHeightUpload = BufferUtils.createShortBuffer((cells + 1) * (cells + 1));
        vao = GL30.glGenVertexArrays();
        vertexBuffer = GL15.glGenBuffers();
        elementBuffers = new int[LEVEL_COUNT];
        textures = new int[LEVEL_COUNT];
        indexCounts = new int[LEVEL_COUNT];

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
            int innerHalfCells = level == 0
                    ? Math.toIntExact((near * 16L
                            - ((long) spacing << (LEVEL_COUNT - 1)) / 2L)
                            / levelSpacing)
                    : cells / 4;
            int[] indices = ClipmapRingMesh.indices(cells, innerHalfCells);
            indexCounts[level] = indices.length;
            IntBuffer indexBuffer = BufferUtils.createIntBuffer(indices.length);
            indexBuffer.put(indices).flip();
            elementBuffers[level] = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, elementBuffers[level]);
            GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER,
                    indexBuffer, GL15.GL_STATIC_DRAW);

            textures[level] = GL11.glGenTextures();
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures[level]);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R16,
                    side, side, 0, GL11.GL_RED, GL11.GL_UNSIGNED_SHORT,
                    (ShortBuffer) null);
        }
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    private static void upload(int level,
            org.realscale.neoforge.network.ClipmapHeightTilePayload tile) {
        int side = tile.gridCells() + 1;
        short[] values = tile.heights();
        ShortBuffer buffer = reusableHeightUpload;
        buffer.clear();
        buffer.put(values).flip();
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures[level]);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0,
                side, side, GL11.GL_RED, GL11.GL_UNSIGNED_SHORT, buffer);
    }

    private static void draw(RenderLevelStageEvent event,
        Map<Integer, org.realscale.neoforge.network.ClipmapHeightTilePayload> tiles,
        List<VisibleSurfaceProxy> proxies) {
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();

        GL20.glUseProgram(program);
        Matrix4f modelView = MODEL_VIEW.set(event.getModelViewMatrix());
        modelView.setTranslation(0.0f, 0.0f, 0.0f);
        Matrix4f viewProjection = VIEW_PROJECTION.set(event.getProjectionMatrix())
                .mul(modelView);
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
        GL20.glUniform1f(uniforms.heightMin(),
                MIN_HEIGHT);
        GL20.glUniform1f(uniforms.heightMax(),
                MAX_HEIGHT);
        GL20.glUniform1f(uniforms.seaLevel(), 63.0f);
        GL20.glUniform1f(uniforms.fogStart(), fogRange.startBlocks());
        GL20.glUniform1f(uniforms.fogEnd(), fogRange.endBlocks());
        GL20.glUniform3f(uniforms.fogColor(),
                0.58f, 0.69f, 0.80f);
        GL20.glUniform1i(uniforms.heightMap(), 0);
        GL20.glUniform1f(uniforms.nearClipBlocks(),
                Math.max(0.0f, nearChunks * 16.0f - 16.0f));

        GL30.glBindVertexArray(vao);
        for (int level = LEVEL_COUNT - 1; level >= 0; level--) {
            var tile = tiles.get(level);
            GL20.glUniform2f(uniforms.origin(),
                    (float) (tile.originX() - cameraX),
                    (float) (tile.originZ() - cameraZ));
            GL20.glUniform1f(uniforms.spacing(),
                    tile.cellSpacingBlocks());
            GL20.glUniform2f(uniforms.noiseOrigin(),
                    (float) Math.floorMod(tile.originX(), 65536L),
                    (float) Math.floorMod(tile.originZ(), 65536L));
            GL20.glUniform1f(uniforms.skirtDepth(),
                    Math.min(32.0f, Math.max(4.0f,
                            tile.cellSpacingBlocks() * 0.125f)));
            GL20.glUniform1f(uniforms.morphBandCells(),
                    level == LEVEL_COUNT - 1 ? 0.0f : 8.0f);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures[level]);
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER,
                    elementBuffers[level]);
            GL11.glDrawElements(GL11.GL_TRIANGLES, indexCounts[level],
                    GL11.GL_UNSIGNED_INT, 0L);
        }
        GL30.glBindVertexArray(0);
        VisibleSurfaceProxyRenderer.draw(event, proxies,
                nearChunks, fogRange.startBlocks(), fogRange.endBlocks());
    }

    private static int linkProgram(String vertexSource, String fragmentSource) {
        int vertex = compileShader(GL20.GL_VERTEX_SHADER, vertexSource);
        int fragment = compileShader(GL20.GL_FRAGMENT_SHADER, fragmentSource);
        int linked = GL20.glCreateProgram();
        GL20.glAttachShader(linked, vertex);
        GL20.glAttachShader(linked, fragment);
        GL20.glLinkProgram(linked);
        if (GL20.glGetProgrami(linked, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(linked);
            GL20.glDeleteProgram(linked);
            GL20.glDeleteShader(vertex);
            GL20.glDeleteShader(fragment);
            throw new IllegalStateException("Geometry Clipmap shader link failed: " + log);
        }
        GL20.glDetachShader(linked, vertex);
        GL20.glDetachShader(linked, fragment);
        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);
        return linked;
    }

    private static int compileShader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("Geometry Clipmap shader compile failed: " + log);
        }
        return shader;
    }


    /**
     * Resolved once when the program links. glGetUniformLocation on every
     * frame and every LOD ring caused avoidable driver calls.
     */
    private record UniformLocations(
            int viewProjection, int cameraY, int gridCells,
            int heightMin, int heightMax, int seaLevel,
            int fogStart, int fogEnd, int fogColor,
            int heightMap, int nearClipBlocks, int origin,
            int spacing, int noiseOrigin, int skirtDepth,
            int morphBandCells) {
        private static UniformLocations lookup(int program) {
            return new UniformLocations(
                    GL20.glGetUniformLocation(program, "uViewProjection"),
                    GL20.glGetUniformLocation(program, "uCameraY"),
                    GL20.glGetUniformLocation(program, "uGridCells"),
                    GL20.glGetUniformLocation(program, "uHeightMin"),
                    GL20.glGetUniformLocation(program, "uHeightMax"),
                    GL20.glGetUniformLocation(program, "uSeaLevel"),
                    GL20.glGetUniformLocation(program, "uFogStart"),
                    GL20.glGetUniformLocation(program, "uFogEnd"),
                    GL20.glGetUniformLocation(program, "uFogColor"),
                    GL20.glGetUniformLocation(program, "uHeightMap"),
                    GL20.glGetUniformLocation(program, "uNearClipBlocks"),
                    GL20.glGetUniformLocation(program, "uOrigin"),
                    GL20.glGetUniformLocation(program, "uSpacing"),
                    GL20.glGetUniformLocation(program, "uNoiseOrigin"),
                    GL20.glGetUniformLocation(program, "uSkirtDepth"),
                    GL20.glGetUniformLocation(program, "uMorphBandCells"));
        }
    }

    private record State(int program, int vao, int arrayBuffer,
            int activeTexture, int texture0, int unpackAlignment, int depthFunc,
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
                    active, texture0,
                    GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT),
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
            // Restore raw bindings via raw GL. RenderSystem may cache
            // texture state, while our texture upload uses direct LWJGL.
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture0);
            GL13.glActiveTexture(activeTexture);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, unpackAlignment);
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
