package org.realscale.neoforge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Opt-in integrated USGS + 24 landforms + REAL OCEAN floor morphology.
 *
 * Ocean floor signal is added at the terrain-density stage, before the
 * existing exact cave range and Grand Overworld caverns. Seawater, aquifers,
 * sea level, ocean biome source, decoration, shipwrecks and monuments are
 * all delegated to the unchanged original noise settings.
 *
 * Uses world-absolute 8192-block sites, original ocean continentalness
 * and original procedural height. The imported Big Sur DEM and its boundary
 * transition are protected using the exact source's measured fade weight.
 */
public final class MarineSeafloorDensity implements DensityFunction.SimpleFunction {
    private final DensityFunction base,continents,proceduralHeight,selector;
    private final int measuredBlendMeters;
    private final ThreadLocal<Cache> cache=ThreadLocal.withInitial(Cache::new);
    private static final class Cache {
        int x=Integer.MIN_VALUE,z=Integer.MIN_VALUE,kind;
        boolean hasKind;
        void locate(int cx,int cz){
            if(x!=cx||z!=cz){x=cx;z=cz;hasKind=false;}
        }
    }
    public MarineSeafloorDensity(DensityFunction base,DensityFunction continents,
                                 DensityFunction proceduralHeight,
                                 DensityFunction selector,int measuredBlendMeters){
        this.base=base;
        this.continents=continents;
        this.proceduralHeight=proceduralHeight;
        this.selector=selector;
        this.measuredBlendMeters=measuredBlendMeters;
    }
    public DensityFunction base(){return base;}
    public DensityFunction continents(){return continents;}
    public DensityFunction proceduralHeight(){return proceduralHeight;}
    public DensityFunction selector(){return selector;}
    public int measuredBlendMeters(){return measuredBlendMeters;}
    public static final MapCodec<MarineSeafloorDensity> MAP_CODEC=
            RecordCodecBuilder.mapCodec(i->i.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("base")
                            .forGetter(MarineSeafloorDensity::base),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("continents")
                            .forGetter(MarineSeafloorDensity::continents),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("procedural_height")
                            .forGetter(MarineSeafloorDensity::proceduralHeight),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("selector")
                            .forGetter(MarineSeafloorDensity::selector),
                    Codec.intRange(256,4096).fieldOf("measured_blend_meters")
                            .forGetter(MarineSeafloorDensity::measuredBlendMeters)
            ).apply(i,MarineSeafloorDensity::new));
    private static final KeyDispatchDataCodec<MarineSeafloorDensity> DISPATCH=
            KeyDispatchDataCodec.of(MAP_CODEC);

    @Override public double compute(DensityFunction.FunctionContext ctx){
        final double measuredOrNatural=base.compute(ctx);
        final double continent=continents.compute(ctx);
        if(continent>=-0.15)return measuredOrNatural;
        final int x=ctx.blockX(),z=ctx.blockZ();
        final int cx=Math.floorDiv(x,OceanFloorMorphologyMath.CELL);
        final int cz=Math.floorDiv(z,OceanFloorMorphologyMath.CELL);
        final long hash=OceanFloorMorphologyMath.hash(cx,cz);
        final int sx=OceanFloorMorphologyMath.centerX(cx,hash);
        final int sz=OceanFloorMorphologyMath.centerZ(cz,hash);
        final double dx=(double)x-sx,dz=(double)z-sz;
        final double radius=OceanFloorMorphologyMath.radius(hash);
        if(dx*dx+dz*dz>=radius*radius*1.081600001)return measuredOrNatural;
        final double height=proceduralHeight.compute(ctx);
        if(OceanFloorMorphologyMath.marineGate(continent,height)<=0)return measuredOrNatural;

        // Exact same DEM coverage/interpolation fade as original USGS.
        // 1 inside the genuine tile, smoothly 1->0 in its border ring,
        // 0 outside measured coverage.
        final double demWeight=RealDemSeamlessMath.cachedWeightOutside(x,z,measuredBlendMeters);
        if(demWeight>=1)return measuredOrNatural;

        final Cache site=cache.get();
        site.locate(cx,cz);
        if(!site.hasKind){
            var anchor=new DensityFunction.SinglePointContext(sx,0,sz);
            site.kind=OceanFloorMorphologyMath.kind(hash,selector.compute(anchor));
            site.hasKind=true;
        }
        final double delta=OceanFloorMorphologyMath.delta(site.kind,dx,dz,radius,
                continent,height,demWeight);
        if(delta==0)return measuredOrNatural;
        return measuredOrNatural+delta;
    }
    @Override public DensityFunction mapAll(DensityFunction.Visitor visitor){
        return visitor.apply(new MarineSeafloorDensity(
                base.mapAll(visitor),continents.mapAll(visitor),
                proceduralHeight.mapAll(visitor),selector.mapAll(visitor),
                measuredBlendMeters));
    }
    @Override public double minValue(){return base.minValue()-3;}
    @Override public double maxValue(){return base.maxValue()+3;}
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec(){return DISPATCH;}
}
