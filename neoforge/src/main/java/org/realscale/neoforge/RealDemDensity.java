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

    // Only active near the measured tile; each worker keeps at most 256 X/Z samples.
    // Sharing a mutable cache across generator worker threads would be unsafe.
    private static final ThreadLocal<RealDemColumnCache> COLUMN_CACHE =
        ThreadLocal.withInitial(() -> new RealDemColumnCache(DEM));

    @Override public double compute(DensityFunction.FunctionContext ctx) {
        final int x=ctx.blockX(), z=ctx.blockZ();
        // Most generated chunks are outside the ~480m pilot: no cache lookup
        // or interpolation is needed. Equality is also zero DEM influence.
        if (x<=DEM.originX() || x>=DEM.maxX() || z<=DEM.originZ() || z>=DEM.maxZ())
            return base.compute(ctx);

        RealDemColumnCache.Column column = COLUMN_CACHE.get().sample(x,z,blendMeters);
        double t=column.influence();
        if(t<=0.0) return base.compute(ctx);
        double d=(column.elevation()-ctx.blockY())/128.0;
        if(t>=1.0)return d;
        // Keep the original floating-point expression order: identical seed,
        // coordinates and settings must produce identical densities.
        return d*t + base.compute(ctx)*(1.0-t);
    }
    @Override public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply(new RealDemDensity(base.mapAll(visitor),blendMeters));
    }
    @Override public double minValue(){return Math.min(-100.0,base.minValue());}
    @Override public double maxValue(){return Math.max(100.0,base.maxValue());}
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec(){return DISPATCH;}
}
