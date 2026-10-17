package org.realscale.neoforge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Mountain-scale basin and plateau relief, applied to HORIZONTAL height
 * signals before the world's vertical density gradient is added.
 *
 * <p>Never edits sea levels, fluids, caves, Minecraft blocks or dimensions.
 * Samples a seed-resolved biome/erosion signal once per active 2048x2048
 * regional site, using a bounded per-density-worker thread-local cache.
 * Never samples another chunk's interpolated height, and never depends on
 * what neighboring chunk happened to generate first.
 */
public final class MountainReliefDensity implements DensityFunction.SimpleFunction {
    private final DensityFunction base, continents, selector;
    private final double basinDepth, plateauRise;
    // One bounded site memo per density function and worker. Revisit a region
    // while jigsaw structures sample other distant columns without
    // re-hashing the geometry or recomputing its seeded classification.
    private final ThreadLocal<MountainReliefSiteCache> current =
            ThreadLocal.withInitial(MountainReliefSiteCache::new);

    public MountainReliefDensity(DensityFunction base, DensityFunction continents,
                                 DensityFunction selector, double basinDepth,
                                 double plateauRise) {
        this.base = base;
        this.continents = continents;
        this.selector = selector;
        this.basinDepth = basinDepth;
        this.plateauRise = plateauRise;
    }

    public DensityFunction base() { return base; }
    public DensityFunction continents() { return continents; }
    public DensityFunction selector() { return selector; }
    public double basinDepth() { return basinDepth; }
    public double plateauRise() { return plateauRise; }

    public static final MapCodec<MountainReliefDensity> MAP_CODEC =
            RecordCodecBuilder.mapCodec(i -> i.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("base").forGetter(MountainReliefDensity::base),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("continents").forGetter(MountainReliefDensity::continents),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("selector").forGetter(MountainReliefDensity::selector),
                    Codec.doubleRange(0.0, 1.9).fieldOf("basin_depth").forGetter(MountainReliefDensity::basinDepth),
                    Codec.doubleRange(0.0, 1.3).fieldOf("plateau_rise").forGetter(MountainReliefDensity::plateauRise)
            ).apply(i, MountainReliefDensity::new));
    private static final KeyDispatchDataCodec<MountainReliefDensity> DISPATCH =
            KeyDispatchDataCodec.of(MAP_CODEC);

    private MountainReliefSiteCache.Site site(DensityFunction.FunctionContext ctx) {
        int gx=Math.floorDiv(ctx.blockX(),MountainReliefMath.CELL);
        int gz=Math.floorDiv(ctx.blockZ(),MountainReliefMath.CELL);
        return current.get().get(gx,gz);
    }

    private void classify(MountainReliefSiteCache.Site site) {
        if (site.classified) return;
        var anchor=new DensityFunction.SinglePointContext(
                Math.toIntExact((long)site.centerX),0,
                Math.toIntExact((long)site.centerZ));
        double regionalSignal=selector.compute(anchor);
        site.type=MountainReliefMath.kind(site.hash,regionalSignal);
        site.anchorHeight=site.type==MountainReliefMath.PLATEAU
                ||site.type==MountainReliefMath.MESA
                ?MountainReliefMath.plateauLevel(site.hash,regionalSignal):0;
        site.classified=true;
    }

    @Override public double compute(DensityFunction.FunctionContext ctx) {
        if(!DensityPerfSampler.ENABLED)return computeUnprofiled(ctx);
        long started=DensityPerfSampler.begin(DensityPerfSampler.Stage.MOUNTAIN_RELIEF);
        try{return computeUnprofiled(ctx);}
        finally{DensityPerfSampler.finish(DensityPerfSampler.Stage.MOUNTAIN_RELIEF,started);}
    }

    private double computeUnprofiled(DensityFunction.FunctionContext ctx) {
        double original = base.compute(ctx);
        // For original <= 0.65, the upland gate's second smoothstep is
        // mathematically zero for ANY continentalness. This also avoids
        // expensive continent noise reads in the ocean and lowlands.
        if (original <= 0.65) return original;
        MountainReliefSiteCache.Site s = site(ctx);
        double dx = ctx.blockX() - s.centerX;
        double dz = ctx.blockZ() - s.centerZ;
        // Site footprint is hash-defined, independent of its expensive
        // seed-resolved selector. Faraway columns never need that sample.
        if (s.definitelyOutside(dx,dz))
            return original;
        classify(s);
        if (s.type == MountainReliefMath.NONE) return original;
        double continent = continents.compute(ctx);
        if (MountainReliefMath.inlandUplandGate(continent, original) <= 0.0)
            return original;
        double radiusFraction = Math.hypot(dx, dz) / s.radius;
        return MountainReliefMath.relief(original, s.anchorHeight,
                continent, s.type, radiusFraction, basinDepth, plateauRise);
    }

    @Override public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply(new MountainReliefDensity(base.mapAll(visitor),
                continents.mapAll(visitor), selector.mapAll(visitor),
                basinDepth, plateauRise));
    }
    @Override public double minValue() { return base.minValue() - basinDepth; }
    @Override public double maxValue() {
        return base.maxValue() + Math.max(plateauRise, basinDepth * 0.15);
    }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return DISPATCH; }
}
