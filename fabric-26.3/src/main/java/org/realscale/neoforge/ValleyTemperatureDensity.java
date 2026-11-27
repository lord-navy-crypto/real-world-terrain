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
        double warmth,int radius) implements DensityFunction {

    public static final MapCodec<ValleyTemperatureDensity> MAP_CODEC =
        RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.CODEC.fieldOf("base").forGetter(ValleyTemperatureDensity::base),
            DensityFunction.CODEC.fieldOf("height_signal").forGetter(ValleyTemperatureDensity::heightSignal),
            DensityFunction.CODEC.fieldOf("continents").forGetter(ValleyTemperatureDensity::continents),
            Codec.doubleRange(0,0.35).fieldOf("warmth").forGetter(ValleyTemperatureDensity::warmth),
            Codec.intRange(48,128).fieldOf("radius").forGetter(ValleyTemperatureDensity::radius)
        ).apply(i,ValleyTemperatureDensity::new));

    private double relativeValley(RealScaleDensityContext ctx) {
        int x=ctx.blockX(), y=ctx.blockY(), z=ctx.blockZ();
        double east=ctx.at(x+radius,y,z).compute(heightSignal);
        double west=ctx.at(x-radius,y,z).compute(heightSignal);
        double north=ctx.at(x,y,z-radius).compute(heightSignal);
        double south=ctx.at(x,y,z+radius).compute(heightSignal);
        return 0.25*(east+west+north+south);
    }

    public double compute(RealScaleDensityContext ctx) {
        double temperature=ctx.compute(base);
        if (warmth<=0 || ctx.blockY()>=256) return temperature;
        double continent=ctx.compute(continents);
        if(continent<=-0.04) return temperature;
        double center=ctx.compute(heightSignal);
        return GeoMath.valleyTemperature(temperature,center,relativeValley(ctx),
            continent,ctx.blockY(),warmth);
    }

        @Override public DensitySampler compileSampler(DensityFunction.CompileContext context) {
        return RealScaleDensitySamplerSupport.compile(context, new DensityFunction[]{base, heightSignal, continents}, this::compute);
    }
    @Override public Interval range() { return Interval.INFINITE; }
    @Override public int domainAxes() { return DensityFunction.ALL_AXES; }

@Override public DensityFunction rewriteChildren(DfRewriteRule rule) {
        return new ValleyTemperatureDensity(
            rule.rewrite(base),rule.rewrite(heightSignal),
            rule.rewrite(continents),warmth,radius);
    }
    
    
    @Override public MapCodec<? extends DensityFunction> codec(){return MAP_CODEC;}
}
