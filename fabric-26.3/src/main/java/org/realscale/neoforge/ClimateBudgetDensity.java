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
 * Relative-climate cap for opt-in radial terrain only.
 * Clamps the combined new adjustments relative to the ORIGINAL world biome
 * temperature, preserving baseline humidity and unmodified temperature noise.
 * Limits how aggressively cold snowy biomes can invade mountain-dominated
 * worlds. Does NOT enforce a global biome area fraction or energy balance.
 */
public record ClimateBudgetDensity(DensityFunction original,DensityFunction adjusted,
                                  double maxCooling,double maxWarming)
        implements DensityFunction {
    public static final MapCodec<ClimateBudgetDensity> MAP_CODEC =
        RecordCodecBuilder.mapCodec(i->i.group(
            DensityFunction.CODEC.fieldOf("original").forGetter(ClimateBudgetDensity::original),
            DensityFunction.CODEC.fieldOf("adjusted").forGetter(ClimateBudgetDensity::adjusted),
            Codec.doubleRange(0.01,1.0).fieldOf("max_cooling").forGetter(ClimateBudgetDensity::maxCooling),
            Codec.doubleRange(0.05,0.8).fieldOf("max_warming").forGetter(ClimateBudgetDensity::maxWarming)
        ).apply(i,ClimateBudgetDensity::new));
    public double compute(RealScaleDensityContext ctx){
        return GeoMath.climateBudget(ctx.compute(original),ctx.compute(adjusted),maxCooling,maxWarming);
    }
        @Override public DensitySampler compileSampler(DensityFunction.CompileContext context) {
        return RealScaleDensitySamplerSupport.compile(context, new DensityFunction[]{original, adjusted}, this::compute);
    }
    @Override public Interval range() { return Interval.INFINITE; }
    @Override public int domainAxes() { return DensityFunction.ALL_AXES; }

@Override public DensityFunction rewriteChildren(DfRewriteRule rule){
        return new ClimateBudgetDensity(rule.rewrite(original),
            rule.rewrite(adjusted),maxCooling,maxWarming);
    }
    
    
    @Override public MapCodec<? extends DensityFunction> codec(){return MAP_CODEC;}
}
