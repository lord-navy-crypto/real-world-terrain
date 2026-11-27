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
 * Optional bounded biome-climate basin proxy. Eight height-signal samples
 * approximate a closed rim; this does not alter heightmaps or create basins.
 * Wind, precipitation and nocturnal inversion are not simulated.
 */
public record BasinTemperatureDensity(DensityFunction base,DensityFunction heightSignal,
                                      DensityFunction continents,double warmth,int radius)
        implements DensityFunction {
    public static final MapCodec<BasinTemperatureDensity> MAP_CODEC =
        RecordCodecBuilder.mapCodec(i->i.group(
            DensityFunction.CODEC.fieldOf("base").forGetter(BasinTemperatureDensity::base),
            DensityFunction.CODEC.fieldOf("height_signal").forGetter(BasinTemperatureDensity::heightSignal),
            DensityFunction.CODEC.fieldOf("continents").forGetter(BasinTemperatureDensity::continents),
            Codec.doubleRange(0,0.35).fieldOf("warmth").forGetter(BasinTemperatureDensity::warmth),
            Codec.intRange(48,512).fieldOf("radius").forGetter(BasinTemperatureDensity::radius)
        ).apply(i,BasinTemperatureDensity::new));
    private double sample(RealScaleDensityContext ctx,int x,int y,int z){
        return ctx.at(x,y,z).compute(heightSignal);
    }
    public double compute(RealScaleDensityContext ctx) {
        double original=ctx.compute(base);
        if (warmth==0 || ctx.blockY()<48)return original;
        double c=ctx.compute(continents);
        if(c<-.03)return original;
        int x=ctx.blockX(),y=ctx.blockY(),z=ctx.blockZ(),r=radius;
        double center=ctx.compute(heightSignal);
        double[] rim={
            sample(ctx,x+r,y,z),sample(ctx,x-r,y,z),sample(ctx,x,y,z+r),sample(ctx,x,y,z-r),
            sample(ctx,x+r,y,z+r),sample(ctx,x+r,y,z-r),sample(ctx,x-r,y,z+r),sample(ctx,x-r,y,z-r)
        };
        return GeoMath.basinTemperature(original,center,rim,c,y,warmth);
    }
        @Override public DensitySampler compileSampler(DensityFunction.CompileContext context) {
        return RealScaleDensitySamplerSupport.compile(context, new DensityFunction[]{base, heightSignal, continents}, this::compute);
    }
    @Override public Interval range() { return Interval.INFINITE; }
    @Override public int domainAxes() { return DensityFunction.ALL_AXES; }

@Override public DensityFunction rewriteChildren(DfRewriteRule rule){
        return new BasinTemperatureDensity(rule.rewrite(base),
            rule.rewrite(heightSignal),rule.rewrite(continents),warmth,radius);
    }
    
    
    @Override public MapCodec<? extends DensityFunction> codec(){return MAP_CODEC;}
}
