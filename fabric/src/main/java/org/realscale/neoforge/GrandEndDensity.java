package org.realscale.neoforge;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
/** Seeded distant-End 3D landmasses; keeps the original dragon island intact. */
public record GrandEndDensity(DensityFunction base,DensityFunction islandNoise,
                               DensityFunction ridgeNoise)
        implements DensityFunction.SimpleFunction {
    public static final MapCodec<GrandEndDensity> MAP_CODEC=RecordCodecBuilder.mapCodec(i->
        i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("base").forGetter(GrandEndDensity::base),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("island_noise").forGetter(GrandEndDensity::islandNoise),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("ridge_noise").forGetter(GrandEndDensity::ridgeNoise)
        ).apply(i,GrandEndDensity::new));
    private static final KeyDispatchDataCodec<GrandEndDensity> DISPATCH=KeyDispatchDataCodec.of(MAP_CODEC);
    @Override public double compute(DensityFunction.FunctionContext context){
        // Original End noise only generates blocks up to Y127. Preserve the
        // dragon battle void above that cap exactly, even though we now
        // allocate a 512-block-tall dimension.
        double original=context.blockY()>=128 ? -1.0 : base.compute(context);
        double blend=GrandDimensionMath.outerEndBlend(context.blockX(),context.blockZ());
        if(blend==0.0||context.blockY()<128||context.blockY()>=GrandDimensionMath.HEIGHT)
            return original;
        double island=GrandDimensionMath.outerEndIslandDensity(context.blockY(),
                      islandNoise.compute(context),ridgeNoise.compute(context));
        if(island<=0.0)return original;
        return Math.max(original,island*blend);
    }
    @Override public DensityFunction mapAll(DensityFunction.Visitor visitor){
        return visitor.apply(new GrandEndDensity(base.mapAll(visitor),
                 islandNoise.mapAll(visitor),ridgeNoise.mapAll(visitor)));
    }
    @Override public double minValue(){return Math.min(base.minValue(),-10.0);}
    @Override public double maxValue(){return Math.max(base.maxValue(),10.0);}
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec(){return DISPATCH;}
}
