package org.realscale.neoforge;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Second versioned opt-in large-scale terrain, active only in the new Natural
 * Exploration v2 noise_settings ID. Never wraps measured USGS DEM or changes V1 saves.
 * Changes horizontal height signal before vertical gradient and caves.
 */
public final class CinematicLandformDensityV2 implements DensityFunction.SimpleFunction {
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
            hash=CinematicLandformMathV2.hash(cx,cz);
            x=CinematicLandformMathV2.siteX(cx,hash);
            z=CinematicLandformMathV2.siteZ(cz,hash);
            radius=CinematicLandformMathV2.radius(hash);
            classified=false;
        }
    }
    public CinematicLandformDensityV2(DensityFunction base,DensityFunction continents,
                                    DensityFunction temperature,DensityFunction humidity) {
        this.base=base;this.continents=continents;
        this.temperature=temperature;this.humidity=humidity;
    }
    public DensityFunction base(){return base;}
    public DensityFunction continents(){return continents;}
    public DensityFunction temperature(){return temperature;}
    public DensityFunction humidity(){return humidity;}
    public static final MapCodec<CinematicLandformDensityV2> MAP_CODEC =
            RecordCodecBuilder.mapCodec(i->i.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("base")
                            .forGetter(CinematicLandformDensityV2::base),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("continents")
                            .forGetter(CinematicLandformDensityV2::continents),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("temperature")
                            .forGetter(CinematicLandformDensityV2::temperature),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("humidity")
                            .forGetter(CinematicLandformDensityV2::humidity)
            ).apply(i,CinematicLandformDensityV2::new));
    private static final KeyDispatchDataCodec<CinematicLandformDensityV2> DISPATCH=
            KeyDispatchDataCodec.of(MAP_CODEC);

    @Override public double compute(DensityFunction.FunctionContext ctx) {
        double before=base.compute(ctx);
        if(before<=0.82)return before;
        int x=ctx.blockX(),z=ctx.blockZ();
        Site site=local.get();
        site.prepare(Math.floorDiv(x,CinematicLandformMathV2.CELL),
                     Math.floorDiv(z,CinematicLandformMathV2.CELL));
        double dx=(double)x-site.x,dz=(double)z-site.z;
        if(dx*dx+dz*dz>=site.radius*site.radius)return before;
        double continental=continents.compute(ctx);
        if(CinematicLandformMathV2.gate(continental,before)==0)return before;
        if(!site.classified) {
            DensityFunction.SinglePointContext anchor=
                    new DensityFunction.SinglePointContext(site.x,0,site.z);
            site.kind=CinematicLandformMathV2.kind(site.hash,
                    temperature.compute(anchor),humidity.compute(anchor));
            site.classified=true;
        }
        if(site.kind==CinematicLandformMathV2.NONE)return before;
        return CinematicLandformMathV2.apply(site.kind,before,dx,dz,
                site.radius,site.hash,continental);
    }
    @Override public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply(new CinematicLandformDensityV2(base.mapAll(visitor),
                continents.mapAll(visitor),temperature.mapAll(visitor),
                humidity.mapAll(visitor)));
    }
    @Override public double minValue(){return base.minValue()-2.0;}
    @Override public double maxValue(){return base.maxValue()+4.0;}
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec(){return DISPATCH;}
}
