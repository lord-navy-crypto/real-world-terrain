package org.realscale.neoforge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

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
           boolean areaBalanced)
        implements DensityFunction.SimpleFunction {
    public static final MapCodec<RadialLatitudeTemperatureDensity> MAP_CODEC =
        RecordCodecBuilder.mapCodec(i->i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("base").forGetter(RadialLatitudeTemperatureDensity::base),
            Codec.intRange(-29_000_000,29_000_000).fieldOf("center_x").forGetter(RadialLatitudeTemperatureDensity::centerX),
            Codec.intRange(-29_000_000,29_000_000).fieldOf("center_z").forGetter(RadialLatitudeTemperatureDensity::centerZ),
            Codec.intRange(1024,10_001_966).fieldOf("pole_distance").forGetter(RadialLatitudeTemperatureDensity::poleDistance),
            Codec.doubleRange(0.0,1.0).fieldOf("strength").forGetter(RadialLatitudeTemperatureDensity::strength),
            Codec.BOOL.optionalFieldOf("balanced",false).forGetter(RadialLatitudeTemperatureDensity::balanced),
            Codec.doubleRange(0.0,0.9).optionalFieldOf("altitude_cooling",0.0).forGetter(RadialLatitudeTemperatureDensity::altitudeCooling),
            Codec.BOOL.optionalFieldOf("area_balanced",false).forGetter(RadialLatitudeTemperatureDensity::areaBalanced)
        ).apply(i,RadialLatitudeTemperatureDensity::new));

    private static final KeyDispatchDataCodec<RadialLatitudeTemperatureDensity> DISPATCH =
        KeyDispatchDataCodec.of(MAP_CODEC);

    @Override public double compute(DensityFunction.FunctionContext ctx){
        double latitude=areaBalanced
            ?GeoMath.areaBalancedRadialLatitudeDeg(ctx.blockX(),ctx.blockZ(),centerX,centerZ,poleDistance)
            :balanced
              ?GeoMath.balancedRadialLatitudeDeg(ctx.blockX(),ctx.blockZ(),centerX,centerZ,poleDistance)
              :GeoMath.radialLatitudeDeg(ctx.blockX(),ctx.blockZ(),centerX,centerZ,poleDistance);
        return balanced
            ?GeoMath.balancedRadialTemperature(base.compute(ctx),latitude,strength,altitudeCooling,ctx.blockY())
            :GeoMath.latitudeTemperature(base.compute(ctx),latitude,strength);
    }
    @Override public DensityFunction mapAll(DensityFunction.Visitor visitor){
        return visitor.apply(new RadialLatitudeTemperatureDensity(base.mapAll(visitor),
            centerX,centerZ,poleDistance,strength,balanced,altitudeCooling,areaBalanced));
    }
    @Override public double minValue(){return base.minValue()-.68*strength;}
    @Override public double maxValue(){return base.maxValue()+.35*strength+altitudeCooling*.75;}
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec(){return DISPATCH;}
}
