package org.realscale.neoforge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Seed-sensitive, smooth terrain-height perturbation using existing noise
 * functions provided by the active world generator. This is a bounded,
 * geology-inspired approximation, NOT hydraulic erosion simulation.
 *
 * Density units: +/- 1 typically corresponds to ~128 blocks in the
 * existing RealScale density gradient. Keep both gains small.
 */
public record RidgeErosionDensity(DensityFunction base, DensityFunction ridgeSignal,
                                  DensityFunction erosionSignal, double ridgeGain,
                                  double erosionGain) implements DensityFunction.SimpleFunction {
    public static final MapCodec<RidgeErosionDensity> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        DensityFunction.HOLDER_HELPER_CODEC.fieldOf("base").forGetter(RidgeErosionDensity::base),
        DensityFunction.HOLDER_HELPER_CODEC.fieldOf("ridge_signal").forGetter(RidgeErosionDensity::ridgeSignal),
        DensityFunction.HOLDER_HELPER_CODEC.fieldOf("erosion_signal").forGetter(RidgeErosionDensity::erosionSignal),
        Codec.doubleRange(0.0,0.32).fieldOf("ridge_gain").forGetter(RidgeErosionDensity::ridgeGain),
        Codec.doubleRange(0.0,0.24).fieldOf("erosion_gain").forGetter(RidgeErosionDensity::erosionGain)
    ).apply(i,RidgeErosionDensity::new));

    private static final KeyDispatchDataCodec<RidgeErosionDensity> DISPATCH =
        KeyDispatchDataCodec.of(MAP_CODEC);

    @Override public double compute(DensityFunction.FunctionContext ctx) {
        return GeoMath.ridgeErosion(base.compute(ctx),ridgeSignal.compute(ctx),
            erosionSignal.compute(ctx),ridgeGain,erosionGain);
    }
    @Override public void fillArray(double[] samples,DensityFunction.ContextProvider contexts) {
        base.fillArray(samples,contexts);
        // Respect Minecraft's context provider and seed-resolved visitors.
        for(int i=0;i<samples.length;i++) {
            DensityFunction.FunctionContext ctx=contexts.forIndex(i);
            samples[i]=GeoMath.ridgeErosion(samples[i],ridgeSignal.compute(ctx),
                erosionSignal.compute(ctx),ridgeGain,erosionGain);
        }
    }
    @Override public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply(new RidgeErosionDensity(base.mapAll(visitor),
            ridgeSignal.mapAll(visitor),erosionSignal.mapAll(visitor),ridgeGain,erosionGain));
    }
    @Override public double minValue(){return base.minValue()-erosionGain;}
    @Override public double maxValue(){return base.maxValue()+ridgeGain;}
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec(){return DISPATCH;}
}
