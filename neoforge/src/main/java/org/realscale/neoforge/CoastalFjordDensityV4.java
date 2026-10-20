package org.realscale.neoforge;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Experimental opt-in v4 coastal fjord height-density engine for NeoForge 1.21.1.
 * Consumes the archived v3 height signal; never alters v1/v2/v3 codecs.
 * Selects cold, wet sites with genuine ocean and inland samples in a fixed
 * coastal direction, then erodes a continuous submerged U-shaped corridor.
 * Does not write blocks, water, biomes, chunks, or saved world metadata.
 */
public final class CoastalFjordDensityV4 implements DensityFunction.SimpleFunction {
    private final DensityFunction base,continents,temperature,humidity;
    private final ThreadLocal<Site> sites=ThreadLocal.withInitial(Site::new);
    private static final class Site {
        int cx=Integer.MIN_VALUE,cz=Integer.MIN_VALUE,x,z;
        long hash;
        boolean classified,enabled;
        double angle,phase;
        void prepare(int nextX,int nextZ) {
            if(cx==nextX&&cz==nextZ)return;
            cx=nextX;cz=nextZ;
            hash=FjordMath.hash(cx,cz);
            x=FjordMath.siteX(cx,hash);
            z=FjordMath.siteZ(cz,hash);
            phase=FjordMath.phase(hash);
            classified=false;
            enabled=false;
        }
    }
    public CoastalFjordDensityV4(DensityFunction base,DensityFunction continents,
                                  DensityFunction temperature,DensityFunction humidity) {
        this.base=base;this.continents=continents;
        this.temperature=temperature;this.humidity=humidity;
    }
    public DensityFunction base(){return base;}
    public DensityFunction continents(){return continents;}
    public DensityFunction temperature(){return temperature;}
    public DensityFunction humidity(){return humidity;}

