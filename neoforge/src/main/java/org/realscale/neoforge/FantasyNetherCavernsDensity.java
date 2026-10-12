package org.realscale.neoforge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * V2 opt-in Nether geometry. Expands caves into several vertical chambers,
 * narrow connecting tunnels and tall cliff ribs while preserving the lower
 * lava sea and upper bedrock band. Does not change Nether biome selection.
 */
public record FantasyNetherCavernsDensity(
        DensityFunction base, DensityFunction chamberField,
        DensityFunction chamberMask, DensityFunction tunnelField,
        DensityFunction cliffField, double strength)
        implements DensityFunction.SimpleFunction {
    public static final MapCodec<FantasyNetherCavernsDensity> MAP_CODEC =
        RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("base").forGetter(FantasyNetherCavernsDensity::base),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("chamber_field").forGetter(FantasyNetherCavernsDensity::chamberField),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("chamber_mask").forGetter(FantasyNetherCavernsDensity::chamberMask),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("tunnel_field").forGetter(FantasyNetherCavernsDensity::tunnelField),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("cliff_field").forGetter(FantasyNetherCavernsDensity::cliffField),
            Codec.doubleRange(0.0,1.0).fieldOf("strength").forGetter(FantasyNetherCavernsDensity::strength)
        ).apply(i,FantasyNetherCavernsDensity::new));
    private static final KeyDispatchDataCodec<FantasyNetherCavernsDensity> DISPATCH =
        KeyDispatchDataCodec.of(MAP_CODEC);

    @Override public double compute(DensityFunction.FunctionContext ctx) {
        double existing = base.compute(ctx);
        int y = ctx.blockY();
        if (strength == 0.0 || y < 30 || y > 246) return existing;

        // Vertical ribbons of rock create cliffs, ribs and overhangs across
        // the already-expanded 256-block Nether, without moving lava level.
        double rib = 0.17 - 1.02 * Math.abs(cliffField.compute(ctx));
        rib -= Math.max(0, 50-y) * 0.012 + Math.max(0, y-224) * 0.022;
        double rock = Math.max(existing, strength * rib);

        double wide = chamberField.compute(ctx);
        double mask = Math.abs(chamberMask.compute(ctx));
        // The independent mask narrows giant chambers so huge contiguous
        // hollow regions do not consume entire mountain/ceiling strata.
        double rarity = Math.max(0.0, mask - 0.29) * 0.70;
        double lower = wide + 0.54 + Math.abs(y-83) * 0.012 + rarity;
        double middle = wide + 0.58 + Math.abs(y-153) * 0.011 + rarity;
        double upper = wide + 0.67 + Math.abs(y-215) * 0.026 + rarity;
        double galleries = Math.min(lower, Math.min(middle, upper));
        // Narrow winding connectors can traverse between chamber tiers.
        double tunnel = Math.abs(tunnelField.compute(ctx)) - 0.080;
        double openings = Math.min(galleries, tunnel);

        // Smoothly extinguish additional carving beside the lava sea and
        // bedrock roof. The original Nether void field remains unchanged.
        double safety = GeoMath.smoothstep(30, 50, y)
                * (1.0 - GeoMath.smoothstep(222, 246, y));
        double safeCavity = openings * strength + (1.0-safety) * 0.65;
        return Math.min(rock, safeCavity);
    }

    @Override public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply(new FantasyNetherCavernsDensity(
            base.mapAll(visitor), chamberField.mapAll(visitor),
            chamberMask.mapAll(visitor), tunnelField.mapAll(visitor),
            cliffField.mapAll(visitor), strength));
    }
    @Override public double minValue(){return Math.min(base.minValue(),-3.0);}
    @Override public double maxValue(){return Math.max(base.maxValue(),2.0);}
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec(){return DISPATCH;}
}
