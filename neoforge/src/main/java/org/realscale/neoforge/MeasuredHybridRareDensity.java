package org.realscale.neoforge;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Measured/USGS world: preserve original measured DEM or its existing
 * procedural fallback except in a sparse, bounded natural landmark footprint.
 * Inside a rare site the source becomes procedural + selected landform effect;
 * the smooth transition combines measured and synthetic solid densities.
 *
 * This wraps solid terrain BEFORE the original caverns/range-min network.
 */
public final class MeasuredHybridRareDensity implements DensityFunction.SimpleFunction {
    private final DensityFunction measured,procedural,proceduralHeight,continents,selector;
    private final ThreadLocal<Cell> cellCache=ThreadLocal.withInitial(Cell::new);
    private static final class Cell {
        int x=Integer.MIN_VALUE,z=Integer.MIN_VALUE,kind;
        boolean valid;
        void reset(int cx,int cz){
            if(cx!=x || cz!=z){x=cx;z=cz;valid=false;}
        }
    }
    public MeasuredHybridRareDensity(DensityFunction measured,DensityFunction procedural,
                                     DensityFunction proceduralHeight,
                                     DensityFunction continents,DensityFunction selector) {
        this.measured=measured;
        this.procedural=procedural;
        this.proceduralHeight=proceduralHeight;
        this.continents=continents;
        this.selector=selector;
    }
    public DensityFunction measured(){return measured;}
    public DensityFunction procedural(){return procedural;}
    public DensityFunction proceduralHeight(){return proceduralHeight;}
    public DensityFunction continents(){return continents;}
    public DensityFunction selector(){return selector;}
    public static final MapCodec<MeasuredHybridRareDensity> MAP_CODEC=
            RecordCodecBuilder.mapCodec(i->i.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("measured").forGetter(MeasuredHybridRareDensity::measured),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("procedural").forGetter(MeasuredHybridRareDensity::procedural),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("procedural_height").forGetter(MeasuredHybridRareDensity::proceduralHeight),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("continents").forGetter(MeasuredHybridRareDensity::continents),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("selector").forGetter(MeasuredHybridRareDensity::selector)
            ).apply(i,MeasuredHybridRareDensity::new));
    private static final KeyDispatchDataCodec<MeasuredHybridRareDensity> DISPATCH=
            KeyDispatchDataCodec.of(MAP_CODEC);

    @Override public double compute(DensityFunction.FunctionContext ctx){
        final double recorded=measured.compute(ctx);
        int x=ctx.blockX(),z=ctx.blockZ();
        int cx=Math.floorDiv(x,RegionalLandformMath.CELL_V3);
        int cz=Math.floorDiv(z,RegionalLandformMath.CELL_V3);
        long hash=RegionalLandformMath.hashV3(cx,cz);
        if(!MeasuredHybridRareMath.isRare(hash))return recorded;
        int sx=RegionalLandformMath.centerXV3(cx,hash);
        int sz=RegionalLandformMath.centerZV3(cz,hash);
        double dx=(double)x-sx,dz=(double)z-sz;
        double radius=RegionalLandformMath.radiusV3(hash);
        if(dx*dx+dz*dz>=radius*radius*1.08160001)return recorded;
        double continent=continents.compute(ctx);
        if(continent<=-0.02)return recorded;
        // The procedural height signal is horizontal-only. Cache via the
        // registered measured_horizontal_memo source, never infer from Y.
        double height=proceduralHeight.compute(ctx);
        double w=MeasuredHybridRareMath.weight(hash,dx,dz,radius,continent,height);
        if(w<=0)return recorded;
        Cell cell=cellCache.get();
        cell.reset(cx,cz);
        if(!cell.valid){
            var anchor=new DensityFunction.SinglePointContext(sx,0,sz);
            cell.kind=RegionalLandformMath.kindV3(hash,selector.compute(anchor));
            cell.valid=true;
        }
        // Shape terms are measured in the same HORIZONTAL density units as
        // realism_max/height_signal; vertical gradient remains unchanged.
        double synthetic=procedural.compute(ctx)
                +RegionalLandformMath.effect(cell.kind,dx,dz,radius);
        return MeasuredHybridRareMath.combine(recorded,synthetic,w);
    }
    @Override public DensityFunction mapAll(DensityFunction.Visitor visitor){
        return visitor.apply(new MeasuredHybridRareDensity(
                measured.mapAll(visitor),procedural.mapAll(visitor),
                proceduralHeight.mapAll(visitor),continents.mapAll(visitor),
                selector.mapAll(visitor)));
    }
    @Override public double minValue(){
        return Math.min(measured.minValue(),procedural.minValue()-7.0);
    }
    @Override public double maxValue(){
        return Math.max(measured.maxValue(),procedural.maxValue()+8.0);
    }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec(){return DISPATCH;}
}
