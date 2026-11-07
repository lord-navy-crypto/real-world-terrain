package org.realscale.neoforge;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Raw-bit regression for the optimized bicubic DEM row sampler.
 * The reference deliberately retains the original four-corner clamp path
 * and four-row evaluation order.
 */
public final class RealDemEdgeRowParityTest {
    private static final Class<?> TYPE=RealDemSeamlessMath.class;
    private static final RealDemDataSource DEM;
    private static final double CELL;
    private static final int GRID;
    private static final Method CORNER,SAMPLE,SMOOTH,LIMIT;

    static {
        try {
            Field data=TYPE.getDeclaredField("DEM");
            data.setAccessible(true);
            DEM=(RealDemDataSource)data.get(null);
            Field cell=TYPE.getDeclaredField("CELL");
            cell.setAccessible(true);
            CELL=cell.getDouble(null);
            Field grid=TYPE.getDeclaredField("GRID");
            grid.setAccessible(true);
            GRID=grid.getInt(null);
            CORNER=TYPE.getDeclaredMethod("corner",int.class,int.class);
            SAMPLE=TYPE.getDeclaredMethod("sampleRow",int.class,int.class,double.class);
            SMOOTH=TYPE.getDeclaredMethod("smoothSegment",
                    double.class,double.class,double.class,double.class,double.class);
            LIMIT=TYPE.getDeclaredMethod("limit",double.class,double.class,double.class);
            for(Method method:new Method[]{CORNER,SAMPLE,SMOOTH,LIMIT})
                method.setAccessible(true);
        } catch(ReflectiveOperationException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private static double limit(double value,double low,double high) throws Exception {
        return (double)LIMIT.invoke(null,value,low,high);
    }

    private static double corner(int row,int col) throws Exception {
        return (double)CORNER.invoke(null,row,col);
    }

    private static double smooth(double previous,double a,double b,
            double next,double t) throws Exception {
        return (double)SMOOTH.invoke(null,previous,a,b,next,t);
    }

    private static double optimizedRow(int row,int ix,double a) throws Exception {
        return (double)SAMPLE.invoke(null,row,ix,a);
    }

    /** Frozen original row sampler: four independently clamped corner reads. */
    private static double originalRow(int row,int ix,double a) throws Exception {
        double p0=corner(row,ix),p1=corner(row,ix+1);
        double pm=ix>0?corner(row,ix-1):2*p0-p1;
        double pp=ix<GRID-2?corner(row,ix+2):2*p1-p0;
        return smooth(pm,p0,p1,pp,a);
    }

    /** Frozen original measuredHeight evaluation, including discarded edge rows. */
    private static double originalMeasuredHeight(double x,double z) throws Exception {
        double cx=limit(x,DEM.originX(),DEM.maxX());
        double cz=limit(z,DEM.originZ(),DEM.maxZ());
        double gx=(cx-DEM.originX())/CELL,gz=(cz-DEM.originZ())/CELL;
        int ix=Math.min(GRID-2,(int)Math.floor(gx));
        int iz=Math.min(GRID-2,(int)Math.floor(gz));
        double a=gx-ix,b=gz-iz;
        double r0=originalRow(iz-1,ix,a);
        double r1=originalRow(iz,ix,a);
        double r2=originalRow(iz+1,ix,a);
        double r3=originalRow(iz+2,ix,a);
        double lower=iz>0?r0:2*r1-r2;
        double upper=iz<GRID-2?r3:2*r2-r1;
        return smooth(lower,r1,r2,upper,b);
    }

    @Test
    void rowSamplerMatchesOriginalForEveryClampedRowAndFraction() throws Exception {
        double[] fractions={0.0,Math.nextUp(0.0),1.0/7.0,0.25,0.5,0.75,
                Math.nextDown(1.0)};
        for(int row=-2;row<=GRID+1;row++) {
            for(int ix=0;ix<GRID-1;ix++) {
                for(double fraction:fractions) {
                    long expected=Double.doubleToRawLongBits(
                            originalRow(row,ix,fraction));
                    long actual=Double.doubleToRawLongBits(
                            optimizedRow(row,ix,fraction));
                    assertEquals(expected,actual,"row="+row+" ix="+ix+" a="+fraction);
                }
            }
        }
    }

    @Test
    void measuredHeightMatchesOriginalAcrossRasterEdgesAndInterior() throws Exception {
        double minX=DEM.originX(),maxX=DEM.maxX();
        double minZ=DEM.originZ(),maxZ=DEM.maxZ();
        double[] xs={minX-8192,minX-1,minX,Math.nextUp(minX),
                minX+CELL/7,minX+CELL,(minX+maxX)*0.5,
                maxX-CELL,maxX-Math.ulp(maxX),maxX,maxX+1,maxX+8192};
        double[] zs={minZ-8192,minZ-1,minZ,Math.nextUp(minZ),
                minZ+CELL/7,minZ+CELL,(minZ+maxZ)*0.5,
                maxZ-CELL,maxZ-Math.ulp(maxZ),maxZ,maxZ+1,maxZ+8192};
        for(double x:xs)for(double z:zs)assertHeightBits(x,z);

        Random random=new Random(0x18ed9eaL);
        for(int i=0;i<12_000;i++) {
            double x=minX-CELL*8+random.nextDouble()*(maxX-minX+CELL*16);
            double z=minZ-CELL*8+random.nextDouble()*(maxZ-minZ+CELL*16);
            assertHeightBits(x,z);
            if(i%5==0) {
                double boundary=(i&1)==0?minZ:maxZ;
                double offset=random.nextDouble()*CELL*0.25*((i&1)==0?1:-1);
                assertHeightBits(x,boundary+offset);
            }
        }
    }

    private static void assertHeightBits(double x,double z) throws Exception {
        long expected=Double.doubleToRawLongBits(originalMeasuredHeight(x,z));
        long actual=Double.doubleToRawLongBits(RealDemSeamlessMath.measuredHeight(x,z));
        assertEquals(expected,actual,"x="+x+" z="+z);
    }
}
