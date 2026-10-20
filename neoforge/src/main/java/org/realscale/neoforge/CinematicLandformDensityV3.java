package org.realscale.neoforge;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Third versioned opt-in northern glacial terrain, active in new Natural
 * Exploration v3 noise_settings. Leaves measured DEM and old V1/V2 saves unchanged.
 * Changes horizontal height signal before vertical gradient and caves.
 */
public final class CinematicLandformDensityV3 implements DensityFunction.SimpleFunction {
    private final DensityFunction base,continents,temperature,humidity;
    private final ThreadLocal<Site> local=ThreadLocal.withInitial(Site::new);
    private static final class Site {
        int cellX=Integer.MIN_VALUE,cellZ=Integer.MIN_VALUE,kind;
        boolean classified;
        long hash;
        int x,z;
        double radius;
        void prepare(int cx,int cz) {
            if(cx==cellX&&cz==cellZ)return;
            cellX=cx;cellZ=cz;
            hash=CinematicLandformMathV3.hash(cx,cz);
            x=CinematicLandformMathV3.siteX(cx,hash);
            z=CinematicLandformMathV3.siteZ(cz,hash);
            radius=CinematicLandformMathV3.radius(hash);
            classified=false;
        }
    }
    public CinematicLandformDensityV3(DensityFunction base,DensityFunction continents,
                                    DensityFunction temperature,DensityFunction humidity) {
        this.base=base;this.continents=continents;
        this.temperature=temperature;this.humidity=humidity;
    }
    public DensityFunction base(){return base;}
    public DensityFunction continents(){return continents;}
    public DensityFunction temperature(){return temperature;}
    public DensityFunction humidity(){return humidity;}
    public static final MapCodec<CinematicLandformDensityV3> MAP_CODEC =
            RecordCodecBuilder.mapCodec(i->i.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("base")
                            .forGetter(CinematicLandformDensityV3::base),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("continents")
                            .forGetter(CinematicLandformDensityV3::continents),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("temperature")
                            .forGetter(CinematicLandformDensityV3::temperature),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("humidity")
                            .forGetter(CinematicLandformDensityV3::humidity)
            ).apply(i,CinematicLandformDensityV3::new));
    private static final KeyDispatchDataCodec<CinematicLandformDensityV3> DISPATCH=
            KeyDispatchDataCodec.of(MAP_CODEC);

    @Override public double compute(DensityFunction.FunctionContext ctx) {
        double before=base.compute(ctx);
        if(before<=0.82)return before;
        int x=ctx.blockX(),z=ctx.blockZ();
        Site site=local.get();
        site.prepare(Math.floorDiv(x,CinematicLandformMathV3.CELL),
                     Math.floorDiv(z,CinematicLandformMathV3.CELL));
        double dx=(double)x-site.x,dz=(double)z-site.z;
        if(dx*dx+dz*dz>=site.radius*site.radius)return before;
        double continental=continents.compute(ctx);
        if(CinematicLandformMathV3.gate(continental,before)==0)return before;
        if(!site.classified) {
            DensityFunction.SinglePointContext anchor=
                    new DensityFunction.SinglePointContext(site.x,0,site.z);
            site.kind=CinematicLandformMathV3.kind(site.hash,
                    temperature.compute(anchor),humidity.compute(anchor));
            site.classified=true;
        }
        if(site.kind==CinematicLandformMathV3.NONE)return before;
        return CinematicLandformMathV3.apply(site.kind,before,dx,dz,
                site.radius,site.hash,continental);
    }
    @Override public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply(new CinematicLandformDensityV3(base.mapAll(visitor),
                continents.mapAll(visitor),temperature.mapAll(visitor),
                humidity.mapAll(visitor)));
    }
    @Override public double minValue(){return base.minValue()-2.0;}
    @Override public double maxValue(){return base.maxValue()+4.0;}
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec(){return DISPATCH;}
}
