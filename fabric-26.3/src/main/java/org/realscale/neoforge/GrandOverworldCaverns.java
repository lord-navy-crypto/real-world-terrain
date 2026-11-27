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
 * Underground-domain pipeline for the single RealScale Overworld.
 * The base density belongs to the surface domain; the cave network and
 * original small-cave noise belong to the underground domain. Their results
 * are composed by RealScaleDomainComposition into the one shared chunk field.
 * Cave network + original small-cave noise are used only well INSIDE solid
 * mountain strata. Original surface/noise/ocean/depth routing stays intact.
 *
 * Unlike a post-generation feature, this subtraction happens in final_density,
 * before blocks are made, and so can excavate enormous real tunnel volumes.
 */
public record GrandOverworldCaverns(DensityFunction base, DensityFunction warp,
                                    DensityFunction smallCaves, boolean galleryV2)
        implements DensityFunction {
    /** Existing codecs and saves preserve their original three-field form. */
    public GrandOverworldCaverns(DensityFunction base,DensityFunction warp,
            DensityFunction smallCaves) {
        this(base,warp,smallCaves,false);
    }
    public static final MapCodec<GrandOverworldCaverns> MAP_CODEC =
            RecordCodecBuilder.mapCodec(i -> i.group(
                    DensityFunction.CODEC.fieldOf("base").forGetter(GrandOverworldCaverns::base),
                    DensityFunction.CODEC.fieldOf("warp").forGetter(GrandOverworldCaverns::warp),
                    DensityFunction.CODEC.fieldOf("small_caves").forGetter(GrandOverworldCaverns::smallCaves),
                    Codec.BOOL.optionalFieldOf("gallery_v2",false).forGetter(GrandOverworldCaverns::galleryV2)
            ).apply(i,GrandOverworldCaverns::new));
    /**
     * Protect at least ~30 blocks of density-equivalent roof near the surface.
     * 0.24 corresponds to ~31 vertical blocks in the canonical density slope,
     * but is a density threshold, NOT a full terrain-depth collision test.
     * Preserve the aquifers and the protected ocean/sea floor.
     */
    static double carve(double original,double tiny,double strength) {
        return RealScaleDomainComposition.compose(original,tiny,strength);
    }

    public double compute(RealScaleDensityContext ctx) {
        if(!DensityPerfSampler.ENABLED)
            return galleryV2?computeExpanded(ctx):computeUnprofiled(ctx);
        long start=DensityPerfSampler.begin(DensityPerfSampler.Stage.OVERWORLD_CAVE);
        try{return galleryV2?computeExpanded(ctx):computeUnprofiled(ctx);}
        finally{DensityPerfSampler.finish(DensityPerfSampler.Stage.OVERWORLD_CAVE,start);}
    }

    private double computeUnprofiled(RealScaleDensityContext ctx) {
        double original=ctx.compute(base);
        int y=ctx.blockY();
        if(y<GrandOverworldCaveNetwork.MIN_Y
                || y>GrandOverworldCaveNetwork.MAX_Y
                || original<=0.0)
            return original;
        // Rare openings from existing halls let players FIND the caves.
        // Restrict entrances to high ground, never ordinary oceans, rivers,
        // or the exposed sea floor.
        double entrance=y>=110
                ? GrandOverworldCaveNetwork.entranceStrength(
                    ctx.blockX(),y,ctx.blockZ()) : 0;
        if(original<=0.24)
            return entrance>0 ? Math.min(original,-2.0*entrance) : original;
        // Giant cave geometry cannot reach this high even after the +/-2 Y
        // warp. In lower layers, most X/Z columns likewise cannot intersect
        // any giant cave passage.
        // The network's conservative bounds account for every possible
        // +/-6 X and +/-5 Z displacement from the seeded warp. When they
        // prove no cave can exist here, skip both warp noise and the 3D
        // corridor graph. Keep small caves and rare entrances unchanged.
        int blockX=ctx.blockX(),blockZ=ctx.blockZ();
        // An upper-layer rejection needs no column mask at all. Otherwise
        // compute the candidate mask ONCE for both fast exclusion and
        // the (potentially expensive) warped 3D cave strength evaluation.
        long columnMask=y>GrandOverworldCaveNetwork.MAX_GEOMETRY_Y+2
                ? 0L : GrandOverworldCaveNetwork.componentMaskForColumn(
                        blockX,blockZ);
        if (columnMask==0L) {
            double chosen=carve(original,ctx.compute(smallCaves),0.0);
            return Math.min(chosen,entrance>0 ? -2.0*entrance : original);
        }
        // The vanilla-seeded noise provides world-seed-dependent detail.
        // It never changes the room/corridor topology or chunk-border logic.
        double shift=Math.max(-1.0,Math.min(1.0,ctx.compute(warp)));
        double strength=GrandOverworldCaveNetwork.strengthFromColumnWithMask(
                blockX,blockZ,
                blockX+shift*6.0, y+shift*2.0,
                blockZ-shift*5.0,columnMask);
        // Within a sufficiently strong giant void, the network already
        // wins against the documented lower bound of the small-cave noise.
        // Skip three additional noise evaluations in those voxels without
        // changing final density; other codecs with lower bounds still work.
        double giantVoid = strength>0 ? -2.4*strength : original;
        double chosen;
        if (strength>0 && giantVoid<=Double.NEGATIVE_INFINITY) {
            chosen=giantVoid;
        } else {
            chosen=carve(original,ctx.compute(smallCaves),strength);
        }
        return Math.min(chosen,entrance>0 ? -2.0*entrance : original);
    }

    /**
     * Expanded cave topology selected by an explicit saved noise-setting flag.
     * Natural Automate and the dedicated Cave Exploration preset enable it;
     * older noise settings omit the flag and retain the legacy network exactly.
     * The added loops and tier-shaped annexes remain deterministic and bounded.
     */
    private double computeExpanded(RealScaleDensityContext ctx) {
        double original=ctx.compute(base);
        int y=ctx.blockY();
        if(y<GrandOverworldCaveNetwork.MIN_Y
                ||y>GrandOverworldCaveNetwork.MAX_Y || original<=0.0)
            return original;
        int x=ctx.blockX(),z=ctx.blockZ();
        double entrance=y>=110
                ?GrandOverworldCaveNetwork.entranceStrength(x,y,z):0;
        if(original<=0.24)
            return entrance>0?Math.min(original,-2.0*entrance):original;
        long mask=y>GrandOverworldCaveNetwork.MAX_GEOMETRY_Y+2
                ?0L:GrandOverworldCaveNetwork.componentMaskForColumn(x,z);
        int extraMask=(y<GrandOverworldCaveDesignV2.MIN_EXTRA_Y-2
                ||y>GrandOverworldCaveDesignV2.MAX_EXTRA_Y+2)
                ?0:GrandOverworldCaveDesignV2.componentMaskForColumn(x,z);
        boolean extra=GrandOverworldCaveDesignV2.mayTouchColumn(x,y,z,extraMask);
        if(mask==0L && !extra) {
            double chosen=carve(original,ctx.compute(smallCaves),0);
            return Math.min(chosen,entrance>0?-2.0*entrance:original);
        }
        double shift=Math.max(-1.0,Math.min(1.0,ctx.compute(warp)));
        double wx=x+shift*6.0,wy=y+shift*2.0,wz=z-shift*5.0;
        double existing=mask!=0L
                ?GrandOverworldCaveNetwork.strengthFromColumnWithMask(
                        x,z,wx,wy,wz,mask):0;
        double addition=extra?GrandOverworldCaveDesignV2.strengthFromColumn(
                x,z,wx,wy,wz,extraMask):0;
        double strength=Math.max(existing,addition);
        double giantVoid=strength>0?-2.4*strength:original;
        double chosen=(strength>0 && giantVoid<=Double.NEGATIVE_INFINITY)
                ?giantVoid:carve(original,ctx.compute(smallCaves),strength);
        return Math.min(chosen,entrance>0?-2.0*entrance:original);
    }

        @Override public DensitySampler compileSampler(DensityFunction.CompileContext context) {
        return RealScaleDensitySamplerSupport.compile(context, new DensityFunction[]{base, warp, smallCaves}, this::compute);
    }
    @Override public Interval range() { return Interval.INFINITE; }
    @Override public int domainAxes() { return DensityFunction.ALL_AXES; }

@Override public DensityFunction rewriteChildren(DfRewriteRule rule) {
        return new GrandOverworldCaverns(rule.rewrite(base),
                rule.rewrite(warp),rule.rewrite(smallCaves),galleryV2);
    }
    
    
    @Override public MapCodec<? extends DensityFunction> codec(){return MAP_CODEC;}
}
