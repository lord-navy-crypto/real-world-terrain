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
    private static int realscale$smokeCalls;
    private static boolean realscale$extensionReported;

    @Inject(method = "getRenderDistanceInBlocks", at = @At("RETURN"),
            cancellable = true, require = 0, remap = false)
    private static void realscale$extendShaderFar(CallbackInfoReturnable<Integer> cir) {
        int original = cir.getReturnValueI();
        int far = GeometryClipmapClient.irisFarDistanceBlocks(original);
        if (Boolean.getBoolean("realscale.experimental.geometryclipmap.smokeCamera")) {
            if (realscale$smokeCalls < 3) {
                System.out.printf(java.util.Locale.ROOT,
                        "[RealScale Iris Clipmap] IRIS_CAMERA_FAR_MIXIN_HIT original=%d returned=%d%n",
                        original, far);
                realscale$smokeCalls++;
            }
            if (far > original && !realscale$extensionReported) {
                realscale$extensionReported = true;
                System.out.printf(java.util.Locale.ROOT,
                        "[RealScale Iris Clipmap] IRIS_CAMERA_FAR_EXTENDED original=%d extended=%d%n",
                        original, far);
            }
        }
        if (far != original) cir.setReturnValue(far);
    }
}
