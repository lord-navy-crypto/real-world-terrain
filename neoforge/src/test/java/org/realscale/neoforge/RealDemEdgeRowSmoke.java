package org.realscale.neoforge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

/**
 * Reconstruct the original four-row measuredHeight() expression using the
 * production row interpolation and compare RAW IEEE-754 bits with Test.18.
 * No Minecraft classes or timing assumptions are involved.
 */
public final class RealDemEdgeRowSmoke {
    private static final Class<?> TYPE = RealDemSeamlessMath.class;
    private static final RealDemDataSource DEM;
    private static final double CELL;
    private static final int GRID;
    private static final Method SAMPLE;
    private static final Method SMOOTH;
    private static final Method LIMIT;
    private static int checked, north, south, inside;

    static {
        try {
            Field data = TYPE.getDeclaredField("DEM");
            data.setAccessible(true);
            DEM = (RealDemDataSource)data.get(null);
            Field cell = TYPE.getDeclaredField("CELL");
            cell.setAccessible(true);
            CELL = cell.getDouble(null);
            Field grid = TYPE.getDeclaredField("GRID");
            grid.setAccessible(true);
            GRID = grid.getInt(null);
            SAMPLE = TYPE.getDeclaredMethod("sampleRow",int.class,int.class,double.class);
            SMOOTH = TYPE.getDeclaredMethod("smoothSegment",
                    double.class,double.class,double.class,double.class,double.class);
            LIMIT = TYPE.getDeclaredMethod("limit",double.class,double.class,double.class);
            SAMPLE.setAccessible(true);
            SMOOTH.setAccessible(true);
            LIMIT.setAccessible(true);
        } catch (ReflectiveOperationException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }
    private static double limit(double v,double lo,double hi) throws Exception {
        return (double)LIMIT.invoke(null,v,lo,hi);
    }
    private static double sample(int row,int x,double a) throws Exception {
        return (double)SAMPLE.invoke(null,row,x,a);
    }
    private static double smooth(double p,double a,double b,double n,double t)
            throws Exception {
        return (double)SMOOTH.invoke(null,p,a,b,n,t);
    }
    private static double frozenFourRowHeight(double x,double z) throws Exception {
        double cx=limit(x,DEM.originX(),DEM.maxX());
        double cz=limit(z,DEM.originZ(),DEM.maxZ());
        double gx=(cx-DEM.originX())/CELL,gz=(cz-DEM.originZ())/CELL;
        int ix=Math.min(GRID-2,(int)Math.floor(gx));
        int iz=Math.min(GRID-2,(int)Math.floor(gz));
        double a=gx-ix,b=gz-iz;
        // Frozen reference from Test.17: always evaluated both unused rows.
        double r0=sample(iz-1,ix,a);
        double r1=sample(iz,ix,a);
        double r2=sample(iz+1,ix,a);
        double r3=sample(iz+2,ix,a);
        double lower=iz>0?r0:2*r1-r2;
        double upper=iz<GRID-2?r3:2*r2-r1;
        return smooth(lower,r1,r2,upper,b);
    }
    private static void check(double x,double z) throws Exception {
        long oldBits=Double.doubleToRawLongBits(frozenFourRowHeight(x,z));
        long nowBits=Double.doubleToRawLongBits(
                RealDemSeamlessMath.measuredHeight(x,z));
        if(oldBits!=nowBits)
            throw new AssertionError("DEM edge interpolation bits differ at "
                    +x+","+z+": "+Long.toHexString(oldBits)+"/"
                    +Long.toHexString(nowBits));
        double clampedZ=limit(z,DEM.originZ(),DEM.maxZ());
        double gz=(clampedZ-DEM.originZ())/CELL;
        int iz=Math.min(GRID-2,(int)Math.floor(gz));
        if(iz==0)north++;
        else if(iz==GRID-2)south++;
        else inside++;
        checked++;
    }

    public static void main(String[] args) throws Exception {
        double minX=DEM.originX(),maxX=DEM.maxX();
        double minZ=DEM.originZ(),maxZ=DEM.maxZ();
        double[] xs={
                minX-8192,minX-1,minX,Math.nextUp(minX),
                minX+CELL/7,minX+CELL,
                (minX+maxX)*0.5,maxX-CELL,maxX-Math.ulp(maxX),
                maxX,maxX+1,maxX+8192
        };
        double[] zs={
                minZ-8192,minZ-1,minZ,Math.nextUp(minZ),
                minZ+CELL/7,minZ+CELL,
                (minZ+maxZ)*0.5,maxZ-CELL,maxZ-Math.ulp(maxZ),
                maxZ,maxZ+1,maxZ+8192
        };
        for(double x:xs)for(double z:zs)check(x,z);
        // Thousands of fractional/interior and boundary-clamped coordinates.
        Random r=new Random(0x18ed9eaL);
        for(int i=0;i<12000;i++) {
            double x=minX-CELL*8 + r.nextDouble()*(maxX-minX+CELL*16);
            double z=minZ-CELL*8 + r.nextDouble()*(maxZ-minZ+CELL*16);
            check(x,z);
            if(i%5==0) {
                double boundary=(i&1)==0 ? minZ : maxZ;
                check(x,boundary+r.nextDouble()*CELL*((i&1)==0?0.25:-0.25));
            }
        }
        if(checked<13000||north<100||south<100||inside<100)
            throw new AssertionError("Insufficient north/south/interior cases");
        System.out.println("REALSCALE_DEM_EDGE_ROW_BITS_PASS checked="+checked
                +" north="+north+" south="+south+" interior="+inside);
    }
}
