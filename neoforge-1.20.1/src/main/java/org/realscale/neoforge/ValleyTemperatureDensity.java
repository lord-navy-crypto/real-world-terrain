package org.realscale.neoforge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Optional biome-temperature adjustment for new Terrain Lab worlds only.
 *
 * A bounded, gradual, geography-inspired proxy: compare the same
 * height signal 96 blocks away in four directions, then warm the
 * lower inland valley when neighbors average higher than the center.
 * No default warming of all lowland plains or ocean biomes.
 *
 * This is not a meteorological model; cold-air pooling / nocturnal
 * valley inversions may make real valleys colder rather than warmer.
 */
public record ValleyTemperatureDensity(DensityFunction base,
        DensityFunction heightSignal,DensityFunction continents,
        double warmth,int radius) implements DensityFunction.SimpleFunction {

    public static final MapCodec<ValleyTemperatureDensity> MAP_CODEC =
        RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("base").forGetter(ValleyTemperatureDensity::base),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("height_signal").forGetter(ValleyTemperatureDensity::heightSignal),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("continents").forGetter(ValleyTemperatureDensity::continents),
            Codec.doubleRange(0,0.35).fieldOf("warmth").forGetter(ValleyTemperatureDensity::warmth),
            Codec.intRange(48,128).fieldOf("radius").forGetter(ValleyTemperatureDensity::radius)
        ).apply(i,ValleyTemperatureDensity::new));

    private static final KeyDispatchDataCodec<ValleyTemperatureDensity> DISPATCH =
        KeyDispatchDataCodec.of(MAP_CODEC);

    private double relativeValley(DensityFunction.FunctionContext ctx) {
        int x=ctx.blockX(), y=ctx.blockY(), z=ctx.blockZ();
        double east=heightSignal.compute(new DensityFunction.SinglePointContext(x+radius,y,z));
        double west=heightSignal.compute(new DensityFunction.SinglePointContext(x-radius,y,z));
        double north=heightSignal.compute(new DensityFunction.SinglePointContext(x,y,z-radius));
        double south=heightSignal.compute(new DensityFunction.SinglePointContext(x,y,z+radius));
        return 0.25*(east+west+north+south);
    }

    @Override public double compute(DensityFunction.FunctionContext ctx) {
        double temperature=base.compute(ctx);
        if (warmth<=0 || ctx.blockY()>=256) return temperature;
        double continent=continents.compute(ctx);
        if(continent<=-0.04) return temperature;
        double center=heightSignal.compute(ctx);
        return GeoMath.valleyTemperature(temperature,center,relativeValley(ctx),
            continent,ctx.blockY(),warmth);
    }

    @Override public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply(new ValleyTemperatureDensity(
            base.mapAll(visitor),heightSignal.mapAll(visitor),
            continents.mapAll(visitor),warmth,radius));
    }
    @Override public double minValue(){return base.minValue();}
    @Override public double maxValue(){return base.maxValue()+warmth;}
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec(){return DISPATCH;}
}
