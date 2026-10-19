package org.realscale.neoforge;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Exact memo for an explicitly Y-INVARIANT height signal.
 *
 * The measured preset's realism_max/height_signal is a 2D flat_cache
 * function. Worldgen / structure height probes repeatedly ask that SAME
 * X,Z signal at hundreds of Y levels in a 2096-high world. Reusing the
 * original returned double avoids re-running a large seeded spline graph.
 *
 * The caller must NEVER wrap a Y-dependent expression, noise warp or
 * final_density in this node. No noise is interpolated, approximated or
 * sampled at a different point: misses call input.compute(ctx) as before.
 *
 * The cache is per mapped density instance and per worker thread, so no
 * values can leak across differently seeded worldgen instances. All entries
 * are fixed-size and evictions are strictly performance-only.
 */
public final class MeasuredHorizontalMemoDensity
        implements DensityFunction.SimpleFunction {
    public static final MapCodec<MeasuredHorizontalMemoDensity> MAP_CODEC =
            RecordCodecBuilder.mapCodec(i -> i.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("input")
                            .forGetter(MeasuredHorizontalMemoDensity::input)
            ).apply(i, MeasuredHorizontalMemoDensity::new));

    private static final KeyDispatchDataCodec<MeasuredHorizontalMemoDensity> DISPATCH =
            KeyDispatchDataCodec.of(MAP_CODEC);

    private final DensityFunction input;
    private final ThreadLocal<MeasuredColumnMemo> caches =
            ThreadLocal.withInitial(MeasuredColumnMemo::new);

    public MeasuredHorizontalMemoDensity(DensityFunction input) {
        this.input=java.util.Objects.requireNonNull(input);
    }

    public DensityFunction input() { return input; }

    @Override public double compute(DensityFunction.FunctionContext ctx) {
        MeasuredColumnMemo memo=caches.get();
        if(memo.lookup(ctx.blockX(),ctx.blockZ()))return memo.value();
        double value=input.compute(ctx);
        memo.store(value);
        return value;
    }

    @Override public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        // A fresh memo for every seed/noise mapping is essential. Never
        // share an unseeded cache across world presets or Minecraft worlds.
        return visitor.apply(new MeasuredHorizontalMemoDensity(
                input.mapAll(visitor)));
    }

    @Override public double minValue() { return input.minValue(); }
    @Override public double maxValue() { return input.maxValue(); }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return DISPATCH;
    }
}
