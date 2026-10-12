package org.realscale.neoforge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Low-amplitude vertically banded SOLIDITY. This is a shape-only geology
 * approximation. It does not change block material or ore strata.
 * The seed-dependent warp prevents perfectly horizontal repeated rings.
 */
public record StrataDensity(DensityFunction base,DensityFunction warp,
                            double strength,int period) implements DensityFunction.SimpleFunction {
    public static final MapCodec<StrataDensity> MAP_CODEC=RecordCodecBuilder.mapCodec(i -> i.group(
        DensityFunction.HOLDER_HELPER_CODEC.fieldOf("base").forGetter(StrataDensity::base),
        DensityFunction.HOLDER_HELPER_CODEC.fieldOf("warp").forGetter(StrataDensity::warp),
        Codec.doubleRange(0.0,0.06).fieldOf("strength").forGetter(StrataDensity::strength),
        Codec.intRange(24,128).fieldOf("period").forGetter(StrataDensity::period)
    ).apply(i,StrataDensity::new));
    private static final KeyDispatchDataCodec<StrataDensity> DISPATCH=KeyDispatchDataCodec.of(MAP_CODEC);

    static double band(double base,double warp,int y,double strength,int period) {
        double warpOffset=Math.max(-1,Math.min(1,warp))*(period/6.0);
        double phase=2.0*Math.PI*(y+warpOffset)/period;
        return base+Math.sin(phase)*strength;
    }
    @Override public double compute(DensityFunction.FunctionContext ctx) {
        return band(base.compute(ctx),warp.compute(ctx),ctx.blockY(),strength,period);
    }
    @Override public void fillArray(double[] samples,DensityFunction.ContextProvider contexts) {
        base.fillArray(samples,contexts);
        for(int i=0;i<samples.length;i++) {
            DensityFunction.FunctionContext ctx=contexts.forIndex(i);
            samples[i]=band(samples[i],warp.compute(ctx),ctx.blockY(),strength,period);
        }
    }
    @Override public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply(new StrataDensity(base.mapAll(visitor),warp.mapAll(visitor),strength,period));
    }
    @Override public double minValue(){return base.minValue()-strength;}
    @Override public double maxValue(){return base.maxValue()+strength;}
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec(){return DISPATCH;}
}
