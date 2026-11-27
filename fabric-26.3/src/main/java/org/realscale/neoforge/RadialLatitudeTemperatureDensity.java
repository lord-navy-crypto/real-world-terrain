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
 * Concentric repeating "latitude" for Minecraft's unbounded procedural
 * world: 0 at the center, 90 at radius Q, 0 at 2Q, 90 at 3Q...
 *
 * This is intentionally not spherical Earth geography: the polar zones are
 * circular rings, not polar points, and there is no N/S hemisphere sign.
 * Does not need trigonometry or seed-specific world spawn coordinates.
 */
public record RadialLatitudeTemperatureDensity(DensityFunction base,int centerX,
           int centerZ,int poleDistance,double strength,boolean balanced,double altitudeCooling,
           boolean areaBalanced, boolean singlePoleArea)
        implements DensityFunction {
    public static final MapCodec<RadialLatitudeTemperatureDensity> MAP_CODEC =
        RecordCodecBuilder.mapCodec(i->i.group(
            DensityFunction.CODEC.fieldOf("base").forGetter(RadialLatitudeTemperatureDensity::base),
            Codec.intRange(-29_000_000,29_000_000).fieldOf("center_x").forGetter(RadialLatitudeTemperatureDensity::centerX),
            Codec.intRange(-29_000_000,29_000_000).fieldOf("center_z").forGetter(RadialLatitudeTemperatureDensity::centerZ),
            Codec.intRange(1024,10_001_966).fieldOf("pole_distance").forGetter(RadialLatitudeTemperatureDensity::poleDistance),
            Codec.doubleRange(0.0,1.0).fieldOf("strength").forGetter(RadialLatitudeTemperatureDensity::strength),
            Codec.BOOL.optionalFieldOf("balanced",false).forGetter(RadialLatitudeTemperatureDensity::balanced),
            Codec.doubleRange(0.0,0.9).optionalFieldOf("altitude_cooling",0.0).forGetter(RadialLatitudeTemperatureDensity::altitudeCooling),
            Codec.BOOL.optionalFieldOf("area_balanced",false).forGetter(RadialLatitudeTemperatureDensity::areaBalanced),
            Codec.BOOL.optionalFieldOf("single_pole_area",false).forGetter(RadialLatitudeTemperatureDensity::singlePoleArea)
        ).apply(i,RadialLatitudeTemperatureDensity::new));

    public double compute(RealScaleDensityContext ctx){
        double latitude=singlePoleArea
            ?GeoMath.singlePoleAreaRadialLatitudeDeg(ctx.blockX(),ctx.blockZ(),centerX,centerZ,poleDistance)
            :areaBalanced
              ?GeoMath.areaBalancedRadialLatitudeDeg(ctx.blockX(),ctx.blockZ(),centerX,centerZ,poleDistance)
            :balanced
              ?GeoMath.balancedRadialLatitudeDeg(ctx.blockX(),ctx.blockZ(),centerX,centerZ,poleDistance)
              :GeoMath.radialLatitudeDeg(ctx.blockX(),ctx.blockZ(),centerX,centerZ,poleDistance);
        return (balanced || areaBalanced || singlePoleArea)
            ?GeoMath.balancedRadialTemperature(ctx.compute(base),latitude,strength,altitudeCooling,ctx.blockY())
            :GeoMath.latitudeTemperature(ctx.compute(base),latitude,strength);
    }
        @Override public DensitySampler compileSampler(DensityFunction.CompileContext context) {
        return RealScaleDensitySamplerSupport.compile(context, new DensityFunction[]{base}, this::compute);
    }
    @Override public Interval range() { return Interval.INFINITE; }
    @Override public int domainAxes() { return DensityFunction.ALL_AXES; }

@Override public DensityFunction rewriteChildren(DfRewriteRule rule){
        return new RadialLatitudeTemperatureDensity(rule.rewrite(base),
            centerX,centerZ,poleDistance,strength,balanced,altitudeCooling,areaBalanced,singlePoleArea);
    }
    
    
    @Override public MapCodec<? extends DensityFunction> codec(){return MAP_CODEC;}
}
