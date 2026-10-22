package org.realscale.neoforge.mixin;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.realscale.neoforge.AquiferReadOnlyStats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Opt-in and observational. Runs AFTER all original vanilla aquifer code.
 * No cancellation, no return modifications, no state writes or fluid changes.
 */
@Mixin(value=Aquifer.NoiseBasedAquifer.class,remap=false)
public abstract class AquiferReadOnlyAuditMixin {
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(()->System.err.println(
                AquiferReadOnlyStats.report()),"realscale-aquifer-readonly-report"));
    }

    @Inject(method="computeSubstance",at=@At("RETURN"),remap=false)
    private void realscale$auditOriginalAquiferReturn(
            DensityFunction.FunctionContext context,double density,
            CallbackInfoReturnable<BlockState> cir){
        AquiferReadOnlyStats stats=AquiferReadOnlyStats.current();
        if(!stats.shouldSample())return;
        BlockState state=cir.getReturnValue();
        stats.record(this,context.blockX(),context.blockY(),context.blockZ(),
                density,state==null,state!=null&&state.isAir(),
                state!=null&&!state.getFluidState().isEmpty(),
                ((Aquifer)(Object)this).shouldScheduleFluidUpdate());
    }
}
