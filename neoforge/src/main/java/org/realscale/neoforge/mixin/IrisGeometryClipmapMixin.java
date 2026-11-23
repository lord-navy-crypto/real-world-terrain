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
 * Adds RealScale's distant surface mesh while the active vanilla/Iris terrain
 * shader and G-buffer are still bound. Iris selects its solid/cutout/translucent
 * phase at renderSectionLayer HEAD and resets it only after this method returns.
 */
@Mixin(LevelRenderer.class)
public abstract class IrisGeometryClipmapMixin {
    @Inject(
            method = "renderSectionLayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ShaderInstance;clear()V",
                    shift = At.Shift.BEFORE
            ),
            require = 1
    )
    private void realscale$drawIrisNativeClipmap(RenderType renderType,
            double cameraX, double cameraY, double cameraZ,
            Matrix4f frustumMatrix, Matrix4f projectionMatrix,
            CallbackInfo callbackInfo) {
        GeometryClipmapClient.renderIrisNativeLayer(renderType,
                cameraX, cameraY, cameraZ);
    }
}
