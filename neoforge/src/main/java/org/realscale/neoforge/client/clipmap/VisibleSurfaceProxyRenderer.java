package org.realscale.neoforge.client.clipmap;

import com.mojang.blaze3d.systems.RenderSystem;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL33;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

/** Exterior-only crossed-billboard renderer for distant feature proxies. */
final class VisibleSurfaceProxyRenderer {
    private static final String VERTEX = """
            #version 410 core
            layout(location=0) in vec2 aCorner;
            layout(location=1) in vec3 iPositionRelative;
            layout(location=2) in vec2 iShape;
            layout(location=3) in vec3 iColor;
            layout(location=4) in float iType;
            uniform mat4 uViewProjection;
            uniform vec3 uAxis;
            out vec2 vUv;
            out vec3 vColor;
            out float vType;
            out float vDistance;
            void main() {
                vec3 relative = iPositionRelative
                    + vec3(aCorner.x * iShape.x * uAxis.x,
                           aCorner.y * iShape.y,
                           aCorner.x * iShape.x * uAxis.z);
                vUv = vec2(aCorner.x * 0.5 + 0.5, aCorner.y);
                vColor = iColor;
                vType = iType;
                vDistance = length(relative.xz);
                gl_Position = uViewProjection * vec4(relative, 1.0);
            }
            """;

    private static final String FRAGMENT = """
            #version 410 core
            in vec2 vUv;
            in vec3 vColor;
            in float vType;
            in float vDistance;
            uniform float uFogStart;
            uniform float uFogEnd;
            uniform vec3 uFogColor;
            out vec4 fragColor;
            void main() {
                float x = abs(vUv.x - 0.5);
                vec3 color = vColor;
                if (vType < 1.5) {
                    float silhouette = 0.39 + 0.10 * sin(vUv.y * 5.2);
                    silhouette *= 1.0 - smoothstep(0.62, 1.0, vUv.y);
                    silhouette += 0.025 * sin(vUv.y * 25.0 + vUv.x * 8.0);
                    bool trunk = vUv.y < 0.35 && x < 0.065;
                    bool canopy = vUv.y >= 0.20 && x < max(0.025, silhouette);
                    if (!trunk && !canopy) discard;
                    float foliageShade = 0.80 + 0.20
                        * smoothstep(0.15, 0.90, vUv.y);
                    color = trunk ? vec3(0.28, 0.19, 0.10)
                        : vColor * foliageShade;
                } else if (vType < 2.5) {
                    float halfWidth = mix(0.22, 0.48, smoothstep(0.0, 0.55, vUv.y));
                    halfWidth *= 1.0 - 0.18 * smoothstep(0.72, 1.0, vUv.y);
                    if (x > halfWidth) discard;
                    color = vUv.y > 0.78
                        ? vec3(0.27, 0.39, 0.25)
                        : mix(vec3(0.31, 0.30, 0.27), vec3(0.48, 0.46, 0.40), vUv.y);
                } else if (vType < 3.5) {
                    float roofWidth = mix(0.46, 0.06, max(0.0, (vUv.y - 0.76) / 0.24));
                    if (x > roofWidth) discard;
                    float windowsX = fract(vUv.x * 6.0);
                    float windowsY = fract(vUv.y * 9.0);
                    bool window = vUv.y > 0.12 && vUv.y < 0.74
                        && windowsX > 0.20 && windowsX < 0.66
                        && windowsY > 0.22 && windowsY < 0.72;
                    color = window ? mix(vColor, vec3(0.13, 0.23, 0.29), 0.77)
                        : vColor * (vUv.y > 0.76 ? 0.72 : 1.0);
                } else {
                    float arch = 0.22 + 0.25 * sqrt(max(0.0, 1.0 - pow((vUv.x - 0.5) / 0.5, 2.0)));
                    bool rim = vUv.y < arch && vUv.y > arch - 0.12;
                    bool opening = vUv.y < arch - 0.12 && vUv.y > 0.06;
                    if (!rim && !opening) discard;
                    color = opening ? vec3(0.035, 0.035, 0.04) : vColor;
                }
                float fog = smoothstep(uFogStart, uFogEnd, vDistance);
                color = mix(color, uFogColor, fog);
                fragColor = vec4(color, 1.0);
            }
            """;

