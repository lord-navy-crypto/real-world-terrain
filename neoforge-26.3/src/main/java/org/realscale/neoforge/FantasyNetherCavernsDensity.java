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
 * V2 opt-in Nether geometry. Expands caves into several vertical chambers,
 * narrow connecting tunnels and tall cliff ribs while preserving the lower
 * lava sea and upper bedrock band. Does not change Nether biome selection.
 */
public record FantasyNetherCavernsDensity(
        DensityFunction base, DensityFunction chamberField,
        DensityFunction chamberMask, DensityFunction tunnelField,
        DensityFunction cliffField, double strength)
        implements DensityFunction {
    public static final MapCodec<FantasyNetherCavernsDensity> MAP_CODEC =
        RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.CODEC.fieldOf("base").forGetter(FantasyNetherCavernsDensity::base),
            DensityFunction.CODEC.fieldOf("chamber_field").forGetter(FantasyNetherCavernsDensity::chamberField),
            DensityFunction.CODEC.fieldOf("chamber_mask").forGetter(FantasyNetherCavernsDensity::chamberMask),
            DensityFunction.CODEC.fieldOf("tunnel_field").forGetter(FantasyNetherCavernsDensity::tunnelField),
            DensityFunction.CODEC.fieldOf("cliff_field").forGetter(FantasyNetherCavernsDensity::cliffField),
            Codec.doubleRange(0.0,1.0).fieldOf("strength").forGetter(FantasyNetherCavernsDensity::strength)
        ).apply(i,FantasyNetherCavernsDensity::new));
    public double compute(RealScaleDensityContext ctx) {
        double existing = ctx.compute(base);
        int y = ctx.blockY();
        if (strength == 0.0 || y < 30 || y > 246) return existing;

        // Vertical ribbons of rock create cliffs, ribs and overhangs across
        // the already-expanded 256-block Nether, without moving lava level.
        double rib = 0.17 - 1.02 * Math.abs(ctx.compute(cliffField));
        rib -= Math.max(0, 50-y) * 0.012 + Math.max(0, y-224) * 0.022;
        double rock = Math.max(existing, strength * rib);

        double wide = ctx.compute(chamberField);
        double mask = Math.abs(ctx.compute(chamberMask));
        // The independent mask narrows giant chambers so huge contiguous
        // hollow regions do not consume entire mountain/ceiling strata.
        double rarity = Math.max(0.0, mask - 0.29) * 0.70;
        double lower = wide + 0.54 + Math.abs(y-83) * 0.012 + rarity;
        double middle = wide + 0.58 + Math.abs(y-153) * 0.011 + rarity;
        double upper = wide + 0.67 + Math.abs(y-215) * 0.026 + rarity;
        double galleries = Math.min(lower, Math.min(middle, upper));
        // Narrow winding connectors can traverse between chamber tiers.
        double tunnel = Math.abs(ctx.compute(tunnelField)) - 0.080;
        double openings = Math.min(galleries, tunnel);

        // Smoothly extinguish additional carving beside the lava sea and
        // bedrock roof. The original Nether void field remains unchanged.
        double safety = GeoMath.smoothstep(30, 50, y)
                * (1.0 - GeoMath.smoothstep(222, 246, y));
        double safeCavity = openings * strength * safety + (1.0-safety) * 0.65;
        return Math.min(rock, safeCavity);
    }

        @Override public DensitySampler compileSampler(DensityFunction.CompileContext context) {
        return RealScaleDensitySamplerSupport.compile(context, new DensityFunction[]{base, chamberField, chamberMask, tunnelField, cliffField}, this::compute);
    }
    @Override public Interval range() { return Interval.INFINITE; }
    @Override public int domainAxes() { return DensityFunction.ALL_AXES; }

@Override public DensityFunction rewriteChildren(DfRewriteRule rule) {
        return new FantasyNetherCavernsDensity(
            rule.rewrite(base), rule.rewrite(chamberField),
            rule.rewrite(chamberMask), rule.rewrite(tunnelField),
            rule.rewrite(cliffField), strength);
    }
    
    
    @Override public MapCodec<? extends DensityFunction> codec(){return MAP_CODEC;}
}
