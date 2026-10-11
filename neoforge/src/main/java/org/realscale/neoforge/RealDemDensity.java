package org.realscale.neoforge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Real DEM-driven noise density inserted into vanilla NoiseBasedChunkGenerator.
 * The same function is used for final terrain AND preliminary surface prediction.
 * Outside the ~480m pilot window it returns original v0.6 Realism MAX density.
 */
public record RealDemDensity(DensityFunction base,int blendMeters) implements DensityFunction.SimpleFunction {
    static final RealDemTile DEM=RealDemTile.load();
    public static final MapCodec<RealDemDensity> MAP_CODEC=RecordCodecBuilder.mapCodec(i -> i.group(
        DensityFunction.HOLDER_HELPER_CODEC.fieldOf("base").forGetter(RealDemDensity::base),
        Codec.intRange(32,256).fieldOf("blend_meters").forGetter(RealDemDensity::blendMeters)
    ).apply(i,RealDemDensity::new));
    private static final KeyDispatchDataCodec<RealDemDensity> DISPATCH=KeyDispatchDataCodec.of(MAP_CODEC);

    @Override public double compute(DensityFunction.FunctionContext ctx) {
        double t=DEM.influence(ctx.blockX(),ctx.blockZ(),blendMeters);
        if(t<=0.0) return base.compute(ctx);
        double d=(DEM.worldSolidYAt(ctx.blockX(),ctx.blockZ())-ctx.blockY())/128.0;
        if(t>=1.0)return d;
        return d*t + base.compute(ctx)*(1.0-t);
    }
    @Override public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply(new RealDemDensity(base.mapAll(visitor),blendMeters));
    }
    @Override public double minValue(){return Math.min(-100.0,base.minValue());}
    @Override public double maxValue(){return Math.max(100.0,base.maxValue());}
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec(){return DISPATCH;}
}
