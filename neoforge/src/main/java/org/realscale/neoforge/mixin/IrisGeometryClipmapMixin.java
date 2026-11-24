package org.realscale.neoforge.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.realscale.neoforge.client.clipmap.GeometryClipmapClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prepares the extended Iris fog range at the start of each terrain layer.
 * Native clipmap submission is dispatched from NeoForge's render-stage events:
 * Sodium overwrites renderSectionLayer and does not retain vanilla's
 * ShaderInstance.clear invocation, so that bytecode location is not portable.
 */
@Mixin(LevelRenderer.class)
public abstract class IrisGeometryClipmapMixin {
    @Inject(
            method = "renderSectionLayer",
            at = @At("HEAD"),
            require = 1
    )
    private void realscale$prepareIrisFrameFog(RenderType renderType,
            double cameraX, double cameraY, double cameraZ,
            Matrix4f frustumMatrix, Matrix4f projectionMatrix,
            CallbackInfo callbackInfo) {
        GeometryClipmapClient.prepareIrisFrameFog();
    }

}
