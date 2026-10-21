package org.realscale.neoforge;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * New worlds ONLY: the measured-data bounds retain their original USGS
 * density bit-for-bit. Beyond their 1536-block smooth transition, ALL
 * ordinary land uses the DEM-fitted world-space height function, with ZERO
 * contribution from the original generic procedural land density.
 *
 * The only procedural classification that remains is existing continentalness
 * to protect the coastline and ocean floor; coastlines have a smooth fade.
 * RealDemFittedMathV1 uses globally defined coordinates and continuously
 * interpolated multi-scale noise, never chunk-local/random per-cell block IDs.
 *
 * Uses its own CODEC ID so older V1 save data remain unchanged.
 */
public final class RealDemFittedFullDensityV2 implements DensityFunction.SimpleFunction {
    private final DensityFunction base, continents;
    private static final class Column {
        int x,z;
        boolean valid;
        double fittedSurfaceHeight,distance;
    }
    private final ThreadLocal<Column> columns=ThreadLocal.withInitial(Column::new);

    public RealDemFittedFullDensityV2(DensityFunction base,DensityFunction continents){
        this.base=base;this.continents=continents;
    }
    public DensityFunction base(){return base;}
    public DensityFunction continents(){return continents;}

    public static final MapCodec<RealDemFittedFullDensityV2> MAP_CODEC=
        RecordCodecBuilder.mapCodec(i->i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("base")
                .forGetter(RealDemFittedFullDensityV2::base),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("continents")
                .forGetter(RealDemFittedFullDensityV2::continents)
        ).apply(i,RealDemFittedFullDensityV2::new));

    private static final KeyDispatchDataCodec<RealDemFittedFullDensityV2> DISPATCH=
        KeyDispatchDataCodec.of(MAP_CODEC);

    @Override public double compute(DensityFunction.FunctionContext ctx) {
        final int x=ctx.blockX(), z=ctx.blockZ();
        final double original=base.compute(ctx);
        final Column c=columns.get();
        if(!c.valid||c.x!=x||c.z!=z){
            c.x=x;c.z=z;c.valid=true;
            c.distance=RealDemFittedMathV1.distanceOutside(x,z);
            c.fittedSurfaceHeight=c.distance>0.0
                ?RealDemFittedMathV1.predictedHeight(x,z):0.0;
        }
        // Keep exact measured USGS and its natural block terrain.
        if(c.distance<=0.0)return original;
        final double continentalness=continents.compute(ctx);
        // Ocean is never touched; near-coast morphology blends continuously.
        if(continentalness<=-0.15)return original;
        final double fitted=(c.fittedSurfaceHeight-ctx.blockY())/128.0;
        return RealDemFittedFullMathV2.blendDensity(
            original,fitted,c.distance,continentalness);
    }
    @Override public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply(new RealDemFittedFullDensityV2(
            base.mapAll(visitor),continents.mapAll(visitor)));
    }
    @Override public double minValue() {return Math.min(-100.0,base.minValue());}
    @Override public double maxValue() {return Math.max(100.0,base.maxValue());}
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return DISPATCH;
    }
}
