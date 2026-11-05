package org.realscale.neoforge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * New-world-only north/south climate latitude, using north = negative Z.
 * Minecraft world coordinates remain geographic metres (1 block = 1 metre).
 * Only the climate latitude progression is compressed 1:20:
 * WGS84 quarter meridian 10,001,966 m / 20 = 500,098 climate blocks.
 * The equator is a fixed configured world Z (default 0), never the spawn point.
 * The repeating triangle keeps climate transitions continuous across poles.
 */
public record LatitudeTemperatureDensity(DensityFunction base,int equatorZ,
                                         int poleDistance,double strength)
        implements DensityFunction.SimpleFunction {
    public static final MapCodec<LatitudeTemperatureDensity> MAP_CODEC =
        RecordCodecBuilder.mapCodec(i->i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("base").forGetter(LatitudeTemperatureDensity::base),
            Codec.intRange(-29_000_000,29_000_000).fieldOf("equator_z").forGetter(LatitudeTemperatureDensity::equatorZ),
            Codec.intRange(1024,10_001_966).fieldOf("pole_distance").forGetter(LatitudeTemperatureDensity::poleDistance),
            Codec.doubleRange(0.0,1.0).fieldOf("strength").forGetter(LatitudeTemperatureDensity::strength)
        ).apply(i,LatitudeTemperatureDensity::new));
    private static final KeyDispatchDataCodec<LatitudeTemperatureDensity> DISPATCH =
        KeyDispatchDataCodec.of(MAP_CODEC);

    @Override public double compute(DensityFunction.FunctionContext ctx){
        double latitude=GeoMath.latitudeDeg(ctx.blockZ(),equatorZ,poleDistance);
        return GeoMath.latitudeTemperature(base.compute(ctx),latitude,strength);
    }
    @Override public DensityFunction mapAll(DensityFunction.Visitor visitor){
        return visitor.apply(new LatitudeTemperatureDensity(base.mapAll(visitor),equatorZ,poleDistance,strength));
    }
    @Override public double minValue(){return base.minValue()-.68*strength;}
    @Override public double maxValue(){return base.maxValue()+.22*strength;}
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec(){return DISPATCH;}
}
