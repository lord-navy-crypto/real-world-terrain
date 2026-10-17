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

    @Override public double compute(DensityFunction.FunctionContext ctx) {
        return GeoMath.strata(base.compute(ctx),warp.compute(ctx),ctx.blockY(),strength,period);
    }
    @Override public void fillArray(double[] samples,DensityFunction.ContextProvider contexts) {
        base.fillArray(samples,contexts);
        for(int i=0;i<samples.length;i++) {
            DensityFunction.FunctionContext ctx=contexts.forIndex(i);
            samples[i]=GeoMath.strata(samples[i],warp.compute(ctx),ctx.blockY(),strength,period);
        }
    }
    @Override public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply(new StrataDensity(base.mapAll(visitor),warp.mapAll(visitor),strength,period));
    }
    @Override public double minValue(){return base.minValue()-strength;}
    @Override public double maxValue(){return base.maxValue()+strength;}
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec(){return DISPATCH;}
}
