package org.realscale.neoforge;

/**
 * USGS-conditioned analytical continuation, for new worlds only.
 *
 * The source data are TRUE elevations within their coverage. Beyond coverage
 * the model is explicitly SYNTHETIC: a bounded gradient continuation joined
 * to an infinite, smooth, deterministic multi-scale landscape whose mean,
 * directional trend and roughness come from the actual loaded DEM grid.
 * This NEVER repeats the raster as tiles or uses Minecraft chunk coordinates.
 */
final class RealDemFittedMathV1 {
    private static final RealDemDataSource DEM=RealDemDataSource.loadMeasured();
    private static final int NX=DEM.width(), NZ=DEM.height();
    private static final double STEP=DEM.cellMeters();
    private static final double MID_X=0.5*(DEM.originX()+DEM.maxX());
    private static final double MID_Z=0.5*(DEM.originZ()+DEM.maxZ());
    private static final double MIN= -48.0, MAX=2000.0;
    private static final double MEAN, SLOPE_X, SLOPE_Z, ROUGH;
    static {
        double sum=0.0, vx=0.0, vz=0.0, varx=0.0, varz=0.0;
        for(int j=0;j<NZ;j++)for(int i=0;i<NX;i++){
            double x=DEM.originX()+i*STEP-MID_X,z=DEM.originZ()+j*STEP-MID_Z;
            double h=DEM.worldSolidYAt(x+MID_X,z+MID_Z);
            sum+=h;vx+=x*h;vz+=z*h;varx+=x*x;varz+=z*z;
        }
        MEAN=sum/(NX*(double)NZ);
        SLOPE_X=Math.max(-0.5,Math.min(0.5,vx/varx));
        SLOPE_Z=Math.max(-0.5,Math.min(0.5,vz/varz));
        double sq=0.0;
        for(int j=0;j<NZ;j++)for(int i=0;i<NX;i++){
            double x=DEM.originX()+i*STEP-MID_X,z=DEM.originZ()+j*STEP-MID_Z;
            double h=DEM.worldSolidYAt(x+MID_X,z+MID_Z);
            double e=h-MEAN-SLOPE_X*x-SLOPE_Z*z;
            sq+=e*e;
        }
        ROUGH=Math.max(18.0,Math.min(200.0,Math.sqrt(sq/(NX*(double)NZ))));
    }
    private RealDemFittedMathV1(){}
    private static double clamp(double v,double lo,double hi){
        return Math.max(lo,Math.min(hi,v));
    }
    static double smoothstep(double a,double b,double v){
        double t=clamp((v-a)/(b-a),0,1);
        return t*t*(3-2*t);
    }
    private static long mix(long a){
        a^=a>>>30; a*=0xbf58476d1ce4e5b9L;
        a^=a>>>27; a*=0x94d049bb133111ebL;
        return a^(a>>>31);
    }
    private static double lattice(long x,long z,long salt){
        long n=mix(x*0x9e3779b97f4a7c15L^z*0xc2b2ae3d27d4eb4fL^salt);
        return (double)(n>>>11)*0x1.0p-52-1.0;
    }
    private static double fade(double v){return v*v*v*(v*(v*6-15)+10);}
    private static double lerp(double a,double b,double t){return a+(b-a)*t;}
    private static double noise(double x,double z,double wavelength,long salt){
        double u=x/wavelength,v=z/wavelength;
        long xi=(long)Math.floor(u),zi=(long)Math.floor(v);
        double a=fade(u-xi),b=fade(v-zi);
        return lerp(lerp(lattice(xi,zi,salt),lattice(xi+1,zi,salt),a),
                    lerp(lattice(xi,zi+1,salt),lattice(xi+1,zi+1,salt),a),b);
    }
    private static double fractal(double x,double z){
        // Statistically calibrated from DEM, no discontinuous cell selection.
        return 1.15*noise(x,z,8192.0,0x4f17c2a1L)
             + 0.65*noise(x,z,2600.0,0x32ac8417L)
             + 0.25*noise(x,z,720.0,0x6257be13L);
    }
    private static double broad(double x,double z){
        double dx=x-MID_X,dz=z-MID_Z;
        double projection=SLOPE_X*dx+SLOPE_Z*dz;
        return MEAN+900.0*Math.tanh(projection/900.0)
                +ROUGH*2.4*fractal(x,z);
    }
    static double distanceOutside(double x,double z){
        double bx=clamp(x,DEM.originX(),DEM.maxX());
        double bz=clamp(z,DEM.originZ(),DEM.maxZ());
        return Math.hypot(x-bx,z-bz);
    }
    static double predictedHeight(double x,double z){
        double bx=clamp(x,DEM.originX(),DEM.maxX());
        double bz=clamp(z,DEM.originZ(),DEM.maxZ());
        double dx=x-bx,dz=z-bz;
        if(dx==0&&dz==0)return DEM.worldSolidYAt(x,z);
        double center=RealDemSeamlessMath.measuredHeight(bx,bz);
        // Inward derivatives, not a global polynomial outside its fit domain.
        double sx=dx==0?0:
            (center-RealDemSeamlessMath.measuredHeight(
                clamp(bx-Math.copySign(STEP,dx),DEM.originX(),DEM.maxX()),bz))/STEP
                *Math.signum(dx);
        double sz=dz==0?0:
            (center-RealDemSeamlessMath.measuredHeight(
                bx,clamp(bz-Math.copySign(STEP,dz),DEM.originZ(),DEM.maxZ())))/STEP
                *Math.signum(dz);
        // Derivative contribution is bounded: no kilometer-high sloping plane.
        double tilt=clamp(sx,-0.70,0.70)*1600.0*Math.tanh(Math.abs(dx)/1600.0)
                   +clamp(sz,-0.70,0.70)*1600.0*Math.tanh(Math.abs(dz)/1600.0);
        double near=center+tilt
                +ROUGH*1.5*(fractal(x,z)-fractal(bx,bz));
        // For tiny DEMs, an infinite edge-projected stripe would be artificial.
        // Gradually prefer DEM-fitted global statistics, never a tiled raster.
        double d=Math.hypot(dx,dz);
        double farWeight=smoothstep(1000.0,12000.0,d);
        return clamp(lerp(near,broad(x,z),farWeight),MIN,MAX);
    }
    static double measuredMinX(){return DEM.originX();}
    static double measuredMaxX(){return DEM.maxX();}
    static double measuredMinZ(){return DEM.originZ();}
    static double measuredMaxZ(){return DEM.maxZ();}
    static double mean(){return MEAN;}
    static double roughness(){return ROUGH;}
}
