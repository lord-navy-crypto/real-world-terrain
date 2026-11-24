package org.realscale.neoforge.mixin;

import org.realscale.neoforge.client.clipmap.GeometryClipmapClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional Iris bridge for the shader-pack far uniform. Vanilla chunk radius is untouched. */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.uniforms.CameraUniforms", remap = false)
public abstract class IrisCameraFarUniformMixin {
    @Inject(method = "getRenderDistanceInBlocks", at = @At("RETURN"),
            cancellable = true, require = 0, remap = false)
    private static void realscale$extendShaderFar(CallbackInfoReturnable<Integer> cir) {
        int original = cir.getReturnValueI();
        int far = GeometryClipmapClient.irisFarDistanceBlocks(original);
        if (far != original) cir.setReturnValue(far);
    }
}
