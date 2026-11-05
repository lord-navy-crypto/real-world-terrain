package org.realscale.neoforge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/** New-world-only geographic temperature latitude, using north = negative Z.
 * Coordinates are anchored to a configured equator line (default Z=0),
 * NOT to a future spawn point, which is unknown when chunk generation begins.
 * Quarter meridian at 1 block/meter is 10,001,966 blocks. The repeating
 * triangle avoids climate discontinuities at either pole or crossing equator.
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
