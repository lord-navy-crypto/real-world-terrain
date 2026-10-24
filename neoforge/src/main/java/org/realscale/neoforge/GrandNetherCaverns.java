package org.realscale.neoforge;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Seed-warped, continuous upper-Nether cave density that creates large hollow
 * mountain interiors and connected 3D natural tunnels. Vanilla terrain
 * remains unchanged below Y144 and above Y440.
 */
public record GrandNetherCaverns(DensityFunction base,DensityFunction warp)
        implements DensityFunction.SimpleFunction {
    public static final MapCodec<GrandNetherCaverns> MAP_CODEC =
        RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("base").forGetter(GrandNetherCaverns::base),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("warp").forGetter(GrandNetherCaverns::warp)
        ).apply(i, GrandNetherCaverns::new));
    private static final KeyDispatchDataCodec<GrandNetherCaverns> DISPATCH =
        KeyDispatchDataCodec.of(MAP_CODEC);

    @Override public double compute(DensityFunction.FunctionContext context) {
        if(!DensityPerfSampler.ENABLED)return computeUnprofiled(context);
        long start=DensityPerfSampler.begin(DensityPerfSampler.Stage.NETHER_CAVE);
        try{return computeUnprofiled(context);}
        finally{DensityPerfSampler.finish(DensityPerfSampler.Stage.NETHER_CAVE,start);}
    }

    private double computeUnprofiled(DensityFunction.FunctionContext context) {
        double original=base.compute(context);
        int x=context.blockX(),y=context.blockY(),z=context.blockZ();
        // Never call extra noise functions in the protected original realm.
        if(y<GrandNetherCaveNetwork.MIN_Y || y>GrandNetherCaveNetwork.MAX_Y)
            return original;
        // Both room and corridor strengths are max(0,1-normalizedDistance),
        // so the entire cave network is bounded by [0,1]. If the original
        // density is already <= -1.8, min(original,-1.8*strength) is
        // exactly original for ANY warp/noise/corridor. Skip this expensive
        // path in existing air volumes without changing density values.
        if(original<=-1.8) return original;
        // If the worker-local, warp-inclusive X/Z bounds exclude every
        // chamber and corridor, strength is exactly zero at any altitude.
        // Keep original vanilla noise and all old cave geometries intact.
        // Look up the 19-bit region/column mask ONCE. The old path first
        // queried it through mayContainVoidNearColumn and then queried it
        // again inside strengthFromColumn after computing the noise warp.
        // Carry the identical mask to the geometry evaluator instead.
        // Resolve the X/Z 19-component mask and its conservative Y reach
        // from the SAME worker-local region graph. Returning zero means
        // one of the original two checks proved cave strength exactly 0;
        // -1 still forces the complete shifted-neighbor fallback.
        int candidateMask=GrandNetherCaveNetwork.componentMaskForColumnAtHeight(
                x,y,z);
        if(candidateMask==0)
            return original;
        // Gently distort ideal ellipsoids and straight corridors using a
        // smooth world-seeded 3D noise field. Less than the radius of a
        // connector, preserving walkable links across all chunk borders.
        // Keep the seeded warp continuous: rounding to whole blocks caused
        // artificial, stair-stepped cave walls in the first prototype.
        double displacement=Math.max(-1.0,Math.min(1.0,warp.compute(context)));
        double strength=GrandNetherCaveNetwork.strengthFromColumnWithMask(
                x,z,x+displacement*5.0, y+displacement*2.0,
                z-displacement*4.0,candidateMask);
        return strength<=0 ? original : Math.min(original,-1.8*strength);
    }
    @Override public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply(new GrandNetherCaverns(base.mapAll(visitor),
                                                    warp.mapAll(visitor)));
    }
    @Override public double minValue() {return Math.min(base.minValue(),-2.0);}
    @Override public double maxValue() {return base.maxValue();}
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec(){
        return DISPATCH;
    }
}
