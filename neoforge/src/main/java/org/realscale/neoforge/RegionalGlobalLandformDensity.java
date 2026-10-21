package org.realscale.neoforge;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Opt-in v3 global-landform horizontal height signal.
 *
 * Separate class/codec from both regional v1 and v2: opening an old save
 * continues to use its byte-identical density source and region distribution.
 * Samples use absolute world coordinates only; no biome rewrites or chunk reads.
 */
public final class RegionalGlobalLandformDensity implements DensityFunction.SimpleFunction {
    private final DensityFunction base, continents, selector;
    private final ThreadLocal<RegionalGlobalLandformCellMemo> cached =
            ThreadLocal.withInitial(RegionalGlobalLandformCellMemo::new);

    public RegionalGlobalLandformDensity(DensityFunction base,
                                          DensityFunction continents,
                                          DensityFunction selector) {
        this.base = base;
        this.continents = continents;
        this.selector = selector;
    }
    public DensityFunction base() { return base; }
    public DensityFunction continents() { return continents; }
    public DensityFunction selector() { return selector; }

    public static final MapCodec<RegionalGlobalLandformDensity> MAP_CODEC =
            RecordCodecBuilder.mapCodec(i -> i.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("base")
                            .forGetter(RegionalGlobalLandformDensity::base),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("continents")
                            .forGetter(RegionalGlobalLandformDensity::continents),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("selector")
                            .forGetter(RegionalGlobalLandformDensity::selector)
            ).apply(i, RegionalGlobalLandformDensity::new));
    private static final KeyDispatchDataCodec<RegionalGlobalLandformDensity> DISPATCH =
            KeyDispatchDataCodec.of(MAP_CODEC);

    @Override public double compute(DensityFunction.FunctionContext ctx) {
        double current = base.compute(ctx);
        if (current <= 0.35) return current;
        final int x = ctx.blockX(), z = ctx.blockZ();
        final int cellX = Math.floorDiv(x, RegionalLandformMath.CELL_V3);
        final int cellZ = Math.floorDiv(z, RegionalLandformMath.CELL_V3);
        // Never rehash the same 12-km cell for hundreds of Y samples.
        RegionalGlobalLandformCellMemo site=cached.get();
        site.at(cellX,cellZ);
        final double dx=(double)x-site.siteX, dz=(double)z-site.siteZ;
        if(dx*dx+dz*dz>=site.edgeSquared)return current;

        final double continental=continents.compute(ctx);
        if(RegionalLandformMath.gate(continental,current)<=0)return current;
        // Selection stays lazy and tied to the SAME seed-remapped selector.
        if(!site.hasKind){
            DensityFunction.SinglePointContext anchor =
                    new DensityFunction.SinglePointContext(site.siteX,0,site.siteZ);
            site.kind=RegionalLandformMath.kindV3(
                    site.hash,selector.compute(anchor));
            site.hasKind=true;
        }
        return RegionalLandformMath.apply(site.kind,current,dx,dz,
                site.radius,continental);
    }

    @Override public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply(new RegionalGlobalLandformDensity(
                base.mapAll(visitor), continents.mapAll(visitor),
                selector.mapAll(visitor)));
    }
    @Override public double minValue() { return base.minValue()-5.0; }
    @Override public double maxValue() { return base.maxValue()+7.0; }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return DISPATCH;
    }
}
