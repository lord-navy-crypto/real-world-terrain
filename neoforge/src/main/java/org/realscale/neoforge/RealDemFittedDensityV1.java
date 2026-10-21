package org.realscale.neoforge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Versioned fitted USGS continuation: old saves and original DEM untouched.
 * Full in-source height preservation; offshore return is bit-exact base.
 * Source location, coherent synthetic model and baselines are world-absolute.
 */
public final class RealDemFittedDensityV1 implements DensityFunction.SimpleFunction {
    private final DensityFunction base, continents;
    private final double strength;
    private final ThreadLocal<Column> cache=ThreadLocal.withInitial(Column::new);
    private static final class Column {
        int x,z;
        boolean valid;
        double predicted,coverageWeight;
    }
    public RealDemFittedDensityV1(DensityFunction base,
                                 DensityFunction continents,double strength){
        this.base=base;this.continents=continents;this.strength=strength;
    }
    public DensityFunction base(){return base;}
    public DensityFunction continents(){return continents;}
    public double strength(){return strength;}
    public static final MapCodec<RealDemFittedDensityV1> MAP_CODEC=
        RecordCodecBuilder.mapCodec(i->i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("base")
                .forGetter(RealDemFittedDensityV1::base),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("continents")
                .forGetter(RealDemFittedDensityV1::continents),
            Codec.doubleRange(0.0,1.0).fieldOf("strength")
                .forGetter(RealDemFittedDensityV1::strength)
        ).apply(i,RealDemFittedDensityV1::new));
    private static final KeyDispatchDataCodec<RealDemFittedDensityV1> DISPATCH=
            KeyDispatchDataCodec.of(MAP_CODEC);
    @Override public double compute(DensityFunction.FunctionContext ctx){
        final int x=ctx.blockX(),z=ctx.blockZ();
        final double baseDensity=base.compute(ctx);
        final double d=RealDemFittedMathV1.distanceOutside(x,z);
        if(d<=0.0||strength<=0.0)return baseDensity;
        double continentalness=continents.compute(ctx);
        // Preserve all existing marine seafloor, ocean caves and deep-sea layers.
        double land=RealDemFittedMathV1.smoothstep(-0.15,0.18,continentalness);
        if(land<=0.0)return baseDensity;
        Column c=cache.get();
        if(!c.valid||c.x!=x||c.z!=z){
            c.x=x;c.z=z;c.valid=true;
            c.predicted=RealDemFittedMathV1.predictedHeight(x,z);
            // Normal derivative at source boundary stays original; after the
            // 1km shoulder fitted terrain contributes at full configured strength.
            c.coverageWeight=RealDemFittedMathV1.smoothstep(0.0,1536.0,d);
        }
        double w=strength*land*c.coverageWeight;
        if(w==0.0)return baseDensity;
        double fitted=(c.predicted-ctx.blockY())/128.0;
        return baseDensity+(fitted-baseDensity)*w;
    }
    @Override public DensityFunction mapAll(DensityFunction.Visitor visitor){
        return visitor.apply(new RealDemFittedDensityV1(
            base.mapAll(visitor),continents.mapAll(visitor),strength));
    }
    @Override public double minValue(){return Math.min(-100.0,base.minValue());}
    @Override public double maxValue(){return Math.max(100.0,base.maxValue());}
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec(){return DISPATCH;}
}
