package org.realscale.neoforge;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Carve connected natural-looking upper-Nether caves out of the vanilla
 * high-ceiling rock density. Never generates blocks or fluids directly.
 */
public record GrandNetherCaverns(DensityFunction base)
        implements DensityFunction.SimpleFunction {
    public static final MapCodec<GrandNetherCaverns> MAP_CODEC =
        RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("base").forGetter(GrandNetherCaverns::base)
        ).apply(i, GrandNetherCaverns::new));
    private static final KeyDispatchDataCodec<GrandNetherCaverns> DISPATCH =
        KeyDispatchDataCodec.of(MAP_CODEC);

    @Override public double compute(DensityFunction.FunctionContext context) {
        double original=base.compute(context);
        double strength=GrandNetherCaveNetwork.strength(
              context.blockX(),context.blockY(),context.blockZ());
        if(strength<=0)return original;
        // Multiplying by smooth signed strength blends the hollow mountain
        // shells into original noise; only subtract solid where cave exists.
        return Math.min(original,-1.8*strength);
    }
    @Override public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply(new GrandNetherCaverns(base.mapAll(visitor)));
    }
    @Override public double minValue() {return Math.min(base.minValue(),-2.0);}
    @Override public double maxValue() {return base.maxValue();}
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec(){
        return DISPATCH;
    }
}
