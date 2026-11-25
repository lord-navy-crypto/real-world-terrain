package org.realscale.neoforge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

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
        implements DensityFunction.SimpleFunction {
    public static final MapCodec<RangeMinDensity> MAP_CODEC =
            RecordCodecBuilder.mapCodec(i -> i.group(
                DensityFunction.HOLDER_HELPER_CODEC.fieldOf("base").forGetter(RangeMinDensity::base),
                DensityFunction.HOLDER_HELPER_CODEC.fieldOf("cave").forGetter(RangeMinDensity::cave),
                Codec.DOUBLE.fieldOf("min_inclusive").forGetter(RangeMinDensity::minInclusive),
                Codec.DOUBLE.fieldOf("max_exclusive").forGetter(RangeMinDensity::maxExclusive)
            ).apply(i, RangeMinDensity::new));
    private static final KeyDispatchDataCodec<RangeMinDensity> DISPATCH =
            KeyDispatchDataCodec.of(MAP_CODEC);

    @Override
    public double compute(DensityFunction.FunctionContext ctx) {
        double first = base.compute(ctx);
        if (first >= minInclusive && first < maxExclusive) {
            // Return Math.min exactly as Minecraft:min does, including
            // negative zero. Do not interpolate, clamp or change cave noise.
            return Math.min(first, cave.compute(ctx));
        }
        return first;
    }

    @Override
    public void fillArray(double[] values, DensityFunction.ContextProvider context) {
        // Compute precisely once per context. Avoid default codec visitor
        // double evaluation and never assume samples are aligned to Y.
        context.fillAllDirectly(values, this);
    }

    @Override public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply(new RangeMinDensity(base.mapAll(visitor),
                cave.mapAll(visitor), minInclusive, maxExclusive));
    }
    @Override public double minValue() {
        return Math.min(base.minValue(), cave.minValue());
    }
    @Override public double maxValue() { return base.maxValue(); }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return DISPATCH;
    }
}
