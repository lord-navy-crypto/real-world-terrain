package org.realscale.neoforge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

import java.util.Arrays;

/**
 * Converts horizontal Minecraft block coordinates into source-map metres.
 * At scale=20, one Minecraft X/Z block samples twenty metres away. Y is
 * intentionally unchanged. A per-thread context pool avoids allocating a
 * wrapper object for every density evaluation; nested wrappers are supported.
 */
public record HorizontalScaleDensity(DensityFunction base,int scale)
        implements DensityFunction.SimpleFunction {

    public static final MapCodec<HorizontalScaleDensity> MAP_CODEC =
        RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("base")
                .forGetter(HorizontalScaleDensity::base),
            Codec.intRange(1,64).fieldOf("scale")
                .forGetter(HorizontalScaleDensity::scale)
        ).apply(i,HorizontalScaleDensity::new));

    private static final KeyDispatchDataCodec<HorizontalScaleDensity> DISPATCH =
        KeyDispatchDataCodec.of(MAP_CODEC);
    private static final ThreadLocal<ContextPool> CONTEXTS =
        ThreadLocal.withInitial(ContextPool::new);

    private static final class ScaledContext implements DensityFunction.FunctionContext {
        private DensityFunction.FunctionContext delegate;
        private int scale;

        void bind(DensityFunction.FunctionContext delegate,int scale) {
            this.delegate=delegate;
            this.scale=scale;
        }
        void clear() { delegate=null; }

        @Override public int blockX() {
            return GeoMath.scaleHorizontalCoordinate(delegate.blockX(),scale);
        }
        @Override public int blockY() { return delegate.blockY(); }
        @Override public int blockZ() {
            return GeoMath.scaleHorizontalCoordinate(delegate.blockZ(),scale);
        }
    }

    private static final class ContextPool {
        private ScaledContext[] contexts=new ScaledContext[2];
        private int depth;

        ScaledContext acquire(DensityFunction.FunctionContext source,int scale) {
            if(depth==contexts.length)
                contexts=Arrays.copyOf(contexts,contexts.length*2);
            ScaledContext context=contexts[depth];
            if(context==null) {
                context=new ScaledContext();
                contexts[depth]=context;
            }
            depth++;
            context.bind(source,scale);
            return context;
        }
        void release() {
            if(depth<=0)throw new IllegalStateException("Unbalanced horizontal scale context");
            contexts[--depth].clear();
        }
    }

    @Override public double compute(DensityFunction.FunctionContext ctx) {
        if(scale==1)return base.compute(ctx);
        ContextPool pool=CONTEXTS.get();
        ScaledContext scaled=pool.acquire(ctx,scale);
        try {
            return base.compute(scaled);
        } finally {
            pool.release();
        }
    }

    @Override public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply(new HorizontalScaleDensity(base.mapAll(visitor),scale));
    }
    @Override public double minValue() { return base.minValue(); }
    @Override public double maxValue() { return base.maxValue(); }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return DISPATCH;
    }
}
