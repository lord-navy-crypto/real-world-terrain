package org.realscale.neoforge.mixin;

import java.util.Queue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.realscale.neoforge.ClientChunkUploadBudget;
import org.realscale.neoforge.RealScaleChunkUploadClient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Default DISABLED. In the opt-in physical client, timeslice the ORIGINAL
 * pending upload Runnables without changing order, content or chunk radius.
 * Interception falls back to vanilla outside an active gameplay render frame.
 */
@Mixin(value=SectionRenderDispatcher.class,remap=false)
public abstract class VanillaSectionUploadBudgetMixin {
    @Shadow @Final private Queue<Runnable> toUpload;

    @Inject(method="uploadAllPendingUploads",at=@At("HEAD"),
            cancellable=true,remap=false)
    private void realscale$limitPendingUploadFrame(CallbackInfo callback) {
        ClientChunkUploadBudget budget=RealScaleChunkUploadClient.BUDGET;
        if(!budget.active())return;
        // Never interfere with initialization, menus or pause transitions.
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null||mc.screen!=null)return;
        if(toUpload.isEmpty())return;
        int drained=0;
        while(budget.canTakeTask()){
            Runnable pending=toUpload.poll();
            if(pending==null)break;
            long begin=System.nanoTime();
            try{
                pending.run(); // Exactly the original vanilla upload Runnable
            }finally{
                budget.completed(System.nanoTime()-begin);
            }
            drained++;
        }
        if(!toUpload.isEmpty())budget.recordDeferred();
        // No Runnable was discarded. The next frame resumes FIFO uploads.
        callback.cancel();
    }
}
