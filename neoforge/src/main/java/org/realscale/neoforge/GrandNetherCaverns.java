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
        double original=base.compute(context);
        int x=context.blockX(),y=context.blockY(),z=context.blockZ();
        // Never call extra noise functions in the protected original realm.
        if(y<GrandNetherCaveNetwork.MIN_Y || y>GrandNetherCaveNetwork.MAX_Y)
            return original;
        // Gently distort ideal ellipsoids and straight corridors using a
        // smooth world-seeded 3D noise field. Less than the radius of a
        // connector, preserving walkable links across all chunk borders.
        double displacement=warp.compute(context);
        int dx=(int)Math.round(displacement*5.0);
        int dy=(int)Math.round(displacement*2.0);
        int dz=(int)Math.round(displacement*-4.0);
        double strength=GrandNetherCaveNetwork.strength(x+dx,y+dy,z+dz);
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
