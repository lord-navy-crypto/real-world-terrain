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
        implements DensityFunction {
    public static final MapCodec<RealDemSeamlessDensity> MAP_CODEC =
        RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.CODEC.fieldOf("base").forGetter(RealDemSeamlessDensity::base),
            Codec.intRange(256,4096).fieldOf("blend_meters").forGetter(RealDemSeamlessDensity::blendMeters)
        ).apply(i,RealDemSeamlessDensity::new));
    public double compute(RealScaleDensityContext ctx) {
        if(!DensityPerfSampler.ENABLED)return computeUnprofiled(ctx);
        long started=DensityPerfSampler.begin(DensityPerfSampler.Stage.REAL_DEM_SEAMLESS);
        try{return computeUnprofiled(ctx);}
        finally{DensityPerfSampler.finish(DensityPerfSampler.Stage.REAL_DEM_SEAMLESS,started);}
    }

    private double computeUnprofiled(RealScaleDensityContext ctx) {
        int x=ctx.blockX(),z=ctx.blockZ();
        // The fade is exactly zero outside its circular support. Reject these
        // world columns before creating/probing a large per-worker DEM cache.
        if(RealDemSeamlessMath.outsideFadeRegion(x,z,blendMeters))
            return ctx.compute(base);
        RealDemSeamlessMath.ColumnSample sample=
                RealDemSeamlessMath.cachedColumnSample(x,z,blendMeters);
        double w=sample.weight;
        if (w<=0.0) return ctx.compute(base);
        double solid=(sample.height-ctx.blockY())/128.0;
        if (w>=1.0) return solid;
        return solid*w + ctx.compute(base)*(1.0-w);
    }

        @Override public DensitySampler compileSampler(DensityFunction.CompileContext context) {
        return RealScaleDensitySamplerSupport.compile(context, new DensityFunction[]{base}, this::compute);
    }
    @Override public Interval range() { return Interval.INFINITE; }
    @Override public int domainAxes() { return DensityFunction.ALL_AXES; }

@Override public DensityFunction rewriteChildren(DfRewriteRule rule) {
        return new RealDemSeamlessDensity(rule.rewrite(base),blendMeters);
    }
    
    
    @Override public MapCodec<? extends DensityFunction> codec(){return MAP_CODEC;}
}
