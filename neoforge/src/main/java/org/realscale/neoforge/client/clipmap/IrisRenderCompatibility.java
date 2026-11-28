package org.realscale.neoforge.client.clipmap;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import net.minecraft.world.level.block.state.BlockState;
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
    private static final BlockContextAccess BLOCK_CONTEXT = BlockContextAccess.discover();
    private static boolean blockContextWarningReported;

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

    /**
     * Resolve a real Iris shader-pack block-state ID. Iris does not use the
     * vanilla registry ID for mc_Entity; it uses WorldRenderingSettings' map.
     * A missing map is treated as unavailable rather than inventing an ID.
     */
    public static int blockStateId(BlockState state) {
        if (state == null || !BLOCK_CONTEXT.available()) return -1;
        try {
            Object ids = BLOCK_CONTEXT.blockStateIds().invoke(BLOCK_CONTEXT.settingsInstance());
            if (ids == null) {
                warnBlockContextOnce("Iris has not published a block-state ID map");
                return -1;
            }
            Method getInt = ids.getClass().getMethod("getInt", Object.class);
            Object value = getInt.invoke(ids, state);
            return value instanceof Number number ? number.intValue() : -1;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            warnBlockContextOnce("could not resolve Iris block-state ID: "
                    + failure.getClass().getSimpleName());
            return -1;
        }
    }

    /**
     * Populate Iris's mc_Entity and mc_midBlock attributes for one synthetic
     * terrain vertex. The position is in the same clipmap-centred coordinates
     * as the vertex being emitted, so its mid-block offset stays local.
     */
    public static boolean beginBlock(Object builder, int blockId, byte renderType,
            int localX, int localY, int localZ) {
        if (blockId < 0 || !BLOCK_CONTEXT.supports(builder)) return false;
        try {
            BLOCK_CONTEXT.beginBlock().invoke(builder, blockId, renderType,
                    (byte) 0, localX, localY, localZ);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            warnBlockContextOnce("could not set Iris terrain block metadata: "
                    + failure.getClass().getSimpleName());
            return false;
        }
    }

    public static void endBlock(Object builder) {
        if (!BLOCK_CONTEXT.supports(builder)) return;
        try {
            BLOCK_CONTEXT.endBlock().invoke(builder);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            warnBlockContextOnce("could not clear Iris terrain block metadata: "
                    + failure.getClass().getSimpleName());
        }
    }

    private static void warnBlockContextOnce(String message) {
        if (blockContextWarningReported) return;
        blockContextWarningReported = true;
        System.err.println("[RealScale Iris Clipmap] " + message
                + "; continuing with neutral fallback metadata");
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

    private record BlockContextAccess(Class<?> builderType, Object settingsInstance,
            Method blockStateIds, Method beginBlock, Method endBlock) {
        private boolean available() {
            return builderType != null && settingsInstance != null
                    && blockStateIds != null && beginBlock != null && endBlock != null;
        }

        private boolean supports(Object builder) {
            return available() && builder != null && builderType.isInstance(builder);
        }

        private static BlockContextAccess discover() {
            try {
                Class<?> builderType = loadIrisClass(
                        "net.irisshaders.iris.vertices.BlockSensitiveBufferBuilder");
                Class<?> settingsType = loadIrisClass(
                        "net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings");
                Object settings = settingsType.getField("INSTANCE").get(null);
                Method blockStateIds = settingsType.getMethod("getBlockStateIds");
                Method beginBlock = builderType.getMethod("beginBlock",
                        int.class, byte.class, byte.class,
                        int.class, int.class, int.class);
                Method endBlock = builderType.getMethod("endBlock");
                return new BlockContextAccess(builderType, settings,
                        blockStateIds, beginBlock, endBlock);
            } catch (ClassNotFoundException absent) {
                return new BlockContextAccess(null, null, null, null, null);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
                return new BlockContextAccess(null, null, null, null, null);
            }
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
