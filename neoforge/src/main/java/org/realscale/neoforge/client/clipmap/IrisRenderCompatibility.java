package org.realscale.neoforge.client.clipmap;

import java.lang.reflect.Method;
import java.util.Objects;

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

    private IrisRenderCompatibility() {}

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

    private record ApiAccess(boolean present, Object instance,
            Method shaderPackInUse, Method renderingShadowPass) {
        private boolean available() {
            return instance != null && shaderPackInUse != null && renderingShadowPass != null;
        }

        private static ApiAccess discover() {
            try {
                ClassLoader loader = IrisRenderCompatibility.class.getClassLoader();
                Class<?> api = Class.forName(IRIS_API, false, loader);
                Object instance = api.getMethod("getInstance").invoke(null);
                Method shaderPackInUse = api.getMethod("isShaderPackInUse");
                Method renderingShadowPass = api.getMethod("isRenderingShadowPass");
                return new ApiAccess(true, instance, shaderPackInUse, renderingShadowPass);
            } catch (ClassNotFoundException absent) {
                return new ApiAccess(false, null, null, null);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
                return new ApiAccess(true, null, null, null);
            }
        }
    }
}
