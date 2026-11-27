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
 * EXACT fusion of:
 *
 *   range_choice(input=base, min=a, max=b,
 *       when_in_range=min(base, cave), when_out_of_range=base)
 *
 * In a 2096-high height sweep the original graph invokes base.compute
 * once to decide range and AGAIN to calculate min (or a third time via
 * the out-of-range branch). Large jigsaw structure searches amplify that.
 *
 * This fused node evaluates base EXACTLY ONCE at the SAME FunctionContext.
 * It is not a cached approximation, doesn't skip any cave, structure, or
 * surface, and preserves Java Math.min signed-zero and NaN semantics.
 */
public record RangeMinDensity(DensityFunction base, DensityFunction cave,
                              double minInclusive, double maxExclusive)
        implements DensityFunction {
    public static final MapCodec<RangeMinDensity> MAP_CODEC =
            RecordCodecBuilder.mapCodec(i -> i.group(
                DensityFunction.CODEC.fieldOf("base").forGetter(RangeMinDensity::base),
                DensityFunction.CODEC.fieldOf("cave").forGetter(RangeMinDensity::cave),
                Codec.DOUBLE.fieldOf("min_inclusive").forGetter(RangeMinDensity::minInclusive),
                Codec.DOUBLE.fieldOf("max_exclusive").forGetter(RangeMinDensity::maxExclusive)
            ).apply(i, RangeMinDensity::new));
    public double compute(RealScaleDensityContext ctx) {
        double first = ctx.compute(base);
        if (first >= minInclusive && first < maxExclusive) {
            // Return Math.min exactly as Minecraft:min does, including
            // negative zero. Do not interpolate, clamp or change cave noise.
            return Math.min(first, ctx.compute(cave));
        }
        return first;
    }

    

        @Override public DensitySampler compileSampler(DensityFunction.CompileContext context) {
        return RealScaleDensitySamplerSupport.compile(context, new DensityFunction[]{base, cave}, this::compute);
    }
    @Override public Interval range() { return Interval.INFINITE; }
    @Override public int domainAxes() { return DensityFunction.ALL_AXES; }

@Override public DensityFunction rewriteChildren(DfRewriteRule rule) {
        return new RangeMinDensity(rule.rewrite(base),
                rule.rewrite(cave), minInclusive, maxExclusive);
    }
    
    
    @Override public MapCodec<? extends DensityFunction> codec() {
        return MAP_CODEC;
    }
}
