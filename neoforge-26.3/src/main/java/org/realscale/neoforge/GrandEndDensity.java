package org.realscale.neoforge;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.util.Interval;
/** Seeded distant-End 3D landmasses; keeps the original dragon island intact. */
public record GrandEndDensity(DensityFunction base,DensityFunction islandNoise,
                               DensityFunction ridgeNoise)
        implements DensityFunction {
    public static final MapCodec<GrandEndDensity> MAP_CODEC=RecordCodecBuilder.mapCodec(i->
        i.group(
            DensityFunction.CODEC.fieldOf("base").forGetter(GrandEndDensity::base),
            DensityFunction.CODEC.fieldOf("island_noise").forGetter(GrandEndDensity::islandNoise),
            DensityFunction.CODEC.fieldOf("ridge_noise").forGetter(GrandEndDensity::ridgeNoise)
        ).apply(i,GrandEndDensity::new));
    public double compute(RealScaleDensityContext context){
        // Original End noise only generates blocks up to Y127. Preserve the
        // dragon battle void above that cap exactly, even though we now
        // allocate a 512-block-tall dimension.
        double original=context.blockY()>=128 ? -1.0 : context.compute(base);
        double blend=GrandDimensionMath.outerEndBlend(context.blockX(),context.blockZ());
        if(blend==0.0||context.blockY()<128||context.blockY()>=GrandDimensionMath.HEIGHT)
            return original;
        double island=GrandDimensionMath.outerEndIslandDensity(context.blockY(),
                      context.compute(islandNoise),context.compute(ridgeNoise));
        if(island<=0.0)return original;
        return Math.max(original,island*blend);
    }
        @Override public DensitySampler compileSampler(DensityFunction.CompileContext context) {
        return RealScaleDensitySamplerSupport.compile(context, new DensityFunction[]{base, islandNoise, ridgeNoise}, this::compute);
    }
    @Override public Interval range() { return Interval.INFINITE; }
    @Override public int domainAxes() { return DensityFunction.ALL_AXES; }

@Override public DensityFunction rewriteChildren(DfRewriteRule rule){
        return new GrandEndDensity(rule.rewrite(base),
                 rule.rewrite(islandNoise),rule.rewrite(ridgeNoise));
    }
    
    
    @Override public MapCodec<? extends DensityFunction> codec(){return MAP_CODEC;}
}
