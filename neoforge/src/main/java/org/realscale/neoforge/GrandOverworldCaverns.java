package org.realscale.neoforge;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Restores actual underground caves to the RealScale main Overworld presets.
 * Cave network + original small-cave noise are used only well INSIDE solid
 * mountain strata. Original surface/noise/ocean/depth routing stays intact.
 *
 * Unlike a post-generation feature, this subtraction happens in final_density,
 * before blocks are made, and so can excavate enormous real tunnel volumes.
 */
public record GrandOverworldCaverns(DensityFunction base, DensityFunction warp,
                                    DensityFunction smallCaves)
        implements DensityFunction.SimpleFunction {
    public static final MapCodec<GrandOverworldCaverns> MAP_CODEC =
            RecordCodecBuilder.mapCodec(i -> i.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("base").forGetter(GrandOverworldCaverns::base),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("warp").forGetter(GrandOverworldCaverns::warp),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("small_caves").forGetter(GrandOverworldCaverns::smallCaves)
            ).apply(i,GrandOverworldCaverns::new));
    private static final KeyDispatchDataCodec<GrandOverworldCaverns> DISPATCH =
            KeyDispatchDataCodec.of(MAP_CODEC);

    /**
     * Protect at least ~30 blocks of density-equivalent roof near the surface.
     * 0.24 corresponds to ~31 vertical blocks in the canonical density slope,
     * but is a density threshold, NOT a full terrain-depth collision test.
     * Preserve the aquifers and the protected ocean/sea floor.
     */
    static double carve(double original,double tiny,double strength) {
        if(original <= 0.24) return original;
        double chosen=original;
        if(tiny < 0.0) chosen=Math.min(chosen,tiny);
        if(strength > 0.0) chosen=Math.min(chosen,-2.4*strength);
        return chosen;
    }

    @Override public double compute(DensityFunction.FunctionContext ctx) {
        double original=base.compute(ctx);
        int y=ctx.blockY();
        if(y<GrandOverworldCaveNetwork.MIN_Y
                || y>GrandOverworldCaveNetwork.MAX_Y
                || original<=0.24)
            return original;
        // The vanilla-seeded noise provides world-seed-dependent detail.
        // It never changes the room/corridor topology or chunk-border logic.
        double shift=Math.max(-1.0,Math.min(1.0,warp.compute(ctx)));
        double strength=GrandOverworldCaveNetwork.strength(
                ctx.blockX()+shift*6.0, y+shift*2.0,
                ctx.blockZ()-shift*5.0);
        return carve(original,smallCaves.compute(ctx),strength);
    }

    @Override public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply(new GrandOverworldCaverns(base.mapAll(visitor),
                warp.mapAll(visitor),smallCaves.mapAll(visitor)));
    }
    @Override public double minValue() {
        return Math.min(-3.0, Math.min(base.minValue(), smallCaves.minValue()));
    }
    @Override public double maxValue(){return base.maxValue();}
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec(){return DISPATCH;}
}
