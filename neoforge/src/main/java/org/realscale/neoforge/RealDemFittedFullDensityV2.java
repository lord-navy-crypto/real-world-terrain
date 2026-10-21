package org.realscale.neoforge;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * The saved V2 codec and mathematical terrain stay unchanged.
 *
 * PERFORMANCE: on the 2096-block-tall world, different Y samples repeatedly
 * revisit identical X/Z columns in an interleaved order. A last-column-only
 * cache recomputes fitted elevations hundreds of times per chunk. Instead
 * each mapped density instance owns a fixed, per-worker, allocation-free
 * 4096-slot cache keyed by WORLD-ABSOLUTE X/Z (including negative coords).
 *
 * Both the fitted surface and the continentalness gate are Y-INVARIANT
 * in the V2 preset: realism_max/continental_field is minecraft:flat_cache
 * of a y_scale=0 shifted noise. The cache must not be used with a Y-varying
 * "continents" input. mapAll creates a fresh instance for the new noise seed.
 *
 * Sea floor and source DEM remain byte-for-byte the legacy base result. Far
 * inland skips base.compute ENTIRELY, including its nested procedural graph.
 */
public final class RealDemFittedFullDensityV2 implements DensityFunction.SimpleFunction {
    private final DensityFunction base, continents;
    private final ThreadLocal<RealDemFittedColumnCache> columns=
        ThreadLocal.withInitial(RealDemFittedColumnCache::new);

    public RealDemFittedFullDensityV2(DensityFunction base,DensityFunction continents){
        this.base=base;this.continents=continents;
    }
    public DensityFunction base(){return base;}
    public DensityFunction continents(){return continents;}
    public static final MapCodec<RealDemFittedFullDensityV2> MAP_CODEC =
        RecordCodecBuilder.mapCodec(i->i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("base")
                .forGetter(RealDemFittedFullDensityV2::base),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("continents")
                .forGetter(RealDemFittedFullDensityV2::continents)
        ).apply(i,RealDemFittedFullDensityV2::new));
    private static final KeyDispatchDataCodec<RealDemFittedFullDensityV2> DISPATCH=
        KeyDispatchDataCodec.of(MAP_CODEC);

    @Override public double compute(DensityFunction.FunctionContext ctx) {
        final int x=ctx.blockX(),z=ctx.blockZ();
        final RealDemFittedColumnCache memo=columns.get();
        if(!memo.lookup(x,z)){
            // Only sample expensive misses (one 2D column, many vertical Y).
            final long begin=DensityPerfSampler.begin(
                DensityPerfSampler.Stage.USGS_FITTED_COLUMN);
            final double distance=RealDemFittedMathV2.distanceOutside(x,z);
            double weight=0.0,height=0.0;
            if(distance>0.0){
                // Known Y-invariant 2D continental field in V2 settings.
                final double continent=continents.compute(ctx);
                weight=RealDemFittedFullMathV2.fittedWeight(distance,continent);
                // No needless DEM fitting in protected ocean columns.
                if(weight>0.0)height=RealDemFittedMathV2.predictedHeight(x,z);
            }
            memo.store(x,z,weight,height);
            DensityPerfSampler.finish(
                DensityPerfSampler.Stage.USGS_FITTED_COLUMN,begin);
        }
        final double weight=memo.weight();
        if(weight<=0.0)return base.compute(ctx); // original DEM and sea
        final double fitted=(memo.height()-ctx.blockY())/128.0;
        if(weight>=1.0)return fitted; // pure fitted, no generic land
        final double original=base.compute(ctx);
        return Math.fma(fitted-original,weight,original);
    }
    @Override public DensityFunction mapAll(DensityFunction.Visitor visitor){
        // Never share cached per-column results across remapped world seeds.
        return visitor.apply(new RealDemFittedFullDensityV2(
            base.mapAll(visitor),continents.mapAll(visitor)));
    }
    @Override public double minValue(){return Math.min(-100.0,base.minValue());}
    @Override public double maxValue(){return Math.max(100.0,base.maxValue());}
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec(){
        return DISPATCH;
    }
}
