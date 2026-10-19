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
    private final boolean diverseV2;
    private final ThreadLocal<V2SiteCache> v2Cache=ThreadLocal.withInitial(V2SiteCache::new);
    private static final class V2SiteCache {
        int cx=Integer.MIN_VALUE,cz=Integer.MIN_VALUE,kind;
        long hash;
        boolean hasKind;
        void prepare(int x,int z,long siteHash) {
            if(cx==x&&cz==z)return;
            cx=x;cz=z;hash=siteHash;hasKind=false;
        }
    }
    public RegionalLandformDensity(DensityFunction base,DensityFunction continents,
                                   DensityFunction selector) {
        this(base,continents,selector,false);
    }
    private RegionalLandformDensity(DensityFunction base,DensityFunction continents,
                                    DensityFunction selector,boolean diverseV2) {
        this.base=base;this.continents=continents;this.selector=selector;
        this.diverseV2=diverseV2;
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
    public static final MapCodec<RegionalLandformDensity> V2_MAP_CODEC =
            RecordCodecBuilder.mapCodec(i->i.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("base").forGetter(RegionalLandformDensity::base),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("continents").forGetter(RegionalLandformDensity::continents),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("selector").forGetter(RegionalLandformDensity::selector)
            ).apply(i,(base,continents,selector)->
                    new RegionalLandformDensity(base,continents,selector,true)));
    private static final KeyDispatchDataCodec<RegionalLandformDensity> DISPATCH =
            KeyDispatchDataCodec.of(MAP_CODEC);
    private static final KeyDispatchDataCodec<RegionalLandformDensity> V2_DISPATCH =
            KeyDispatchDataCodec.of(V2_MAP_CODEC);

    @Override public double compute(DensityFunction.FunctionContext ctx) {
        double before=base.compute(ctx);
        if(before<=0.35)return before;
        if(diverseV2)return computeV2(ctx,before);
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
    private double computeV2(DensityFunction.FunctionContext ctx,double before) {
        int cx=Math.floorDiv(ctx.blockX(),RegionalLandformMath.CELL_V2);
        int cz=Math.floorDiv(ctx.blockZ(),RegionalLandformMath.CELL_V2);
        long h=RegionalLandformMath.hashV2(cx,cz);
        int siteX=RegionalLandformMath.centerXV2(cx,h);
        int siteZ=RegionalLandformMath.centerZV2(cz,h);
        double dx=(double)ctx.blockX()-siteX,dz=(double)ctx.blockZ()-siteZ;
        double radius=RegionalLandformMath.radiusV2(h);
        if(dx*dx+dz*dz>=radius*radius*1.166400001)return before;
        double continent=continents.compute(ctx);
        if(RegionalLandformMath.gate(continent,before)<=0)return before;
        V2SiteCache site=v2Cache.get();
        site.prepare(cx,cz,h);
        if(!site.hasKind) {
            var anchor=new DensityFunction.SinglePointContext(siteX,0,siteZ);
            site.kind=RegionalLandformMath.kindV2(h,selector.compute(anchor));
            site.hasKind=true;
        }
        return RegionalLandformMath.apply(site.kind,before,dx,dz,radius,continent);
    }
    @Override public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply(new RegionalLandformDensity(
                base.mapAll(visitor),continents.mapAll(visitor),selector.mapAll(visitor),diverseV2));
    }
    @Override public double minValue(){
        return base.minValue()-(diverseV2 ? 5.0 : 3.0);
    }
    @Override public double maxValue(){
        return base.maxValue()+(diverseV2 ? 9.0 : 5.0);
    }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec(){
        return diverseV2 ? V2_DISPATCH : DISPATCH;
    }
}
