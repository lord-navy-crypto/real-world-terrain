package org.realscale.neoforge.mixin;

import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import org.realscale.neoforge.ExactPerlinNoiseMemo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Opt-in real vanilla PerlinNoise getValue(DDDDDZ)D exact argument memo.
 * Disabled by default. This has NO effect on the original noise formula,
 * seed, octave arrangement or interpolation: cache misses run vanilla.
 *
 * Profile-only mode instruments, but never returns a memoized result.
 */
@Mixin(value=PerlinNoise.class,remap=false)
public abstract class PerlinExactMemoMixin {
    @Unique private static final boolean realscale$CACHE=
            Boolean.getBoolean("realscale.experimental.perlin.cache");
    @Unique private static final boolean realscale$VERIFY=
            Boolean.getBoolean("realscale.experimental.perlin.verify");
    static{
        Runtime.getRuntime().addShutdownHook(new Thread(()->System.err.println(
                ExactPerlinNoiseMemo.report()),"realscale-perlin-cache-report"));
    }

    @Inject(method="getValue(DDDDDZ)D",at=@At("HEAD"),
            cancellable=true,remap=false)
    private void realscale$lookup(double x,double y,double z,
            double yScale,double yMax,boolean useOrigin,
            CallbackInfoReturnable<Double> returnInfo){
        ExactPerlinNoiseMemo memo=ExactPerlinNoiseMemo.current();
        if(memo.replaying())return;
        if(!realscale$CACHE){
            memo.countWithoutCache();
            return;
        }
        Object owner=this;
        if(!memo.get(owner,x,y,z,yScale,yMax,useOrigin))return;
        double cached=memo.value();
        if(realscale$VERIFY && memo.shouldReplayHit()){
            memo.setReplaying(true);
            double vanilla;
            try{
                vanilla=((PerlinNoise)owner).getValue(
                        x,y,z,yScale,yMax,useOrigin);
            }finally{
                memo.setReplaying(false);
            }
            memo.verifiedReplay(cached,vanilla);
        }
        returnInfo.setReturnValue(cached);
    }

    @Inject(method="getValue(DDDDDZ)D",at=@At("RETURN"),remap=false)
    private void realscale$store(double x,double y,double z,
            double yScale,double yMax,boolean useOrigin,
            CallbackInfoReturnable<Double> returnInfo){
        if(!realscale$CACHE)return;
        ExactPerlinNoiseMemo memo=ExactPerlinNoiseMemo.current();
        if(memo.replaying())return;
        memo.put(this,x,y,z,yScale,yMax,useOrigin,
                returnInfo.getReturnValue());
    }
}
