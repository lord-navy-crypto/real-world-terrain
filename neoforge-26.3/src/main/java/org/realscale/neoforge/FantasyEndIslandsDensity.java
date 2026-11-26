package org.realscale.neoforge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Opt-in End outer-archipelago density. The central 1152-block-radius
 * dragon arena is strictly unchanged. Additional floating island shelves
 * fade in outside it, and vanish vertically away from their bands.
 *
 * Only a world preset that explicitly selects fantasy_end_v1 can use this.
 */
public record FantasyEndIslandsDensity(DensityFunction base,
        DensityFunction field,DensityFunction detail,double strength)
        implements DensityFunction.SimpleFunction {
    public static final MapCodec<FantasyEndIslandsDensity> MAP_CODEC =
        RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("base").forGetter(FantasyEndIslandsDensity::base),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("field").forGetter(FantasyEndIslandsDensity::field),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("detail").forGetter(FantasyEndIslandsDensity::detail),
            Codec.doubleRange(0.0,1.5).fieldOf("strength").forGetter(FantasyEndIslandsDensity::strength)
        ).apply(i,FantasyEndIslandsDensity::new));
    private static final KeyDispatchDataCodec<FantasyEndIslandsDensity> DISPATCH =
        KeyDispatchDataCodec.of(MAP_CODEC);

    @Override public double compute(DensityFunction.FunctionContext ctx) {
        double vanilla=base.compute(ctx);
        double radius=Math.hypot((double)ctx.blockX(),(double)ctx.blockZ());
        if(radius <= 1152 || strength <= 0.0)return vanilla;
        double fade=GeoMath.smoothstep(1152.0,1920.0,radius);
        double broad=field.compute(ctx);
        double fine=detail.compute(ctx);
        double y=ctx.blockY();
        // Two vertically separated outer-island terraces. A rare broad
        // footprint plus 3D variation yields sculpted rather than flat land.
        double lower=strength*(broad*0.92+fine*0.18-0.33)
                      -Math.abs(y-92.0)*0.026;
        double upper=strength*(broad*1.00+fine*0.22-0.36)
                      -Math.abs(y-166.0)*0.021;
        double islands=Math.max(lower,upper)-(1.0-fade)*1.4;
        return Math.max(vanilla,islands);
    }
    @Override public DensityFunction mapAll(DensityFunction.Visitor visitor){
        return visitor.apply(new FantasyEndIslandsDensity(base.mapAll(visitor),
                field.mapAll(visitor),detail.mapAll(visitor),strength));
    }
    @Override public double minValue(){return Math.min(base.minValue(),-8.0);}
    @Override public double maxValue(){return Math.max(base.maxValue(),3.0);}
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec(){return DISPATCH;}
}
