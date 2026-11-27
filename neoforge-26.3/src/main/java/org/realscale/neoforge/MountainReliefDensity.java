package org.realscale.neoforge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.util.Interval;

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
public final class MountainReliefDensity implements DensityFunction {
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
                    DensityFunction.CODEC.fieldOf("base").forGetter(MountainReliefDensity::base),
                    DensityFunction.CODEC.fieldOf("continents").forGetter(MountainReliefDensity::continents),
                    DensityFunction.CODEC.fieldOf("selector").forGetter(MountainReliefDensity::selector),
                    Codec.doubleRange(0.0, 1.9).fieldOf("basin_depth").forGetter(MountainReliefDensity::basinDepth),
                    Codec.doubleRange(0.0, 1.3).fieldOf("plateau_rise").forGetter(MountainReliefDensity::plateauRise)
            ).apply(i, MountainReliefDensity::new));
    private MountainReliefSiteCache.Site site(RealScaleDensityContext ctx) {
        int gx=Math.floorDiv(ctx.blockX(),MountainReliefMath.CELL);
        int gz=Math.floorDiv(ctx.blockZ(),MountainReliefMath.CELL);
        return current.get().get(gx,gz);
    }

    private void classify(MountainReliefSiteCache.Site site, RealScaleDensityContext ctx) {
        if (site.classified) return;
        var anchor=ctx.at(
                Math.toIntExact((long)site.centerX),0,
                Math.toIntExact((long)site.centerZ));
        double regionalSignal=anchor.compute(selector);
        site.type=MountainReliefMath.kind(site.hash,regionalSignal);
        site.anchorHeight=site.type==MountainReliefMath.PLATEAU
                ||site.type==MountainReliefMath.MESA
                ?MountainReliefMath.plateauLevel(site.hash,regionalSignal):0;
        site.classified=true;
    }

    public double compute(RealScaleDensityContext ctx) {
        if(!DensityPerfSampler.ENABLED)return computeUnprofiled(ctx);
        long started=DensityPerfSampler.begin(DensityPerfSampler.Stage.MOUNTAIN_RELIEF);
        try{return computeUnprofiled(ctx);}
        finally{DensityPerfSampler.finish(DensityPerfSampler.Stage.MOUNTAIN_RELIEF,started);}
    }

    private double computeUnprofiled(RealScaleDensityContext ctx) {
        double original = ctx.compute(base);
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
        classify(s,ctx);
        if (s.type == MountainReliefMath.NONE) return original;
        // Type-specific support is narrower than the common basin envelope:
        // mesas vanish at radiusFraction >= 0.58, plateaus at >= 1.0.
        // Reject only mathematically zero relief, with conservative slack,
        // BEFORE computing potentially expensive continental noise.
        if (MountainReliefMath.definitelyOutsideActiveRelief(
                s.type, dx, dz, s.radius)) return original;
        double continent = ctx.compute(continents);
        double gate = MountainReliefMath.inlandUplandGate(continent, original);
        if (gate <= 0.0) return original;
        double radiusFraction = Math.hypot(dx, dz) / s.radius;
        return MountainReliefMath.reliefWithGate(original, s.anchorHeight,
                continent, s.type, radiusFraction, basinDepth, plateauRise, gate);
    }

        @Override public DensitySampler compileSampler(DensityFunction.CompileContext context) {
        return RealScaleDensitySamplerSupport.compile(context, new DensityFunction[]{base, continents, selector}, this::compute);
    }
    @Override public Interval range() { return Interval.INFINITE; }
    @Override public int domainAxes() { return DensityFunction.ALL_AXES; }

@Override public DensityFunction rewriteChildren(DfRewriteRule rule) {
        return new MountainReliefDensity(rule.rewrite(base),
                rule.rewrite(continents), rule.rewrite(selector),
                basinDepth, plateauRise);
    }
    
    
    @Override public MapCodec<? extends DensityFunction> codec() { return MAP_CODEC; }
}