    public static final MapCodec<CoastalFjordDensityV4> MAP_CODEC=
            RecordCodecBuilder.mapCodec(i->i.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("base")
                            .forGetter(CoastalFjordDensityV4::base),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("continents")
                            .forGetter(CoastalFjordDensityV4::continents),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("temperature")
                            .forGetter(CoastalFjordDensityV4::temperature),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("humidity")
                            .forGetter(CoastalFjordDensityV4::humidity)
            ).apply(i,CoastalFjordDensityV4::new));
    private static final KeyDispatchDataCodec<CoastalFjordDensityV4> DISPATCH=
            KeyDispatchDataCodec.of(MAP_CODEC);

    private double at(DensityFunction fn,double x,double z) {
        return fn.compute(new DensityFunction.SinglePointContext(
                (int)Math.round(x),0,(int)Math.round(z)));
    }
    private void classify(Site site) {
        site.classified=true;
        // A fjord must reach cold marine water, not make a landlocked lake.
        if(at(temperature,site.x,site.z)>-0.22
                ||at(humidity,site.x,site.z)<-0.18)return;
        double best=0;
        int first=(int)((site.hash>>>53)&7L);
        for(int step=0;step<8;step++) {
            double angle=((first+step)&7)*(Math.PI/4.0);
            double c=Math.cos(angle),s=Math.sin(angle);
            double seaX=site.x-1100*c,seaZ=site.z-1100*s;
            double outerX=site.x-1340*c,outerZ=site.z-1340*s;
            double landX=site.x+900*c,landZ=site.z+900*s;
            double seaCont=at(continents,seaX,seaZ);
            double outerCont=at(continents,outerX,outerZ);
            double landCont=at(continents,landX,landZ);
            if(seaCont>=-0.07||outerCont>=-0.08||landCont<=0.16)
                continue;
            double seaHeight=at(base,seaX,seaZ);
            double outerHeight=at(base,outerX,outerZ);
            double landHeight=at(base,landX,landZ);
            if(seaHeight>=-0.06||outerHeight>=-0.04
                    ||landHeight<=0.42)continue;
            double score=(landCont-seaCont)+0.12*(landHeight-seaHeight);
            if(score>best) {
                best=score;site.angle=angle;site.enabled=true;
            }
        }
    }
    @Override public double compute(DensityFunction.FunctionContext ctx) {
        double before=base.compute(ctx);
        int x=ctx.blockX(),z=ctx.blockZ();
        Site site=sites.get();
        site.prepare(Math.floorDiv(x,FjordMath.CELL),
                     Math.floorDiv(z,FjordMath.CELL));
        double dx=(double)x-site.x,dz=(double)z-site.z;
        if(dx*dx+dz*dz>=FjordMath.RADIUS*FjordMath.RADIUS)return before;
        if(!site.classified)classify(site);
        if(!site.enabled)return before;
        return FjordMath.carve(before,dx,dz,site.angle,site.phase);
    }
    @Override public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply(new CoastalFjordDensityV4(base.mapAll(visitor),
                continents.mapAll(visitor),temperature.mapAll(visitor),
                humidity.mapAll(visitor)));
    }
    @Override public double minValue(){return Math.min(base.minValue(),-1.0);}
    @Override public double maxValue(){return base.maxValue();}
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec(){return DISPATCH;}

    // === BEGIN INDEPENDENT FJORD MATH ===
    /** The following nested class compiles without any Minecraft library. */
    static final class FjordMath {
        static final int CELL=3072;
        static final double RADIUS=1350.0;
        private FjordMath(){}
        static long mix(long h) {
            h=(h^(h>>>30))*0xbf58476d1ce4e5b9L;
            h=(h^(h>>>27))*0x94d049bb133111ebL;
            return h^(h>>>31);
        }
        static long hash(int cx,int cz) {
            return mix((long)cx*0x9e3779b97f4a7c15L
                    ^(long)cz*0xc2b2ae3d27d4eb4fL
                    ^0x7b46f22e21e5af19L);
        }
        static int siteX(int cx,long h) {
            return (int)((long)cx*CELL+CELL/2+Math.floorMod(h>>>13,193L)-96);
        }
        static int siteZ(int cz,long h) {
            return (int)((long)cz*CELL+CELL/2+Math.floorMod(h>>>34,193L)-96);
        }
        static double phase(long h){return Math.floorMod(h>>>40,6284L)/1000.0;}
        static double smooth(double a,double b,double x) {
            double t=Math.max(0.0,Math.min(1.0,(x-a)/(b-a)));
            return t*t*(3.0-2.0*t);
        }
        static double carve(double before,double dx,double dz,
                            double angle,double phase) {
            double r=Math.hypot(dx,dz);
            if(r>=RADIUS)return before;
            double c=Math.cos(angle),s=Math.sin(angle);
            double u=dx*c+dz*s;
            double v=-dx*s+dz*c;
            // Winding glacier-cut valley with broad estuary and U-shape.
            double meander=66.0*Math.sin(u/315.0+phase)
                         +21.0*Math.sin(u/113.0-phase*0.7);
            double halfWidth=95.0+46.0*(1.0-smooth(-500.0,850.0,u));
            double cross=1.0-smooth(halfWidth-25,halfWidth+120,
                                    Math.abs(v-meander));
            double length=smooth(-1310,-1010,u)*(1.0-smooth(830,1250,u));
            // Fade exactly to zero INSIDE the cell for seamless chunks.
            double boundary=1.0-smooth(0.80,1.0,r/RADIUS);
            double weight=cross*length*boundary;
            if(weight<=0)return before;
            // Signal zero is sea level Y=63; one unit is about 128 blocks.
            double floor=-0.24-0.36*smooth(-520,480,u)
                         +0.11*smooth(550,1000,u);
            // Terrain can only erode. Existing ocean never rises into a dam.
            return before+weight*(Math.min(before,floor)-before);
        }
    }
    // === END INDEPENDENT FJORD MATH ===
}
