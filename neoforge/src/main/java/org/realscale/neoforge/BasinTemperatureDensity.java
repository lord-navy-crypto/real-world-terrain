package org.realscale.neoforge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Optional bounded biome-climate basin proxy. Eight height-signal samples
 * approximate a closed rim; this does not alter heightmaps or create basins.
 * Wind, precipitation and nocturnal inversion are not simulated.
 */
public record BasinTemperatureDensity(DensityFunction base,DensityFunction heightSignal,
                                      DensityFunction continents,double warmth,int radius)
        implements DensityFunction.SimpleFunction {
    public static final MapCodec<BasinTemperatureDensity> MAP_CODEC =
        RecordCodecBuilder.mapCodec(i->i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("base").forGetter(BasinTemperatureDensity::base),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("height_signal").forGetter(BasinTemperatureDensity::heightSignal),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("continents").forGetter(BasinTemperatureDensity::continents),
            Codec.doubleRange(0,0.35).fieldOf("warmth").forGetter(BasinTemperatureDensity::warmth),
            Codec.intRange(48,512).fieldOf("radius").forGetter(BasinTemperatureDensity::radius)
        ).apply(i,BasinTemperatureDensity::new));
    private static final KeyDispatchDataCodec<BasinTemperatureDensity> DISPATCH=
        KeyDispatchDataCodec.of(MAP_CODEC);

    private double sample(int x,int y,int z){
        return heightSignal.compute(new DensityFunction.SinglePointContext(x,y,z));
    }
    @Override public double compute(DensityFunction.FunctionContext ctx) {
        double original=base.compute(ctx);
        if (warmth==0 || ctx.blockY()<48)return original;
        double c=continents.compute(ctx);
        if(c<-.03)return original;
        int x=ctx.blockX(),y=ctx.blockY(),z=ctx.blockZ(),r=radius;
        double center=heightSignal.compute(ctx);
        double[] rim={
            sample(x+r,y,z),sample(x-r,y,z),sample(x,y,z+r),sample(x,y,z-r),
            sample(x+r,y,z+r),sample(x+r,y,z-r),sample(x-r,y,z+r),sample(x-r,y,z-r)
        };
        return GeoMath.basinTemperature(original,center,rim,c,y,warmth);
    }
    @Override public DensityFunction mapAll(DensityFunction.Visitor visitor){
        return visitor.apply(new BasinTemperatureDensity(base.mapAll(visitor),
            heightSignal.mapAll(visitor),continents.mapAll(visitor),warmth,radius));
    }
    @Override public double minValue(){return base.minValue();}
    @Override public double maxValue(){return base.maxValue()+warmth;}
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec(){return DISPATCH;}
}
