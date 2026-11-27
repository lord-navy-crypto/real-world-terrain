package org.realscale.neoforge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.util.Interval;

/**
 * Opt-in End outer-archipelago density. The central 1152-block-radius
 * dragon arena is strictly unchanged. Additional floating island shelves
 * fade in outside it, and vanish vertically away from their bands.
 *
 * Only a world preset that explicitly selects fantasy_end_v1 can use this.
 */
public record FantasyEndIslandsDensity(DensityFunction base,
        DensityFunction field,DensityFunction detail,double strength)
        implements DensityFunction {
    public static final MapCodec<FantasyEndIslandsDensity> MAP_CODEC =
        RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.CODEC.fieldOf("base").forGetter(FantasyEndIslandsDensity::base),
            DensityFunction.CODEC.fieldOf("field").forGetter(FantasyEndIslandsDensity::field),
            DensityFunction.CODEC.fieldOf("detail").forGetter(FantasyEndIslandsDensity::detail),
            Codec.doubleRange(0.0,1.5).fieldOf("strength").forGetter(FantasyEndIslandsDensity::strength)
        ).apply(i,FantasyEndIslandsDensity::new));
    public double compute(RealScaleDensityContext ctx) {
        double vanilla=ctx.compute(base);
        double radius=Math.hypot((double)ctx.blockX(),(double)ctx.blockZ());
        if(radius <= 1152 || strength <= 0.0)return vanilla;
        double fade=GeoMath.smoothstep(1152.0,1920.0,radius);
        double broad=ctx.compute(field);
        double fine=ctx.compute(detail);
        double y=ctx.blockY();
        // Two vertically separated outer-island terraces. A rare broad
        // footprint plus 3D variation yields sculpted rather than flat land.
        double lower=strength*(broad*0.92+fine*0.18-0.33)
                      -Math.abs(y-92.0)*0.026;
        double upper=strength*(broad*1.00+fine*0.22-0.36)
                      -Math.abs(y-166.0)*0.021;
        double islands=Math.max(lower,upper)-(1.0-fade)*1.4;
        return Math.max(vanilla,islands);
    }
        @Override public DensitySampler compileSampler(DensityFunction.CompileContext context) {
        return RealScaleDensitySamplerSupport.compile(context, new DensityFunction[]{base, field, detail}, this::compute);
    }
    @Override public Interval range() { return Interval.INFINITE; }
    @Override public int domainAxes() { return DensityFunction.ALL_AXES; }

@Override public DensityFunction rewriteChildren(DfRewriteRule rule){
        return new FantasyEndIslandsDensity(rule.rewrite(base),
                rule.rewrite(field),rule.rewrite(detail),strength);
    }
    
    
    @Override public MapCodec<? extends DensityFunction> codec(){return MAP_CODEC;}
}