    private static final int MAX_PROXIES = 4096;
    private static final int INSTANCE_FLOATS = 9;
    private static final List<VisibleSurfaceProxy> VISIBLE_SCRATCH =
            new ArrayList<>(MAX_PROXIES);
    private static final FloatBuffer INSTANCE_UPLOAD =
            BufferUtils.createFloatBuffer(MAX_PROXIES * INSTANCE_FLOATS);
    private static final FloatBuffer MATRIX_UPLOAD = BufferUtils.createFloatBuffer(16);
    private static final Matrix4f MODEL_VIEW = new Matrix4f();
    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();

    private static int program;
    private static UniformLocations uniforms;
    private static int vao;
    private static int cornerBuffer;
    private static int instanceBuffer;

    private VisibleSurfaceProxyRenderer() {}

    static void draw(RenderLevelStageEvent event, List<VisibleSurfaceProxy> proxies,
            int nearChunks, float fogStart, float fogEnd) {
        if (proxies == null || proxies.isEmpty()) return;
        RenderSystem.assertOnRenderThread();

        var camera = event.getCamera().getPosition();
        List<VisibleSurfaceProxy> visible = VISIBLE_SCRATCH;
        visible.clear();
        double nearRadius = Math.max(0.0, nearChunks * 16.0 - 16.0);
        double nearRadiusSquared = nearRadius * nearRadius;
        // Proxies are fully fogged beyond this horizon. A small allowance
        // preserves the near edge of tall/wide silhouettes at the fade boundary.
        double farRadius = fogEnd + 64.0;
        double farRadiusSquared = farRadius * farRadius;
        for (VisibleSurfaceProxy proxy : proxies) {
            double dx = proxy.x() - camera.x;
            double dz = proxy.z() - camera.z;
            double distanceSquared = dx * dx + dz * dz;
            if (distanceSquared < nearRadiusSquared
                    || distanceSquared > farRadiusSquared) continue;
            if (event.getFrustum().isVisible(proxy.bounds())) visible.add(proxy);
        }
        if (visible.isEmpty()) return;

        ensureResources();
        FloatBuffer instances = INSTANCE_UPLOAD;
        instances.clear();
        for (VisibleSurfaceProxy proxy : visible) {
            instances.put((float) (proxy.x() - camera.x));
            instances.put((float) (proxy.y() - camera.y));
            instances.put((float) (proxy.z() - camera.z));
            instances.put(proxy.radius());
            instances.put(proxy.height());
            instances.put(((proxy.rgb() >>> 16) & 0xFF) / 255.0f);
            instances.put(((proxy.rgb() >>> 8) & 0xFF) / 255.0f);
            instances.put((proxy.rgb() & 0xFF) / 255.0f);
            instances.put(proxy.type());
        }
        instances.flip();

        Matrix4f modelView = MODEL_VIEW.set(event.getModelViewMatrix());
        modelView.setTranslation(0.0f, 0.0f, 0.0f);
        Matrix4f viewProjection = VIEW_PROJECTION.set(event.getProjectionMatrix())
                .mul(modelView);
        FloatBuffer matrix = MATRIX_UPLOAD;
        matrix.clear();
        viewProjection.get(matrix);
        matrix.flip();

        GL20.glUseProgram(program);
        GL20.glUniformMatrix4fv(uniforms.viewProjection(), false, matrix);
        GL20.glUniform1f(uniforms.fogStart(), fogStart);
        GL20.glUniform1f(uniforms.fogEnd(), fogEnd);
        GL20.glUniform3f(uniforms.fogColor(), 0.58f, 0.69f, 0.80f);

        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, instanceBuffer);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, instances, GL15.GL_STREAM_DRAW);
        int axisLocation = uniforms.axis();
        GL20.glUniform3f(axisLocation, 1.0f, 0.0f, 0.0f);
        GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, 6, visible.size());
        GL20.glUniform3f(axisLocation, 0.0f, 0.0f, 1.0f);
        GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, 6, visible.size());
        // Third view plane limits edge-on disappearance without full meshes.
        GL20.glUniform3f(axisLocation, 0.70710677f, 0.0f, 0.70710677f);
        GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, 6, visible.size());
        GL30.glBindVertexArray(0);
    }

    static void dispose() {
        RenderSystem.assertOnRenderThread();
        if (vao != 0) GL30.glDeleteVertexArrays(vao);
        if (cornerBuffer != 0) GL15.glDeleteBuffers(cornerBuffer);
        if (instanceBuffer != 0) GL15.glDeleteBuffers(instanceBuffer);
        if (program != 0) GL20.glDeleteProgram(program);
        vao = 0;
        cornerBuffer = 0;
        instanceBuffer = 0;
        program = 0;
        uniforms = null;
        VISIBLE_SCRATCH.clear();
    }

    private static void ensureResources() {
        if (program != 0) return;
        program = link(VERTEX, FRAGMENT);
        uniforms = UniformLocations.lookup(program);
        vao = GL30.glGenVertexArrays();
        cornerBuffer = GL15.glGenBuffers();
        instanceBuffer = GL15.glGenBuffers();

        FloatBuffer corners = BufferUtils.createFloatBuffer(12);
        corners.put(new float[]{
                -1.0f, 0.0f, 1.0f, 0.0f, 1.0f, 1.0f,
                -1.0f, 0.0f, 1.0f, 1.0f, -1.0f, 1.0f
        }).flip();

        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, cornerBuffer);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, corners, GL15.GL_STATIC_DRAW);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false,
                2 * Float.BYTES, 0L);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, instanceBuffer);
        int stride = 9 * Float.BYTES;
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false, stride, 0L);
        GL33.glVertexAttribDivisor(1, 1);
        GL20.glEnableVertexAttribArray(2);
        GL20.glVertexAttribPointer(2, 2, GL11.GL_FLOAT, false,
                stride, 3L * Float.BYTES);
        GL33.glVertexAttribDivisor(2, 1);
        GL20.glEnableVertexAttribArray(3);
        GL20.glVertexAttribPointer(3, 3, GL11.GL_FLOAT, false,
                stride, 5L * Float.BYTES);
        GL33.glVertexAttribDivisor(3, 1);
        GL20.glEnableVertexAttribArray(4);
        GL20.glVertexAttribPointer(4, 1, GL11.GL_FLOAT, false,
                stride, 8L * Float.BYTES);
        GL33.glVertexAttribDivisor(4, 1);
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }


    private record UniformLocations(int viewProjection, int axis,
            int fogStart, int fogEnd, int fogColor) {
        private static UniformLocations lookup(int program) {
            return new UniformLocations(
                    GL20.glGetUniformLocation(program, "uViewProjection"),
                    GL20.glGetUniformLocation(program, "uAxis"),
                    GL20.glGetUniformLocation(program, "uFogStart"),
                    GL20.glGetUniformLocation(program, "uFogEnd"),
                    GL20.glGetUniformLocation(program, "uFogColor"));
        }
    }

    private static int link(String vertexSource, String fragmentSource) {
        int vertex = compile(GL20.GL_VERTEX_SHADER, vertexSource);
        int fragment = compile(GL20.GL_FRAGMENT_SHADER, fragmentSource);
        int linked = GL20.glCreateProgram();
        GL20.glAttachShader(linked, vertex);
        GL20.glAttachShader(linked, fragment);
        GL20.glLinkProgram(linked);
        if (GL20.glGetProgrami(linked, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(linked);
            GL20.glDeleteProgram(linked);
            GL20.glDeleteShader(vertex);
            GL20.glDeleteShader(fragment);
            throw new IllegalStateException("Visible proxy shader link failed: " + log);
        }
        GL20.glDetachShader(linked, vertex);
        GL20.glDetachShader(linked, fragment);
        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);
        return linked;
    }

    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("Visible proxy shader compile failed: " + log);
        }
        return shader;
    }
}
