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
 * New-world-only north/south climate latitude, using north = negative Z.
 * Minecraft world coordinates remain geographic metres (1 block = 1 metre).
 * Only the climate latitude progression is compressed 1:20:
 * WGS84 quarter meridian 10,001,966 m / 20 = 500,098 climate blocks.
 * The equator is fixed at Z=0, never the spawn point. The codec retains its
 * historical equator_z and pole_distance fields so old worldgen JSON still
 * loads, but runtime calculation deliberately ignores those legacy values.
 * The live inspector and worldgen use the same WGS84 inverse-meridian math.
 */
public record LatitudeTemperatureDensity(DensityFunction base,int equatorZ,
                                         int poleDistance,double strength)
        implements DensityFunction {
    public static final MapCodec<LatitudeTemperatureDensity> MAP_CODEC =
        RecordCodecBuilder.mapCodec(i->i.group(
            DensityFunction.CODEC.fieldOf("base").forGetter(LatitudeTemperatureDensity::base),
            Codec.intRange(-29_000_000,29_000_000).fieldOf("equator_z").forGetter(LatitudeTemperatureDensity::equatorZ),
            Codec.intRange(1024,10_001_966).fieldOf("pole_distance").forGetter(LatitudeTemperatureDensity::poleDistance),
            Codec.doubleRange(0.0,1.0).fieldOf("strength").forGetter(LatitudeTemperatureDensity::strength)
        ).apply(i,LatitudeTemperatureDensity::new));
    public double compute(RealScaleDensityContext ctx){
        double latitude=GeographicClimateMath.geodeticLatitudeDeg(ctx.blockZ());
        return GeoMath.latitudeTemperature(ctx.compute(base),latitude,strength);
    }
        @Override public DensitySampler compileSampler(DensityFunction.CompileContext context) {
        return RealScaleDensitySamplerSupport.compile(context, new DensityFunction[]{base}, this::compute);
    }
    @Override public Interval range() { return Interval.INFINITE; }
    @Override public int domainAxes() { return DensityFunction.ALL_AXES; }

@Override public DensityFunction rewriteChildren(DfRewriteRule rule){
        return new LatitudeTemperatureDensity(rule.rewrite(base),equatorZ,poleDistance,strength);
    }
    
    
    @Override public MapCodec<? extends DensityFunction> codec(){return MAP_CODEC;}
}
