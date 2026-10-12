package org.realscale.neoforge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Opt-in repaired USGS Big Sur DEM profile (new worlds only).
 *
 * The measured 480 x 480 metre square remains 100% DEM-driven.
 * Beyond its edge, extrapolate the nearest source slope a bounded 128 m
 * then blend smoothly to the unmodified procedural fallback OUTSIDE the
 * source square. No coordinate is relative to chunk origin.
 *
 * A wider, smooth 2048 m transition is required because the small DEM tile\n * contains heights over 1 km above the unrelated procedural fallback.\n * A 768 m transition could make implausibly abrupt exterior escarpments.\n * Monotone bicubic interpolation keeps 32 m sample values, reduces pointy
 * grid-cell cusps, and cannot overshoot the surrounding four source samples.
 */
public record RealDemSeamlessDensity(DensityFunction base, int blendMeters)
        implements DensityFunction.SimpleFunction {
    public static final MapCodec<RealDemSeamlessDensity> MAP_CODEC =
        RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("base").forGetter(RealDemSeamlessDensity::base),
            Codec.intRange(256,4096).fieldOf("blend_meters").forGetter(RealDemSeamlessDensity::blendMeters)
        ).apply(i,RealDemSeamlessDensity::new));
    private static final KeyDispatchDataCodec<RealDemSeamlessDensity> DISPATCH =
        KeyDispatchDataCodec.of(MAP_CODEC);

    @Override public double compute(DensityFunction.FunctionContext ctx) {
        double x=ctx.blockX(),z=ctx.blockZ();
        double w=RealDemSeamlessMath.weightOutside(x,z,blendMeters);
        if (w<=0.0) return base.compute(ctx);
        double solid=(RealDemSeamlessMath.extendedHeight(x,z)-ctx.blockY())/128.0;
        if (w>=1.0) return solid;
        return solid*w + base.compute(ctx)*(1.0-w);
    }

    @Override public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply(new RealDemSeamlessDensity(base.mapAll(visitor),blendMeters));
    }
    @Override public double minValue(){return Math.min(-100.0,base.minValue());}
    @Override public double maxValue(){return Math.max(100.0,base.maxValue());}
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec(){return DISPATCH;}
}
