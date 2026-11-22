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

/**
 * OpenGL 4.1 terrain-only Geometry Clipmap renderer.
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
            layout(location = 0) in vec2 aGrid;
            uniform mat4 uViewProjection;
            uniform sampler2D uHeightMap;
            uniform vec2 uOrigin;
            uniform float uCameraY;
            uniform float uSpacing;
            uniform float uGridCells;
            uniform float uHeightMin;
            uniform float uHeightMax;
            uniform float uSeaLevel;
            out float vHeight;
            out float vOcean;
            out float vSlope;
            out float vDistance;
            void main() {
                float side = uGridCells + 1.0;
                vec2 uv = (aGrid + vec2(0.5)) / side;
                float h = mix(uHeightMin, uHeightMax, texture(uHeightMap, uv).r);
                float ocean = step(h, uSeaLevel);
                float surfaceY = mix(h, uSeaLevel, ocean);

                float leftH = mix(uHeightMin, uHeightMax,
                    texture(uHeightMap, uv - vec2(1.0 / side, 0.0)).r);
                float rightH = mix(uHeightMin, uHeightMax,
                    texture(uHeightMap, uv + vec2(1.0 / side, 0.0)).r);
                float northH = mix(uHeightMin, uHeightMax,
                    texture(uHeightMap, uv - vec2(0.0, 1.0 / side)).r);
                float southH = mix(uHeightMin, uHeightMax,
                    texture(uHeightMap, uv + vec2(0.0, 1.0 / side)).r);
                float slope = length(vec2(rightH - leftH, southH - northH))
                    / max(2.0 * uSpacing, 1.0);

                vec2 worldXZ = uOrigin + aGrid * uSpacing;
                vec3 relative = vec3(worldXZ.x, surfaceY - uCameraY, worldXZ.y);
                vHeight = surfaceY;
                vOcean = ocean;
                vSlope = slope;
                vDistance = length(relative.xz);
                gl_Position = uViewProjection * vec4(relative, 1.0);
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #version 410 core
            in float vHeight;
            in float vOcean;
            in float vSlope;
            in float vDistance;
            uniform float uFogStart;
            uniform float uFogEnd;
            uniform vec3 uFogColor;
            out vec4 fragColor;
            void main() {
                vec3 grass = vec3(0.20, 0.34, 0.16);
                vec3 rock = vec3(0.40, 0.39, 0.36);
                vec3 snow = vec3(0.82, 0.85, 0.88);
                vec3 water = vec3(0.055, 0.22, 0.34);

                float steep = smoothstep(0.18, 0.62, vSlope);
                float highland = smoothstep(260.0, 780.0, vHeight);
                vec3 land = mix(grass, rock, max(steep, highland));
                land = mix(land, snow, smoothstep(720.0, 1320.0, vHeight));
                vec3 color = mix(land, water, vOcean);

                float fog = smoothstep(uFogStart, uFogEnd, vDistance);
                color = mix(color, uFogColor, fog);
                fragColor = vec4(color, 1.0);
            }
            """;

    private static int program;
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
            String dimensionKey) {
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

        if (program == 0 || gridCells != nextGridCells
                || nearChunks != nextNearChunks || baseSpacing != nextBaseSpacing) {
            dispose();
            initialize(nextGridCells, nextNearChunks, nextBaseSpacing);
        }

        for (int i = 0; i < LEVEL_COUNT; i++) {
            var tile = tiles.get(i);
            if (uploaded[i] != tile) {
                upload(i, tile);
                uploaded[i] = tile;
            }
        }

        draw(event, tiles);
    }

    static void dispose() {
        RenderSystem.assertOnRenderThread();
        if (vao != 0) GL30.glDeleteVertexArrays(vao);
        if (vertexBuffer != 0) GL15.glDeleteBuffers(vertexBuffer);
        for (int buffer : elementBuffers) if (buffer != 0) GL15.glDeleteBuffers(buffer);
        for (int texture : textures) if (texture != 0) GL11.glDeleteTextures(texture);
        if (program != 0) GL20.glDeleteProgram(program);
        program = 0;
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
        vao = GL30.glGenVertexArrays();
        vertexBuffer = GL15.glGenBuffers();
        elementBuffers = new int[LEVEL_COUNT];
        textures = new int[LEVEL_COUNT];
        indexCounts = new int[LEVEL_COUNT];

        int side = cells + 1;
        FloatBuffer vertices = BufferUtils.createFloatBuffer(side * side * 2);
        for (int z = 0; z <= cells; z++) {
            for (int x = 0; x <= cells; x++) {
                vertices.put((float) x).put((float) z);
            }
        }
        vertices.flip();

        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vertexBuffer);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STATIC_DRAW);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 2 * Float.BYTES, 0L);

        for (int level = 0; level < LEVEL_COUNT; level++) {
            int[] indices = buildRingIndices(cells, near, spacing, level);
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

    private static int[] buildRingIndices(int cells, int near, int baseSpacing,
            int level) {
        int spacing = baseSpacing << level;
        int innerHalfCells;
        if (level == 0) {
            long centreSnapSpacing = (long) baseSpacing << (LEVEL_COUNT - 1);
            long protectedRadius = near * 16L + centreSnapSpacing / 2L;
            innerHalfCells = Math.toIntExact(protectedRadius / spacing);
        } else {
            innerHalfCells = cells / 4;
        }
        int center = cells / 2;
        int min = center - innerHalfCells;
        int max = center + innerHalfCells;
        IntBuffer buffer = BufferUtils.createIntBuffer(cells * cells * 6);
        for (int z = 0; z < cells; z++) {
            for (int x = 0; x < cells; x++) {
                if (x >= min && x < max && z >= min && z < max) continue;
                int a = z * (cells + 1) + x;
                int b = (z + 1) * (cells + 1) + x;
                int c = z * (cells + 1) + x + 1;
                int d = (z + 1) * (cells + 1) + x + 1;
                buffer.put(a).put(b).put(c);
                buffer.put(c).put(b).put(d);
            }
        }
        buffer.flip();
        int[] result = new int[buffer.remaining()];
        buffer.get(result);
        return result;
    }

    private static void upload(int level,
            org.realscale.neoforge.network.ClipmapHeightTilePayload tile) {
        int side = tile.gridCells() + 1;
        short[] values = tile.heights();
        ShortBuffer buffer = BufferUtils.createShortBuffer(values.length);
        buffer.put(values).flip();
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures[level]);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0,
                side, side, GL11.GL_RED, GL11.GL_UNSIGNED_SHORT, buffer);
    }

    private static void draw(RenderLevelStageEvent event,
            Map<Integer, org.realscale.neoforge.network.ClipmapHeightTilePayload> tiles) {
        State state = State.capture();
        try {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.disableCull();

            GL20.glUseProgram(program);
            Matrix4f modelView = new Matrix4f(event.getModelViewMatrix());
            modelView.setTranslation(0.0f, 0.0f, 0.0f);
            Matrix4f viewProjection = new Matrix4f(event.getProjectionMatrix()).mul(modelView);
            FloatBuffer matrix = BufferUtils.createFloatBuffer(16);
            viewProjection.get(matrix);
            matrix.flip();

            int loc = GL20.glGetUniformLocation(program, "uViewProjection");
            GL20.glUniformMatrix4fv(loc, false, matrix);
            Camera camera = event.getCamera();
            double cameraX = camera.getPosition().x;
            double cameraY = camera.getPosition().y;
            double cameraZ = camera.getPosition().z;
            GL20.glUniform1f(GL20.glGetUniformLocation(program, "uCameraY"),
                    (float) cameraY);
            GL20.glUniform1f(GL20.glGetUniformLocation(program, "uGridCells"),
                    gridCells);
            GL20.glUniform1f(GL20.glGetUniformLocation(program, "uHeightMin"),
                    MIN_HEIGHT);
            GL20.glUniform1f(GL20.glGetUniformLocation(program, "uHeightMax"),
                    MAX_HEIGHT);
            GL20.glUniform1f(GL20.glGetUniformLocation(program, "uSeaLevel"), 63.0f);
            GL20.glUniform1f(GL20.glGetUniformLocation(program, "uFogStart"), 6500.0f);
            GL20.glUniform1f(GL20.glGetUniformLocation(program, "uFogEnd"), 19000.0f);
            GL20.glUniform3f(GL20.glGetUniformLocation(program, "uFogColor"),
                    0.58f, 0.69f, 0.80f);
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "uHeightMap"), 0);

            GL30.glBindVertexArray(vao);
            for (int level = LEVEL_COUNT - 1; level >= 0; level--) {
                var tile = tiles.get(level);
                GL20.glUniform2f(GL20.glGetUniformLocation(program, "uOrigin"),
                        (float) (tile.originX() - cameraX),
                        (float) (tile.originZ() - cameraZ));
                GL20.glUniform1f(GL20.glGetUniformLocation(program, "uSpacing"),
                        tile.cellSpacingBlocks());
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures[level]);
                GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER,
                        elementBuffers[level]);
                GL11.glDrawElements(GL11.GL_TRIANGLES, indexCounts[level],
                        GL11.GL_UNSIGNED_INT, 0L);
            }
            GL30.glBindVertexArray(0);
        } finally {
            state.restore();
        }
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

    private record State(int program, int vao, int arrayBuffer,
            int activeTexture, int texture0, int depthFunc,
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
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            RenderSystem.bindTexture(texture0);
            RenderSystem.activeTexture(activeTexture);
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
