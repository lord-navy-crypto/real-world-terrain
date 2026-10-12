package org.realscale.neoforge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Opt-in repaired USGS Big Sur DEM profile (new worlds only).
 *
 * The measured 480 x 480 metre square remains 100% DEM-driven.
 * Beyond its edge, extrapolate the nearest source slope a bounded 128 m
 * then blend smoothly to the unmodified procedural fallback OUTSIDE the
 * source square. No coordinate is relative to chunk origin.
 *
 * Monotone bicubic interpolation keeps 32 m sample values, reduces pointy
 * grid-cell cusps, and cannot overshoot the surrounding four source samples.
 */
public record RealDemSeamlessDensity(DensityFunction base, int blendMeters)
        implements DensityFunction.SimpleFunction {
    private static final RealDemTile DEM = RealDemTile.load();
    private static final double CELL = 32.0;
    private static final int GRID = 16;
    private static final double[][] HEIGHTS = loadHeights();

    public static final MapCodec<RealDemSeamlessDensity> MAP_CODEC =
        RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("base").forGetter(RealDemSeamlessDensity::base),
            Codec.intRange(256,1536).fieldOf("blend_meters").forGetter(RealDemSeamlessDensity::blendMeters)
        ).apply(i,RealDemSeamlessDensity::new));
    private static final KeyDispatchDataCodec<RealDemSeamlessDensity> DISPATCH =
        KeyDispatchDataCodec.of(MAP_CODEC);

    private static double[][] loadHeights() {
        double[][] h = new double[GRID][GRID];
        for (int z=0; z<GRID; z++)
            for (int x=0; x<GRID; x++)
                h[z][x] = DEM.worldSolidYAt(
                    DEM.originX()+CELL*x, DEM.originZ()+CELL*z);
        return h;
    }

    private static double limit(double value,double low,double high) {
        return Math.max(low,Math.min(high,value));
    }

    private static double monotoneSlope(double left,double right) {
        if (left*right<=0.0) return 0.0;
        return 2.0*left*right/(left+right);
    }

    private static double smoothSegment(double prev,double a,double b,
                                         double next,double t) {
        double d=b-a;
        if (d==0.0) return a;
        double before=a-prev;
        double after=next-b;
        double m0=monotoneSlope(before,d);
        double m1=monotoneSlope(d,after);
        double t2=t*t,t3=t2*t;
        double result=(2*t3-3*t2+1)*a+(t3-2*t2+t)*m0
                     +(-2*t3+3*t2)*b+(t3-t2)*m1;
        return limit(result,Math.min(a,b),Math.max(a,b));
    }

    private static double corner(int row,int col) {
        return HEIGHTS[(int)limit(row,0,GRID-1)][(int)limit(col,0,GRID-1)];
    }

    static double measuredHeight(double x,double z) {
        double cx=limit(x,DEM.originX(),DEM.maxX());
        double cz=limit(z,DEM.originZ(),DEM.maxZ());
        double gx=(cx-DEM.originX())/CELL, gz=(cz-DEM.originZ())/CELL;
        int ix=Math.min(GRID-2,(int)Math.floor(gx));
        int iz=Math.min(GRID-2,(int)Math.floor(gz));
        double a=gx-ix,b=gz-iz;
        double[] rows=new double[4];
        for(int r=-1;r<=2;r++){
            double p0=corner(iz+r,ix),p1=corner(iz+r,ix+1);
            double pm=ix>0?corner(iz+r,ix-1):2*p0-p1;
            double pp=ix<GRID-2?corner(iz+r,ix+2):2*p1-p0;
            rows[r+1]=smoothSegment(pm,p0,p1,pp,a);
        }
        double lower=iz>0?rows[0]:2*rows[1]-rows[2];
        double upper=iz<GRID-2?rows[3]:2*rows[2]-rows[1];
        return smoothSegment(lower,rows[1],rows[2],upper,b);
    }

    static double weightOutside(double x,double z,int fadeMeters) {
        double dx=x-limit(x,DEM.originX(),DEM.maxX());
        double dz=z-limit(z,DEM.originZ(),DEM.maxZ());
        double t=limit(Math.hypot(dx,dz)/fadeMeters,0.0,1.0);
        return 1.0-t*t*(3.0-2.0*t);
    }

    static double extendedHeight(double x,double z) {
        double cx=limit(x,DEM.originX(),DEM.maxX());
        double cz=limit(z,DEM.originZ(),DEM.maxZ());
        double center=measuredHeight(cx,cz);
        double dx=x-cx,dz=z-cz;
        if(dx!=0.0) {
            double neighbor=measuredHeight(cx+(dx<0?CELL:-CELL),cz);
            double slope=(neighbor-center)/(dx<0?CELL:-CELL);
            center+=limit(slope,-1.25,1.25)*limit(dx,-128,128);
        }
        if(dz!=0.0) {
            double neighbor=measuredHeight(cx,cz+(dz<0?CELL:-CELL));
            double slope=(neighbor-center)/(dz<0?CELL:-CELL);
            center+=limit(slope,-1.25,1.25)*limit(dz,-128,128);
        }
        return limit(center,-64.0,2031.0);
    }

    @Override public double compute(DensityFunction.FunctionContext ctx) {
        double x=ctx.blockX(),z=ctx.blockZ();
        double w=weightOutside(x,z,blendMeters);
        if (w<=0.0) return base.compute(ctx);
        double solid=(extendedHeight(x,z)-ctx.blockY())/128.0;
        if (w>=1.0) return solid;
        return solid*w + base.compute(ctx)*(1.0-w);
    }

    @Override public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply(new RealDemSeamlessDensity(base.mapAll(visitor),blendMeters));
    }
    @Override public double minValue(){return Math.min(-100.0,base.minValue());}
    @Override public double maxValue(){return Math.max(100.0,base.maxValue());}
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec(){return DISPATCH;}
}
