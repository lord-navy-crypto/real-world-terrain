package org.realscale.neoforge;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Versioned opt-in large-scale terrain, active only in the new Natural
 * Exploration noise_settings ID. Does NOT wrap measured USGS DEM.
 * Changes horizontal height signal before vertical gradient and caves.
 */
public final class CinematicLandformDensity implements DensityFunction.SimpleFunction {
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
            hash=CinematicLandformMath.hash(cx,cz);
            x=CinematicLandformMath.siteX(cx,hash);
            z=CinematicLandformMath.siteZ(cz,hash);
            radius=CinematicLandformMath.radius(hash);
            classified=false;
        }
    }
    public CinematicLandformDensity(DensityFunction base,DensityFunction continents,
                                    DensityFunction temperature,DensityFunction humidity) {
        this.base=base;this.continents=continents;
        this.temperature=temperature;this.humidity=humidity;
    }
    public DensityFunction base(){return base;}
    public DensityFunction continents(){return continents;}
    public DensityFunction temperature(){return temperature;}
    public DensityFunction humidity(){return humidity;}
    public static final MapCodec<CinematicLandformDensity> MAP_CODEC =
            RecordCodecBuilder.mapCodec(i->i.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("base")
                            .forGetter(CinematicLandformDensity::base),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("continents")
                            .forGetter(CinematicLandformDensity::continents),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("temperature")
                            .forGetter(CinematicLandformDensity::temperature),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("humidity")
                            .forGetter(CinematicLandformDensity::humidity)
            ).apply(i,CinematicLandformDensity::new));
    private static final KeyDispatchDataCodec<CinematicLandformDensity> DISPATCH=
            KeyDispatchDataCodec.of(MAP_CODEC);

    @Override public double compute(DensityFunction.FunctionContext ctx) {
        double before=base.compute(ctx);
        if(before<=0.82)return before;
        int x=ctx.blockX(),z=ctx.blockZ();
        Site site=local.get();
        site.prepare(Math.floorDiv(x,CinematicLandformMath.CELL),
                     Math.floorDiv(z,CinematicLandformMath.CELL));
        double dx=(double)x-site.x,dz=(double)z-site.z;
        if(dx*dx+dz*dz>=site.radius*site.radius)return before;
        double continental=continents.compute(ctx);
        if(CinematicLandformMath.gate(continental,before)==0)return before;
        if(!site.classified) {
            DensityFunction.SinglePointContext anchor=
                    new DensityFunction.SinglePointContext(site.x,0,site.z);
            site.kind=CinematicLandformMath.kind(site.hash,
                    temperature.compute(anchor),humidity.compute(anchor));
            site.classified=true;
        }
        if(site.kind==CinematicLandformMath.NONE)return before;
        return CinematicLandformMath.apply(site.kind,before,dx,dz,
                site.radius,site.hash,continental);
    }
    @Override public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply(new CinematicLandformDensity(base.mapAll(visitor),
                continents.mapAll(visitor),temperature.mapAll(visitor),
                humidity.mapAll(visitor)));
    }
    @Override public double minValue(){return base.minValue()-2.0;}
    @Override public double maxValue(){return base.maxValue()+3.8;}
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec(){return DISPATCH;}
}
