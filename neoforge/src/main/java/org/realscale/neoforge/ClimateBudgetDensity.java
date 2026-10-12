package org.realscale.neoforge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Relative-climate cap for opt-in radial terrain only.
 * Clamps the combined new adjustments relative to the ORIGINAL world biome
 * temperature, preserving baseline humidity and unmodified temperature noise.
 * Limits how aggressively cold snowy biomes can invade mountain-dominated
 * worlds. Does NOT enforce a global biome area fraction or energy balance.
 */
public record ClimateBudgetDensity(DensityFunction original,DensityFunction adjusted,
                                  double maxCooling,double maxWarming)
        implements DensityFunction.SimpleFunction {
    public static final MapCodec<ClimateBudgetDensity> MAP_CODEC =
        RecordCodecBuilder.mapCodec(i->i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("original").forGetter(ClimateBudgetDensity::original),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("adjusted").forGetter(ClimateBudgetDensity::adjusted),
            Codec.doubleRange(0.05,1.0).fieldOf("max_cooling").forGetter(ClimateBudgetDensity::maxCooling),
            Codec.doubleRange(0.05,0.8).fieldOf("max_warming").forGetter(ClimateBudgetDensity::maxWarming)
        ).apply(i,ClimateBudgetDensity::new));
    private static final KeyDispatchDataCodec<ClimateBudgetDensity> DISPATCH =
        KeyDispatchDataCodec.of(MAP_CODEC);

    @Override public double compute(DensityFunction.FunctionContext ctx){
        return GeoMath.climateBudget(original.compute(ctx),adjusted.compute(ctx),maxCooling,maxWarming);
    }
    @Override public DensityFunction mapAll(DensityFunction.Visitor visitor){
        return visitor.apply(new ClimateBudgetDensity(original.mapAll(visitor),
            adjusted.mapAll(visitor),maxCooling,maxWarming));
    }
    @Override public double minValue(){return original.minValue()-maxCooling;}
    @Override public double maxValue(){return original.maxValue()+maxWarming;}
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec(){return DISPATCH;}
}
