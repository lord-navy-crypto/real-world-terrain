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
 * V2 experimental outer End: multiscale island groups, vertical cliff ribs,
 * sparse sky islands and small cross-island cavities. The entire region
 * <=1152 blocks from 0,0 uses vanilla End density exactly.
 */
public record FantasyEndArchipelagoDensity(
        DensityFunction base, DensityFunction broad, DensityFunction medium,
        DensityFunction fine, DensityFunction undercut, double strength)
        implements DensityFunction {
    public static final MapCodec<FantasyEndArchipelagoDensity> MAP_CODEC =
        RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.CODEC.fieldOf("base").forGetter(FantasyEndArchipelagoDensity::base),
            DensityFunction.CODEC.fieldOf("broad").forGetter(FantasyEndArchipelagoDensity::broad),
            DensityFunction.CODEC.fieldOf("medium").forGetter(FantasyEndArchipelagoDensity::medium),
            DensityFunction.CODEC.fieldOf("fine").forGetter(FantasyEndArchipelagoDensity::fine),
            DensityFunction.CODEC.fieldOf("undercut").forGetter(FantasyEndArchipelagoDensity::undercut),
            Codec.doubleRange(0.0,1.5).fieldOf("strength").forGetter(FantasyEndArchipelagoDensity::strength)
        ).apply(i,FantasyEndArchipelagoDensity::new));
    public double compute(RealScaleDensityContext ctx) {
        double original = ctx.compute(base);
        double distance = Math.hypot((double)ctx.blockX(), (double)ctx.blockZ());
        if (distance <= 1152.0 || strength <= 0.0) return original;
        double fade = GeoMath.smoothstep(1152.0, 1920.0, distance);
        double y = ctx.blockY();
        double a = ctx.compute(broad);
        double b = ctx.compute(medium);
        double c = ctx.compute(fine);
        // Archipelago continents: broad common pattern and mid-scale bays.
        // Density transitions are continuous at all scales and world seams.
        double continent = strength * (a*0.98 + b*0.35 + c*0.08 - 0.15);
        double mainIslands = continent - Math.abs(y-89.0)*0.017;
        // Mid-sized high terraces are less common than main islands.
        double highIslands = strength*(a*0.77 + b*0.44 + c*0.17 - 0.40)
                             - Math.abs(y-166.0)*0.024;
        // Rare small islands beyond conventional outer End heights.
        double skyIslands = strength*(a*0.54 + b*0.64 + c*0.21 - 0.76)
                            - Math.abs(y-219.0)*0.042;
        // XZ-only high-frequency rib around sheer cliffs and island edges.
        // Intersections build massive cliff faces, but keep separated void.
        double cliffRib = strength*(a*0.16 + 0.14 - Math.abs(b)*0.83)
                          - Math.max(0.0,y-194.0)*0.016
                          - Math.max(0.0,50.0-y)*0.018;
        double geology = Math.max(Math.max(mainIslands,highIslands),
                                  Math.max(skyIslands,cliffRib));
        // Narrow undercuts through mid-height rock; never hollow thin sky
        // islands or remove the preserved central dragon platform.
        if (y > 61 && y < 131 && geology > 0.0) {
            double aperture = Math.abs(ctx.compute(undercut)) - 0.075;
            geology = Math.min(geology,aperture);
        }
        // Blend back to original End density with no sudden radial rim.
        geology -= (1.0-fade)*1.8;
        return Math.max(original,geology);
    }
        @Override public DensitySampler compileSampler(DensityFunction.CompileContext context) {
        return RealScaleDensitySamplerSupport.compile(context, new DensityFunction[]{base, broad, medium, fine, undercut}, this::compute);
    }
    @Override public Interval range() { return Interval.INFINITE; }
    @Override public int domainAxes() { return DensityFunction.ALL_AXES; }

@Override public DensityFunction rewriteChildren(DfRewriteRule rule) {
        return new FantasyEndArchipelagoDensity(
            rule.rewrite(base),rule.rewrite(broad),rule.rewrite(medium),
            rule.rewrite(fine),rule.rewrite(undercut),strength);
    }
    
    
    @Override public MapCodec<? extends DensityFunction> codec(){return MAP_CODEC;}
}
