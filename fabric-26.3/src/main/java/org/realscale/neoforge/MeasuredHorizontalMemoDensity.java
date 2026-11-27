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

/**
 * Exact memo for an explicitly Y-INVARIANT height signal.
 *
 * Production height signals may use different fitted source families, but
 * this wrapper is permitted only when the complete input graph is proven
 * independent of Y. Worldgen / structure probes repeatedly ask that SAME
 * X,Z signal at hundreds of Y levels in a 2096-high world. Reusing the
 * original returned double avoids re-running the same seeded graph.
 *
 * The caller must NEVER wrap a Y-dependent expression, noise warp or
 * final_density in this node. No noise is interpolated, approximated or
 * sampled at a different point: misses call ctx.compute(input) as before.
 *
 * The cache is per mapped density instance and per worker thread, so no
 * values can leak across differently seeded worldgen instances. All entries
 * are fixed-size and evictions are strictly performance-only.
 */
public final class MeasuredHorizontalMemoDensity
        implements DensityFunction {
    public static final MapCodec<MeasuredHorizontalMemoDensity> MAP_CODEC =
            RecordCodecBuilder.mapCodec(i -> i.group(
                    DensityFunction.CODEC.fieldOf("input")
                            .forGetter(MeasuredHorizontalMemoDensity::input)
            ).apply(i, MeasuredHorizontalMemoDensity::new));

    private final DensityFunction input;
    private final ThreadLocal<MeasuredColumnMemo> caches =
            ThreadLocal.withInitial(MeasuredColumnMemo::new);

    public MeasuredHorizontalMemoDensity(DensityFunction input) {
        this.input=java.util.Objects.requireNonNull(input);
    }

    public DensityFunction input() { return input; }

    public double compute(RealScaleDensityContext ctx) {
        MeasuredColumnMemo memo=caches.get();
        boolean hit=memo.lookup(ctx.blockX(),ctx.blockZ());
        if(DensityPerfSampler.ENABLED)DensityPerfSampler.horizontalMemoProbe(hit);
        if(hit)return memo.value();
        double value=ctx.compute(input);
        memo.store(value);
        return value;
    }

        @Override public DensitySampler compileSampler(DensityFunction.CompileContext context) {
        return RealScaleDensitySamplerSupport.compile(context, new DensityFunction[]{input}, this::compute);
    }
    @Override public Interval range() { return Interval.INFINITE; }
    @Override public int domainAxes() { return DensityFunction.ALL_AXES; }

@Override public DensityFunction rewriteChildren(DfRewriteRule rule) {
        // A fresh memo for every seed/noise mapping is essential. Never
        // share an unseeded cache across world presets or Minecraft worlds.
        return new MeasuredHorizontalMemoDensity(
                rule.rewrite(input));
    }

    
    
    @Override public MapCodec<? extends DensityFunction> codec() {
        return MAP_CODEC;
    }
}
