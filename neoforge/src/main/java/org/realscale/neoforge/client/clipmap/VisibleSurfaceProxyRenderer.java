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
            uniform vec3 uCameraOffset;
            uniform float uNearRadiusSquared;
            uniform float uFarRadiusSquared;
            out vec2 vUv;
            out vec3 vColor;
            out float vType;
            out float vDistance;
            void main() {
                vec3 centerRelative = iPositionRelative - uCameraOffset;
                float centerDistanceSquared = dot(centerRelative.xz, centerRelative.xz);
                vUv = vec2(aCorner.x * 0.5 + 0.5, aCorner.y);
                vColor = iColor;
                vType = iType;
                // Cull each complete proxy by its centre to avoid slicing a
                // billboard when only one of its vertices crosses a radius.
                if (centerDistanceSquared < uNearRadiusSquared
                        || centerDistanceSquared > uFarRadiusSquared) {
                    vDistance = sqrt(centerDistanceSquared);
                    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
                    return;
                }
                vec3 relative = centerRelative
                    + vec3(aCorner.x * iShape.x * uAxis.x,
                           aCorner.y * iShape.y,
                           aCorner.x * iShape.x * uAxis.z);
                vDistance = length(relative.xz);
                // The hardware clipper handles the view frustum; instance
                // data stays centre-relative and is not rebuilt each frame.
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
            uniform float uLightLevel;
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
                color *= uLightLevel;
                float fog = smoothstep(uFogStart, uFogEnd, vDistance);
                color = mix(color, uFogColor, fog);
                fragColor = vec4(color, 1.0);
            }
            """;

    private static final int MAX_PROXIES = 4096;
    private static final int INSTANCE_FLOATS = 9;
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
    private static List<VisibleSurfaceProxy> uploadedProxies;
    private static long uploadedCenterX = Long.MIN_VALUE;
    private static long uploadedCenterZ = Long.MIN_VALUE;
    private static int uploadedCount;

    private VisibleSurfaceProxyRenderer() {}

    static void draw(RenderLevelStageEvent event, Matrix4f extendedProjection,
            List<VisibleSurfaceProxy> proxies, long centerX, long centerZ,
            int nearChunks, float fogStart, float fogEnd, float lightLevel) {
        RenderSystem.assertOnRenderThread();
        if (proxies == null || proxies.isEmpty()) {
            uploadedProxies = null;
            uploadedCount = 0;
            return;
        }

        ensureResources();
        if (uploadedProxies != proxies || uploadedCenterX != centerX
                || uploadedCenterZ != centerZ) {
            uploadInstances(proxies, centerX, centerZ);
        }
        if (uploadedCount == 0) return;

        var camera = event.getCamera().getPosition();
        Matrix4f modelView = MODEL_VIEW.set(event.getModelViewMatrix());
        modelView.setTranslation(0.0f, 0.0f, 0.0f);
        // Proxies must share the same far-plane extension as the terrain mesh;
        // using the vanilla projection here clipped trees/buildings at the
        // original view distance even while the distant ground remained visible.
        Matrix4f viewProjection = VIEW_PROJECTION.set(extendedProjection)
                .mul(modelView);
        FloatBuffer matrix = MATRIX_UPLOAD;
        matrix.clear();
        viewProjection.get(matrix);
        matrix.flip();

        double nearRadius = Math.max(0.0, nearChunks * 16.0 - 16.0);
        double farRadius = fogEnd + 64.0;
        GL20.glUseProgram(program);
        GL20.glUniformMatrix4fv(uniforms.viewProjection(), false, matrix);
        GL20.glUniform3f(uniforms.cameraOffset(),
                (float) (camera.x - centerX), (float) camera.y,
                (float) (camera.z - centerZ));
        GL20.glUniform1f(uniforms.nearRadiusSquared(),
                (float) (nearRadius * nearRadius));
        GL20.glUniform1f(uniforms.farRadiusSquared(),
                (float) (farRadius * farRadius));
        GL20.glUniform1f(uniforms.fogStart(), fogStart);
        GL20.glUniform1f(uniforms.fogEnd(), fogEnd);
        GL20.glUniform1f(uniforms.lightLevel(), lightLevel);
        ClipmapFogColor.upload(uniforms.fogColor());

        GL30.glBindVertexArray(vao);
        int axisLocation = uniforms.axis();
        GL20.glUniform3f(axisLocation, 1.0f, 0.0f, 0.0f);
        GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, 6, uploadedCount);
        GL20.glUniform3f(axisLocation, 0.0f, 0.0f, 1.0f);
        GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, 6, uploadedCount);
        // Third view plane limits edge-on disappearance without full meshes.
        GL20.glUniform3f(axisLocation, 0.70710677f, 0.0f, 0.70710677f);
        GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, 6, uploadedCount);
        GL30.glBindVertexArray(0);
    }

    /**
     * Instance positions are stored relative to the snapped clipmap centre.
     * They are uploaded once per immutable proxy snapshot, not once per frame.
     */
    private static void uploadInstances(List<VisibleSurfaceProxy> proxies,
            long centerX, long centerZ) {
        FloatBuffer instances = INSTANCE_UPLOAD;
        instances.clear();
        int count = 0;
        for (VisibleSurfaceProxy proxy : proxies) {
            if (count >= MAX_PROXIES) break;
            instances.put((float) ((long) proxy.x() - centerX));
            instances.put(proxy.y());
            instances.put((float) ((long) proxy.z() - centerZ));
            instances.put(proxy.radius());
            instances.put(proxy.height());
            instances.put(((proxy.rgb() >>> 16) & 0xFF) / 255.0f);
            instances.put(((proxy.rgb() >>> 8) & 0xFF) / 255.0f);
            instances.put((proxy.rgb() & 0xFF) / 255.0f);
            instances.put(proxy.type());
            count++;
        }
        instances.flip();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, instanceBuffer);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, instances, GL15.GL_STATIC_DRAW);
        uploadedProxies = proxies;
        uploadedCenterX = centerX;
        uploadedCenterZ = centerZ;
        uploadedCount = count;
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
        uploadedProxies = null;
        uploadedCenterX = Long.MIN_VALUE;
        uploadedCenterZ = Long.MIN_VALUE;
        uploadedCount = 0;
    }

    private static void ensureResources() {
        if (program != 0) return;
        try {
        program = link(VERTEX, FRAGMENT);
        uniforms = UniformLocations.lookup(program);
        vao = GL30.glGenVertexArrays();
        cornerBuffer = GL15.glGenBuffers();
        instanceBuffer = GL15.glGenBuffers();
        if (vao == 0 || cornerBuffer == 0 || instanceBuffer == 0) {
            throw new IllegalStateException("OpenGL failed to allocate surface-proxy buffers");
        }

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
        } catch (RuntimeException | Error failure) {
            dispose();
            throw failure;
        }
    }


    private record UniformLocations(int viewProjection, int axis,
            int cameraOffset, int nearRadiusSquared, int farRadiusSquared,
            int fogStart, int fogEnd, int fogColor, int lightLevel) {
        private static UniformLocations lookup(int program) {
            return new UniformLocations(
                    GL20.glGetUniformLocation(program, "uViewProjection"),
                    GL20.glGetUniformLocation(program, "uAxis"),
                    GL20.glGetUniformLocation(program, "uCameraOffset"),
                    GL20.glGetUniformLocation(program, "uNearRadiusSquared"),
                    GL20.glGetUniformLocation(program, "uFarRadiusSquared"),
                    GL20.glGetUniformLocation(program, "uFogStart"),
                    GL20.glGetUniformLocation(program, "uFogEnd"),
                    GL20.glGetUniformLocation(program, "uFogColor"),
                    GL20.glGetUniformLocation(program, "uLightLevel"));
        }
    }

    private static int link(String vertexSource, String fragmentSource) {
        int vertex = 0;
        int fragment = 0;
        int linked = 0;
        boolean vertexAttached = false;
        boolean fragmentAttached = false;
        try {
            vertex = compile(GL20.GL_VERTEX_SHADER, vertexSource);
            fragment = compile(GL20.GL_FRAGMENT_SHADER, fragmentSource);
            linked = GL20.glCreateProgram();
            if (linked == 0) {
                throw new IllegalStateException("OpenGL failed to allocate surface-proxy shader program");
            }
            GL20.glAttachShader(linked, vertex);
            vertexAttached = true;
            GL20.glAttachShader(linked, fragment);
            fragmentAttached = true;
            GL20.glLinkProgram(linked);
            if (GL20.glGetProgrami(linked, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                throw new IllegalStateException("Visible proxy shader link failed: "
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

    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type);
        if (shader == 0) {
            throw new IllegalStateException("OpenGL failed to allocate surface-proxy shader object");
        }
        try {
            GL20.glShaderSource(shader, source);
            GL20.glCompileShader(shader);
            if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
                throw new IllegalStateException("Visible proxy shader compile failed: "
                        + GL20.glGetShaderInfoLog(shader));
            }
            return shader;
        } catch (RuntimeException | Error failure) {
            GL20.glDeleteShader(shader);
            throw failure;
        }
    }
}
