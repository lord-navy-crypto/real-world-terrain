package org.realscale.neoforge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Small OPTIONAL warming of procedurally generated enclosed upland basins.
 * Samples the SAME seed-shifted 2048-cell site classifier as mountain_relief,
 * so the climate adjustment follows actual basins rather than random lowland.
 *
 * <p>Not a full weather simulation: real cold-air drainage can make some
 * basins colder at night. This approximation is bounded by climate_budget.
 */
public final class MountainBasinTemperatureDensity
        implements DensityFunction.SimpleFunction {
    private final DensityFunction base, heightSignal, continents, selector;
    private final double warmth;
    private final ThreadLocal<Site> current = ThreadLocal.withInitial(Site::new);

    private static final class Site {
        int gx = Integer.MIN_VALUE, gz = Integer.MIN_VALUE, kind;
        long hash;
        boolean classified;
        double x, z, radius;
    }

    public MountainBasinTemperatureDensity(DensityFunction base,
            DensityFunction heightSignal, DensityFunction continents,
            DensityFunction selector, double warmth) {
        this.base=base; this.heightSignal=heightSignal;
        this.continents=continents; this.selector=selector;
        this.warmth=warmth;
    }

    public DensityFunction base() {return base;}
    public DensityFunction heightSignal() {return heightSignal;}
    public DensityFunction continents() {return continents;}
    public DensityFunction selector() {return selector;}
    public double warmth() {return warmth;}

    public static final MapCodec<MountainBasinTemperatureDensity> MAP_CODEC =
            RecordCodecBuilder.mapCodec(i -> i.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("base").forGetter(MountainBasinTemperatureDensity::base),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("height_signal").forGetter(MountainBasinTemperatureDensity::heightSignal),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("continents").forGetter(MountainBasinTemperatureDensity::continents),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("selector").forGetter(MountainBasinTemperatureDensity::selector),
                    Codec.doubleRange(0.0, 0.25).fieldOf("warmth").forGetter(MountainBasinTemperatureDensity::warmth)
            ).apply(i, MountainBasinTemperatureDensity::new));
    private static final KeyDispatchDataCodec<MountainBasinTemperatureDensity> DISPATCH =
            KeyDispatchDataCodec.of(MAP_CODEC);

    private Site site(DensityFunction.FunctionContext ctx) {
        int gx=Math.floorDiv(ctx.blockX(),MountainReliefMath.CELL);
        int gz=Math.floorDiv(ctx.blockZ(),MountainReliefMath.CELL);
        Site s=current.get();
        if (s.gx==gx && s.gz==gz)return s;
        long h=MountainReliefMath.siteHash(gx,gz);
        s.x=(long)gx*MountainReliefMath.CELL
                +MountainReliefMath.CELL/2+MountainReliefMath.centerOffsetX(h);
        s.z=(long)gz*MountainReliefMath.CELL
                +MountainReliefMath.CELL/2+MountainReliefMath.centerOffsetZ(h);
        s.radius=MountainReliefMath.radius(h);
        // Region footprint is seed/hash-only. The classifier is expensive,
        // and only a small share of quart positions lie within basin cores.
        // Defer it until a point qualifies geometrically, preserving Y=0.
        s.hash=h;
        s.kind=MountainReliefMath.NONE;
        s.classified=false;
        s.gx=gx;s.gz=gz;
        return s;
    }

    private void classify(Site site) {
        if(site.classified)return;
        double region=selector.compute(new DensityFunction.SinglePointContext(
                Math.toIntExact((long)site.x),0,Math.toIntExact((long)site.z)));
        site.kind=MountainReliefMath.kind(site.hash,region);
        site.classified=true;
    }

    @Override public double compute(DensityFunction.FunctionContext ctx) {
        double original=base.compute(ctx);
        if(warmth==0)return original;
        // The altitude multiplier vanishes completely at Y<=80. Do not
        // sample continents/height noise in that common case.
        double exposedLand=GeoMath.smoothstep(80,200,ctx.blockY());
        if(exposedLand<=0.0)return original;
        double continent=continents.compute(ctx);
        if(continent<=-0.10)return original;
        double highlandGate=MountainReliefMath.inlandUplandGate(
                continent,heightSignal.compute(ctx));
        if(highlandGate<=0.0)return original;
        Site s=site(ctx);
        double dx=ctx.blockX()-s.x,dz=ctx.blockZ()-s.z;
        // Conservative squared-distance rejection of sites outside the
        // actual r<0.78 basin core; the tiny epsilon protects edge parity.
        double limit=s.radius*0.780000001;
        if(dx*dx+dz*dz>=limit*limit)return original;
        classify(s);
        if(s.kind!=MountainReliefMath.BASIN)return original;
        double r=Math.hypot(dx,dz)/s.radius;
        if(r>=0.78)return original;
        return original + warmth*MountainReliefMath.basinCore(r)
                *highlandGate*exposedLand;
    }

    @Override public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply(new MountainBasinTemperatureDensity(base.mapAll(visitor),
                heightSignal.mapAll(visitor),continents.mapAll(visitor),
                selector.mapAll(visitor),warmth));
    }
    @Override public double minValue(){return base.minValue();}
    @Override public double maxValue(){return base.maxValue()+warmth;}
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec(){return DISPATCH;}
}
