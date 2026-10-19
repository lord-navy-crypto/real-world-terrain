package org.realscale.neoforge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Procedural regional terrain morphology at the horizontal HEIGHT signal
 * stage, before vertical density, aquifer and cavern carving. The selected
 * mode never touches historical Sacred or measured USGS noise settings.
 */
public final class RegionalLandformDensity implements DensityFunction.SimpleFunction {
    private final DensityFunction base,continents,selector;
    public RegionalLandformDensity(DensityFunction base,DensityFunction continents,
                                   DensityFunction selector) {
        this.base=base;this.continents=continents;this.selector=selector;
    }
    public DensityFunction base(){return base;}
    public DensityFunction continents(){return continents;}
    public DensityFunction selector(){return selector;}
    public static final MapCodec<RegionalLandformDensity> MAP_CODEC =
            RecordCodecBuilder.mapCodec(i->i.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("base").forGetter(RegionalLandformDensity::base),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("continents").forGetter(RegionalLandformDensity::continents),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("selector").forGetter(RegionalLandformDensity::selector)
            ).apply(i,RegionalLandformDensity::new));
    private static final KeyDispatchDataCodec<RegionalLandformDensity> DISPATCH =
            KeyDispatchDataCodec.of(MAP_CODEC);

    @Override public double compute(DensityFunction.FunctionContext ctx) {
        double before=base.compute(ctx);
        if(before<=0.35)return before;
        int cellX=Math.floorDiv(ctx.blockX(),RegionalLandformMath.CELL);
        int cellZ=Math.floorDiv(ctx.blockZ(),RegionalLandformMath.CELL);
        long hash=RegionalLandformMath.hash(cellX,cellZ);
        double dx=(double)ctx.blockX()-RegionalLandformMath.centerX(cellX,hash);
        double dz=(double)ctx.blockZ()-RegionalLandformMath.centerZ(cellZ,hash);
        double radius=RegionalLandformMath.radius(hash);
        if(dx*dx+dz*dz>radius*radius*1.166400001)return before;
        double continent=continents.compute(ctx);
        if(RegionalLandformMath.gate(continent,before)<=0)return before;
        // Seed-dependent selector is sampled only at the site's fixed center
        // to keep region identity stable for every column and generation order.
        DensityFunction.SinglePointContext anchor=new DensityFunction.SinglePointContext(
                RegionalLandformMath.centerX(cellX,hash),0,
                RegionalLandformMath.centerZ(cellZ,hash));
        int kind=RegionalLandformMath.kind(hash,selector.compute(anchor));
        return RegionalLandformMath.apply(kind,before,dx,dz,radius,continent);
    }
    @Override public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply(new RegionalLandformDensity(
                base.mapAll(visitor),continents.mapAll(visitor),selector.mapAll(visitor)));
    }
    @Override public double minValue(){return base.minValue()-3.0;}
    @Override public double maxValue(){return base.maxValue()+5.0;}
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec(){return DISPATCH;}
}
