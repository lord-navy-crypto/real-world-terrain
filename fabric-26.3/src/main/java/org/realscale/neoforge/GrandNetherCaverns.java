package org.realscale.neoforge;

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
 * Seed-warped, continuous upper-Nether cave density that creates large hollow
 * mountain interiors and connected 3D natural tunnels. Vanilla terrain
 * remains unchanged below Y144 and above Y440.
 */
public record GrandNetherCaverns(DensityFunction base,DensityFunction warp)
        implements DensityFunction {
    public static final MapCodec<GrandNetherCaverns> MAP_CODEC =
        RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.CODEC.fieldOf("base").forGetter(GrandNetherCaverns::base),
            DensityFunction.CODEC.fieldOf("warp").forGetter(GrandNetherCaverns::warp)
        ).apply(i, GrandNetherCaverns::new));
    public double compute(RealScaleDensityContext context) {
        if(!DensityPerfSampler.ENABLED)return computeUnprofiled(context);
        long start=DensityPerfSampler.begin(DensityPerfSampler.Stage.NETHER_CAVE);
        try{return computeUnprofiled(context);}
        finally{DensityPerfSampler.finish(DensityPerfSampler.Stage.NETHER_CAVE,start);}
    }

    private double computeUnprofiled(RealScaleDensityContext context) {
        double original=context.compute(base);
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
        if(!GrandNetherCaveNetwork.mayContainVoidNearColumn(x,z))
            return original;
        // Gently distort ideal ellipsoids and straight corridors using a
        // smooth world-seeded 3D noise field. Less than the radius of a
        // connector, preserving walkable links across all chunk borders.
        // Keep the seeded warp continuous: rounding to whole blocks caused
        // artificial, stair-stepped cave walls in the first prototype.
        double displacement=Math.max(-1.0,Math.min(1.0,context.compute(warp)));
        double strength=GrandNetherCaveNetwork.strengthFromColumn(
                x,z,x+displacement*5.0, y+displacement*2.0,
                z-displacement*4.0);
        return strength<=0 ? original : Math.min(original,-1.8*strength);
    }
        @Override public DensitySampler compileSampler(DensityFunction.CompileContext context) {
        return RealScaleDensitySamplerSupport.compile(context, new DensityFunction[]{base, warp}, this::compute);
    }
    @Override public Interval range() { return Interval.INFINITE; }
    @Override public int domainAxes() { return DensityFunction.ALL_AXES; }

@Override public DensityFunction rewriteChildren(DfRewriteRule rule) {
        return new GrandNetherCaverns(rule.rewrite(base),
                                                    rule.rewrite(warp));
    }
    
    
    @Override public MapCodec<? extends DensityFunction> codec(){
        return MAP_CODEC;
    }
}
