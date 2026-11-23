package org.realscale.neoforge.client.clipmap;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import net.neoforged.fml.ModList;

/**
 * Optional Iris bridge that deliberately has no compile-time Iris dependency.
 *
 * <p>Iris 1.21.1 exposes shader-pack and shadow-pass state through IrisApi v0.
 * The clipmap uses that small optional bridge only for routing: with a shader
 * pack active, geometry is submitted through vanilla RenderType passes while
 * Iris's G-buffer program is already selected. No Iris classes are linked at
 * compile time.
 */
public final class IrisRenderCompatibility {
    private static final String IRIS_API =
            "net.irisshaders.iris.api.v0.IrisApi";
    private static final ApiAccess API = ApiAccess.discover();
    private static final ProjectionAccess PROJECTION = ProjectionAccess.discover();

    private IrisRenderCompatibility() {}

    /**
     * Resolve optional Iris classes through the active mod-launcher context first.
     * The defining class loader may not see another mod's isolated class space.
     */
    private static Class<?> loadIrisClass(String name) throws ClassNotFoundException {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        if (context != null) {
            try {
                return Class.forName(name, false, context);
            } catch (ClassNotFoundException ignored) {
                // Try RealScale's mod class loader below.
            }
        }
        return Class.forName(name, false, IrisRenderCompatibility.class.getClassLoader());
    }

    /**
     * If Iris is installed but its API is not visible, treat that as unknown
     * rather than as no Iris; unknown state must never enable raw GL drawing.
     */
    private static boolean irisModLoaded() {
        try {
            return ModList.get().isLoaded("iris");
        } catch (RuntimeException | LinkageError unavailable) {
            // During an unusually early class-load, prefer the safe disabled path.
            return true;
        }
    }

    public static State current() {
        if (!API.present()) {
            return new State(Mode.NO_IRIS, false);
        }
        if (!API.available()) {
            return new State(Mode.API_UNAVAILABLE, false);
        }
        try {
            boolean shaderPackInUse = (boolean) API.shaderPackInUse().invoke(API.instance());
            boolean shadowPass = shaderPackInUse
                    && (boolean) API.renderingShadowPass().invoke(API.instance());
            return new State(shaderPackInUse
                    ? Mode.SHADERPACK_ACTIVE : Mode.IRIS_NO_SHADERPACK, shadowPass);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            return new State(Mode.API_UNAVAILABLE, false);
        }
    }

    /**
     * Temporarily overrides Iris's captured G-buffer projection. Iris shader
     * packs consume gbufferProjection from CapturedRenderingState rather than
     * only ShaderInstance.PROJECTION_MATRIX, so changing the vanilla uniform
     * alone would still leave the shader-pack reconstruction matrix stale.
     *
     * <p>Returns null when the pinned Iris 1.21.1 bridge is unavailable.
     */
    public static boolean hasNativeProjectionBridge() {
        return PROJECTION.available();
    }

    /**
     * Iris compiles its vanilla terrain ShaderInstances against this extended
     * format even when Sodium is installed. The clipmap's Iris-native VBOs must
     * use the same format so UV2/lightmap and Iris terrain attributes line up.
     */
    public static VertexFormat terrainVertexFormat() {
        try {
            Class<?> formats = loadIrisClass("net.irisshaders.iris.vertices.IrisVertexFormats");
            Object format = formats.getField("TERRAIN").get(null);
            return format instanceof VertexFormat vertexFormat ? vertexFormat : null;
        } catch (ClassNotFoundException absent) {
            return null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return null;
        }
    }

    public static ProjectionScope overrideGbufferProjection(Matrix4f projection) {
        if (projection == null || !PROJECTION.available()) return null;
        try {
            Object previous = PROJECTION.getter().invoke(PROJECTION.instance());
            if (!(previous instanceof Matrix4fc matrix)) return null;
            Matrix4f original = new Matrix4f(matrix);
            PROJECTION.setter().invoke(PROJECTION.instance(), new Matrix4f(projection));
            return new ProjectionScope(PROJECTION.instance(), PROJECTION.setter(), original);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            return null;
        }
    }

    public static final class ProjectionScope implements AutoCloseable {
        private final Object instance;
        private final Method setter;
        private final Matrix4f original;
        private boolean closed;

        private ProjectionScope(Object instance, Method setter, Matrix4f original) {
            this.instance = instance;
            this.setter = setter;
            this.original = original;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            try {
                setter.invoke(instance, original);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                System.err.println("[RealScale Iris Clipmap] failed to restore Iris G-buffer projection: "
                        + failure.getClass().getSimpleName());
            }
        }
    }

    public enum Mode {
        NO_IRIS,
        IRIS_NO_SHADERPACK,
        SHADERPACK_ACTIVE,
        API_UNAVAILABLE
    }

    public record State(Mode mode, boolean shadowPass) {
        public State {
            Objects.requireNonNull(mode, "mode");
        }

        /**
         * The native terrain-stage path is enabled only when Iris confirms an
         * active shader pack. It intentionally remains available during Iris
         * shadow rendering so distant terrain can cast and receive shadows.
         */
        public boolean allowsIrisNativeRenderer() {
            return mode == Mode.SHADERPACK_ACTIVE;
        }

        /**
         * Standalone GLSL is safe only without an active Iris pack and outside
         * the shadow pass. Unknown API state fails closed.
         */
        public boolean allowsRawOpenGlRenderer() {
            return !shadowPass
                    && (mode == Mode.NO_IRIS || mode == Mode.IRIS_NO_SHADERPACK);
        }
    }

    private record ProjectionAccess(Object instance, Method getter, Method setter) {
        private boolean available() {
            return instance != null && getter != null && setter != null;
        }

        private static ProjectionAccess discover() {
            try {
                Class<?> captured = loadIrisClass(
                        "net.irisshaders.iris.uniforms.CapturedRenderingState");
                Field instanceField = captured.getField("INSTANCE");
                Object instance = instanceField.get(null);
                Method getter = captured.getMethod("getGbufferProjection");
                Method setter = captured.getMethod("setGbufferProjection", Matrix4f.class);
                return new ProjectionAccess(instance, getter, setter);
            } catch (ClassNotFoundException absent) {
                return new ProjectionAccess(null, null, null);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
                return new ProjectionAccess(null, null, null);
            }
        }
    }

    private record ApiAccess(boolean present, Object instance,
            Method shaderPackInUse, Method renderingShadowPass) {
        private boolean available() {
            return instance != null && shaderPackInUse != null && renderingShadowPass != null;
        }

        private static ApiAccess discover() {
            try {
                Class<?> api = loadIrisClass(IRIS_API);
                Object instance = api.getMethod("getInstance").invoke(null);
                Method shaderPackInUse = api.getMethod("isShaderPackInUse");
                Method renderingShadowPass = api.getMethod("isRenderingShadowPass");
                return new ApiAccess(true, instance, shaderPackInUse, renderingShadowPass);
            } catch (ClassNotFoundException absent) {
                return new ApiAccess(irisModLoaded(), null, null, null);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
                return new ApiAccess(true, null, null, null);
            }
        }
    }
}
