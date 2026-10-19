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
    private static final int SLOTS=8192;

    public static final MapCodec<MeasuredHorizontalMemoDensity> MAP_CODEC =
            RecordCodecBuilder.mapCodec(i -> i.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("input")
                            .forGetter(MeasuredHorizontalMemoDensity::input)
            ).apply(i, MeasuredHorizontalMemoDensity::new));

    private static final KeyDispatchDataCodec<MeasuredHorizontalMemoDensity> DISPATCH =
            KeyDispatchDataCodec.of(MAP_CODEC);

    private final DensityFunction input;
    private final ThreadLocal<Columns> caches =
            ThreadLocal.withInitial(Columns::new);

    private static final class Columns {
        final long[] coordinates=new long[SLOTS];
        final double[] values=new double[SLOTS];
        final boolean[] valid=new boolean[SLOTS];
        boolean lastValid;
        long lastCoordinate;
        double lastValue;
    }

    public MeasuredHorizontalMemoDensity(DensityFunction input) {
        this.input=java.util.Objects.requireNonNull(input);
    }

    public DensityFunction input() { return input; }

    @Override public double compute(DensityFunction.FunctionContext ctx) {
        int x=ctx.blockX(), z=ctx.blockZ();
        long coordinate=((long)x<<32)|(z&0xffffffffL);
        Columns c=caches.get();
        if(c.lastValid && c.lastCoordinate==coordinate)return c.lastValue;
        int hash=x*0x9e3779b9 ^ Integer.rotateLeft(z*0x85ebca6b,13);
        int slot=(hash^(hash>>>16))&(SLOTS-1);
        final double value;
        if(c.valid[slot] && c.coordinates[slot]==coordinate)
            value=c.values[slot];
        else {
            value=input.compute(ctx);
            c.coordinates[slot]=coordinate;
            c.values[slot]=value;
            c.valid[slot]=true;
        }
        c.lastCoordinate=coordinate;
        c.lastValue=value;
        c.lastValid=true;
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
