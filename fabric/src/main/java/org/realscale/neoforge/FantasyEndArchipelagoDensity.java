package org.realscale.neoforge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * V2 experimental outer End: multiscale island groups, vertical cliff ribs,
 * sparse sky islands and small cross-island cavities. The entire region
 * <=1152 blocks from 0,0 uses vanilla End density exactly.
 */
public record FantasyEndArchipelagoDensity(
        DensityFunction base, DensityFunction broad, DensityFunction medium,
        DensityFunction fine, DensityFunction undercut, double strength)
        implements DensityFunction.SimpleFunction {
    public static final MapCodec<FantasyEndArchipelagoDensity> MAP_CODEC =
        RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("base").forGetter(FantasyEndArchipelagoDensity::base),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("broad").forGetter(FantasyEndArchipelagoDensity::broad),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("medium").forGetter(FantasyEndArchipelagoDensity::medium),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("fine").forGetter(FantasyEndArchipelagoDensity::fine),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("undercut").forGetter(FantasyEndArchipelagoDensity::undercut),
            Codec.doubleRange(0.0,1.5).fieldOf("strength").forGetter(FantasyEndArchipelagoDensity::strength)
        ).apply(i,FantasyEndArchipelagoDensity::new));
    private static final KeyDispatchDataCodec<FantasyEndArchipelagoDensity> DISPATCH =
        KeyDispatchDataCodec.of(MAP_CODEC);

    @Override public double compute(DensityFunction.FunctionContext ctx) {
        double original = base.compute(ctx);
        double distance = Math.hypot((double)ctx.blockX(), (double)ctx.blockZ());
        if (distance <= 1152.0 || strength <= 0.0) return original;
        double fade = GeoMath.smoothstep(1152.0, 1920.0, distance);
        double y = ctx.blockY();
        double a = broad.compute(ctx);
        double b = medium.compute(ctx);
        double c = fine.compute(ctx);
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
            double aperture = Math.abs(undercut.compute(ctx)) - 0.075;
            geology = Math.min(geology,aperture);
        }
        // Blend back to original End density with no sudden radial rim.
        geology -= (1.0-fade)*1.8;
        return Math.max(original,geology);
    }
    @Override public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply(new FantasyEndArchipelagoDensity(
            base.mapAll(visitor),broad.mapAll(visitor),medium.mapAll(visitor),
            fine.mapAll(visitor),undercut.mapAll(visitor),strength));
    }
    @Override public double minValue(){return Math.min(base.minValue(),-9.0);}
    @Override public double maxValue(){return Math.max(base.maxValue(),4.0);}
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec(){return DISPATCH;}
}
