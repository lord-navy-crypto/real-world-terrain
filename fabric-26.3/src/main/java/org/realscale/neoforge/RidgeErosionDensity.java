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
 * Seed-sensitive, smooth terrain-height perturbation using existing noise
 * functions provided by the active world generator. This is a bounded,
 * geology-inspired approximation, NOT hydraulic erosion simulation.
 *
 * Density units: +/- 1 typically corresponds to ~128 blocks in the
 * existing RealScale density gradient. Keep both gains small.
 */
public record RidgeErosionDensity(DensityFunction base, DensityFunction ridgeSignal,
                                  DensityFunction erosionSignal, double ridgeGain,
                                  double erosionGain) implements DensityFunction {
    public static final MapCodec<RidgeErosionDensity> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        DensityFunction.CODEC.fieldOf("base").forGetter(RidgeErosionDensity::base),
        DensityFunction.CODEC.fieldOf("ridge_signal").forGetter(RidgeErosionDensity::ridgeSignal),
        DensityFunction.CODEC.fieldOf("erosion_signal").forGetter(RidgeErosionDensity::erosionSignal),
        Codec.doubleRange(0.0,0.32).fieldOf("ridge_gain").forGetter(RidgeErosionDensity::ridgeGain),
        Codec.doubleRange(0.0,0.24).fieldOf("erosion_gain").forGetter(RidgeErosionDensity::erosionGain)
    ).apply(i,RidgeErosionDensity::new));

    public double compute(RealScaleDensityContext ctx) {
        return GeoMath.ridgeErosion(ctx.compute(base),ctx.compute(ridgeSignal),
            ctx.compute(erosionSignal),ridgeGain,erosionGain);
    }
    
        @Override public DensitySampler compileSampler(DensityFunction.CompileContext context) {
        return RealScaleDensitySamplerSupport.compile(context, new DensityFunction[]{base, ridgeSignal, erosionSignal}, this::compute);
    }
    @Override public Interval range() { return Interval.INFINITE; }
    @Override public int domainAxes() { return DensityFunction.ALL_AXES; }

@Override public DensityFunction rewriteChildren(DfRewriteRule rule) {
        return new RidgeErosionDensity(rule.rewrite(base),
            rule.rewrite(ridgeSignal),rule.rewrite(erosionSignal),ridgeGain,erosionGain);
    }
    
    
    @Override public MapCodec<? extends DensityFunction> codec(){return MAP_CODEC;}
}
