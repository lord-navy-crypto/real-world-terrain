package org.realscale.neoforge.client.clipmap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class IrisRenderCompatibilityTest {
    @Test
    void standaloneRendererRunsWithoutIrisOrWithIrisButNoShaderPack() {
        assertTrue(new IrisRenderCompatibility.State(
                IrisRenderCompatibility.Mode.NO_IRIS, false)
                .allowsRawOpenGlRenderer());
        assertTrue(new IrisRenderCompatibility.State(
                IrisRenderCompatibility.Mode.IRIS_NO_SHADERPACK, false)
                .allowsRawOpenGlRenderer());
    }

    @Test
    void standaloneRendererNeverWritesIntoActiveShaderPackOrShadowPass() {
        assertFalse(new IrisRenderCompatibility.State(
                IrisRenderCompatibility.Mode.SHADERPACK_ACTIVE, false)
                .allowsRawOpenGlRenderer());
        assertFalse(new IrisRenderCompatibility.State(
                IrisRenderCompatibility.Mode.SHADERPACK_ACTIVE, true)
                .allowsRawOpenGlRenderer());
        assertFalse(new IrisRenderCompatibility.State(
                IrisRenderCompatibility.Mode.IRIS_NO_SHADERPACK, true)
                .allowsRawOpenGlRenderer());
    }

    @Test
    void nativeRendererRunsOnlyWithAnActiveShaderPackIncludingShadowPasses() {
        assertTrue(new IrisRenderCompatibility.State(
                IrisRenderCompatibility.Mode.SHADERPACK_ACTIVE, false)
                .allowsIrisNativeRenderer());
        assertTrue(new IrisRenderCompatibility.State(
                IrisRenderCompatibility.Mode.SHADERPACK_ACTIVE, true)
                .allowsIrisNativeRenderer());
        assertFalse(new IrisRenderCompatibility.State(
                IrisRenderCompatibility.Mode.NO_IRIS, false)
                .allowsIrisNativeRenderer());
        assertFalse(new IrisRenderCompatibility.State(
                IrisRenderCompatibility.Mode.IRIS_NO_SHADERPACK, false)
                .allowsIrisNativeRenderer());
        assertFalse(new IrisRenderCompatibility.State(
                IrisRenderCompatibility.Mode.API_UNAVAILABLE, false)
                .allowsIrisNativeRenderer());
    }

    @Test
    void unknownIrisApiStateFailsClosed() {
        assertFalse(new IrisRenderCompatibility.State(
                IrisRenderCompatibility.Mode.API_UNAVAILABLE, false)
                .allowsRawOpenGlRenderer());
    }
}
