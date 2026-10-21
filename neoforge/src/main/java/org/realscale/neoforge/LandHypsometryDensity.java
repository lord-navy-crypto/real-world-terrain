package org.realscale.neoforge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Land-only, versioned hypsometry adjustment. Must wrap procedural
 * horizontal height_signal only, BEFORE vanilla vertical density gradient;
 * never wrap DEM, ocean, caves or final_density. */
public record LandHypsometryDensity(DensityFunction base,double strength)
        implements DensityFunction.SimpleFunction {
    public static final MapCodec<LandHypsometryDensity> MAP_CODEC =
        RecordCodecBuilder.mapCodec(i->i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("base")
                .forGetter(LandHypsometryDensity::base),
            Codec.doubleRange(0.0,0.45).fieldOf("strength")
                .forGetter(LandHypsometryDensity::strength)
        ).apply(i,LandHypsometryDensity::new));
    private static final KeyDispatchDataCodec<LandHypsometryDensity> DISPATCH=
            KeyDispatchDataCodec.of(MAP_CODEC);
    @Override public double compute(DensityFunction.FunctionContext ctx) {
        return LandHypsometryMath.adjust(base.compute(ctx),strength);
    }
    @Override public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply(new LandHypsometryDensity(base.mapAll(visitor),strength));
    }
    @Override public double minValue(){return base.minValue()-3.0;}
    @Override public double maxValue(){return base.maxValue();}
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec(){return DISPATCH;}
}
