package org.realscale.neoforge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

/**
 * Replays the complete frozen Test.22 19-component Nether broad phase with
 * its original Hall/Hall/radius horizontal reach formula. The candidate
 * implementation uses cached immutable TubeSegment AABBs, but must return
 * precisely the same 19 bits for every source X/Z probe.
 */
public final class GrandNetherMaskAabbParitySmoke {
    private GrandNetherMaskAabbParitySmoke() {}
    private static long checked, empty, active, tubeBits;
    private static Field field(Class<?> type,String name)throws Exception {
        Field f=type.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }
    private static Object fieldValue(Object instance,String name)throws Exception {
        return field(instance.getClass(),name).get(instance);
    }
    private static void require(boolean ok,String message) {
        if(!ok)throw new AssertionError(message);
    }
    private static boolean roomCouldTouch(GrandNetherCaveNetwork.Hall h,
            double loX,double hiX,double loZ,double hiZ) {
        return loX<=h.x()+h.radiusX() && hiX>=h.x()-h.radiusX()
            && loZ<=h.z()+h.radiusZ() && hiZ>=h.z()-h.radiusZ();
    }
    // Frozen 19-bit Test.22 candidate-test equation (not cached geometry).
    private static boolean tubeCouldTouch(GrandNetherCaveNetwork.Hall a,
            GrandNetherCaveNetwork.Hall b,double radius,
            double loX,double hiX,double loZ,double hiZ) {
        double reach=radius+4.0;
        return loX<=Math.max(a.x(),b.x())+reach
            && hiX>=Math.min(a.x(),b.x())-reach
            && loZ<=Math.max(a.z(),b.z())+reach
            && hiZ>=Math.min(a.z(),b.z())-reach;
    }
    private static int originalMask(Object cache,
            double loX,double hiX,double loZ,double hiZ) throws Exception {
        var halls=(GrandNetherCaveNetwork.Hall[][])fieldValue(cache,"halls");
        var satellites=(GrandNetherCaveNetwork.Hall[][])fieldValue(cache,"satellites");
        int bits=0;
        for(int layer=0;layer<2;layer++) {
            int shift=layer*9;
            var center=halls[layer][0];
            if(roomCouldTouch(center,loX,hiX,loZ,hiZ))
                bits|=1<<shift;
            for(int dir=0;dir<4;dir++)
                if(tubeCouldTouch(center,halls[layer][dir+1],11,
                        loX,hiX,loZ,hiZ))
                    bits|=1<<(shift+1+dir);
            for(int gallery=0;gallery<2;gallery++) {
                var satellite=satellites[layer][gallery];
                if(roomCouldTouch(satellite,loX,hiX,loZ,hiZ))
                    bits|=1<<(shift+5+gallery*2);
                if(tubeCouldTouch(center,satellite,7,
                        loX,hiX,loZ,hiZ))
                    bits|=1<<(shift+6+gallery*2);
            }
        }
        if(tubeCouldTouch(halls[0][0],halls[1][0],10,
                loX,hiX,loZ,hiZ))
            bits|=1<<18;
        return bits;
    }
    private static void compare(Object cache,Method mask,double x,double z)
            throws Exception {
        final double loX=x-5.0,hiX=x+5.0,loZ=z-4.0,hiZ=z+4.0;
        int frozen=originalMask(cache,loX,hiX,loZ,hiZ);
        int optimized=(int)mask.invoke(null,cache,loX,hiX,loZ,hiZ);
        require(frozen==optimized,
                "19-bit Nether cave column mask differs at "+x+","+z
                    +" frozen=0x"+Integer.toHexString(frozen)
                    +" candidate=0x"+Integer.toHexString(optimized));
        require((optimized&~0x7ffff)==0,"Unexpected bits outside 19 components");
        checked++;
        if(frozen==0)empty++;else active++;
        tubeBits+=Integer.bitCount(frozen & 0x7ffff);
    }
    public static void main(String[] args)throws Exception {
        Class<?> net=GrandNetherCaveNetwork.class;
        var threadLocal=(ThreadLocal<?>)field(net,"CACHED_HALLS").get(null);
        Object regions=threadLocal.get();
        Method select=regions.getClass().getDeclaredMethod(
                "get",int.class,int.class);
        select.setAccessible(true);
        Class<?> cacheClass=Class.forName(net.getName()+"$CellCache");
        Method mask=net.getDeclaredMethod("componentMask",cacheClass,
                double.class,double.class,double.class,double.class);
        mask.setAccessible(true);
        Random rng=new Random(0x23e7ef48L);
        for(int gx=-3;gx<=3;gx++)for(int gz=-3;gz<=3;gz++) {
            Object cache=select.invoke(regions,gx,gz);
            double centerX=(long)gx*GrandNetherCaveNetwork.CELL+128;
            double centerZ=(long)gz*GrandNetherCaveNetwork.CELL+128;
            for(int k=0;k<160;k++) {
                double x=centerX+rng.nextInt(641)-320+(k%3==0?0.5:0.0);
                double z=centerZ+rng.nextInt(641)-320+(k%7==0?0.125:0.0);
                compare(cache,mask,x,z);
            }
            var halls=(GrandNetherCaveNetwork.Hall[][])fieldValue(cache,"halls");
            var galleries=(GrandNetherCaveNetwork.Hall[][])fieldValue(cache,"satellites");
            for(var layer:halls)for(var hall:layer)
                for(double dx:new double[]{-78,-52,-1,0,1,52,78})
                    for(double dz:new double[]{-78,0,78})
                        compare(cache,mask,hall.x()+dx,hall.z()+dz);
            for(var layer:galleries)for(var hall:layer)
                for(double dx:new double[]{-27,0,27})
                    for(double dz:new double[]{-27,0,27})
                        compare(cache,mask,hall.x()+dx,hall.z()+dz);
        }
        require(checked>15000 && active>500 && empty>500 && tubeBits>10000,
                "Insufficient Nether mask proof: cases="+checked+" nonempty="
                +active+" empty="+empty+" componentHits="+tubeBits);
        System.out.println("NETHER_CACHED_19BIT_MASK_PARITY_PASS cases="+checked
                +" active="+active+" empty="+empty+" componentHits="+tubeBits);
    }
}
