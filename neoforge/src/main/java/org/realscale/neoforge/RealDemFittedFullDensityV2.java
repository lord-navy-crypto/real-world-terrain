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
    private static final int SLOTS=4096, MASK=SLOTS-1;
    private final DensityFunction base, continents;
    private static final class Columns {
        final long[] keys=new long[SLOTS];
        final boolean[] valid=new boolean[SLOTS];
        final double[] weights=new double[SLOTS];
        final double[] surfaces=new double[SLOTS];
        long recentKey;
        double recentWeight,recentSurface;
        boolean recentValid;
        int misses;
        int hits;
    }
    private final ThreadLocal<Columns> columns=ThreadLocal.withInitial(Columns::new);

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

    private static int index(int x,int z){
        int h=x*0x9e3779b9^Integer.rotateLeft(z*0x85ebca6b,13);
        return (h^(h>>>16))&MASK;
    }

    @Override public double compute(DensityFunction.FunctionContext ctx) {
        final int x=ctx.blockX(),z=ctx.blockZ();
        final long key=((long)x<<32)|(z&0xffffffffL);
        final Columns cache=columns.get();
        final double weight, surface;
        if(cache.recentValid && cache.recentKey==key){
            weight=cache.recentWeight;surface=cache.recentSurface;
            cache.hits++;
        }else{
            final int slot=index(x,z);
            if(cache.valid[slot] && cache.keys[slot]==key){
                weight=cache.weights[slot];surface=cache.surfaces[slot];
                cache.hits++;
            }else{
                final double distance=RealDemFittedMathV2.distanceOutside(x,z);
                double fittedWeight=0.0, fittedSurface=0.0;
                // Do not load the continental noise or fitted surface at all
                // for exact USGS area. No ocean height extrapolation either.
                if(distance>0.0){
                    final double continentalness=continents.compute(ctx);
                    fittedWeight=RealDemFittedFullMathV2.fittedWeight(
                        distance,continentalness);
                    if(fittedWeight>0.0){
                        fittedSurface=RealDemFittedMathV2.predictedHeight(x,z);
                    }
                }
                cache.keys[slot]=key;cache.valid[slot]=true;
                cache.weights[slot]=fittedWeight;
                cache.surfaces[slot]=fittedSurface;
                cache.misses++;
                weight=fittedWeight;surface=fittedSurface;
            }
            cache.recentKey=key;cache.recentWeight=weight;
            cache.recentSurface=surface;cache.recentValid=true;
        }
        if(weight<=0.0)return base.compute(ctx); // exact DEM and oceans
        final double fitted=(surface-ctx.blockY())/128.0;
        if(weight>=1.0)return fitted; // not a single generic height operation
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
