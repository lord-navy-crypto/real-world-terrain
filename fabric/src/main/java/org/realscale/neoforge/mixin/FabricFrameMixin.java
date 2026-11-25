package org.realscale.neoforge.mixin;
import net.minecraft.client.Minecraft;
import org.realscale.neoforge.RealScaleClientFrameProfiler;
import org.realscale.neoforge.RealScaleGpuTimestampProfiler;
import org.realscale.neoforge.RealScaleChunkUploadClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class FabricFrameMixin {
    @Inject(method="runTick", at=@At("HEAD"))
    private void realscale$frameStart(boolean renderLevel, CallbackInfo ci) {
        RealScaleGpuTimestampProfiler.onFramePre();
        RealScaleChunkUploadClient.onPre();
    }
    @Inject(method="runTick", at=@At("RETURN"))
    private void realscale$frameEnd(boolean renderLevel, CallbackInfo ci) {
        RealScaleChunkUploadClient.onPost();
        RealScaleGpuTimestampProfiler.onFramePost();
        RealScaleClientFrameProfiler.onFrame();
    }
}
