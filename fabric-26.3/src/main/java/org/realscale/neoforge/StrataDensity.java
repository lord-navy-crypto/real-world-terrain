package org.realscale.neoforge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.util.Interval;

/**
 * Low-amplitude vertically banded SOLIDITY. This is a shape-only geology
 * approximation. It does not change block material or ore strata.
 * The seed-dependent warp prevents perfectly horizontal repeated rings.
 */
public record StrataDensity(DensityFunction base,DensityFunction warp,
                            double strength,int period) implements DensityFunction {
    public static final MapCodec<StrataDensity> MAP_CODEC=RecordCodecBuilder.mapCodec(i -> i.group(
        DensityFunction.CODEC.fieldOf("base").forGetter(StrataDensity::base),
        DensityFunction.CODEC.fieldOf("warp").forGetter(StrataDensity::warp),
        Codec.doubleRange(0.0,0.06).fieldOf("strength").forGetter(StrataDensity::strength),
        Codec.intRange(24,128).fieldOf("period").forGetter(StrataDensity::period)
    ).apply(i,StrataDensity::new));
    public double compute(RealScaleDensityContext ctx) {
        return GeoMath.strata(ctx.compute(base),ctx.compute(warp),ctx.blockY(),strength,period);
    }
    
        @Override public DensitySampler compileSampler(DensityFunction.CompileContext context) {
        return RealScaleDensitySamplerSupport.compile(context, new DensityFunction[]{base, warp}, this::compute);
    }
    @Override public Interval range() { return Interval.INFINITE; }
    @Override public int domainAxes() { return DensityFunction.ALL_AXES; }

@Override public DensityFunction rewriteChildren(DfRewriteRule rule) {
        return new StrataDensity(rule.rewrite(base),rule.rewrite(warp),strength,period);
    }
    
    
    @Override public MapCodec<? extends DensityFunction> codec(){return MAP_CODEC;}
}
