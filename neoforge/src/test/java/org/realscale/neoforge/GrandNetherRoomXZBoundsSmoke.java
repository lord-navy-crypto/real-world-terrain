package org.realscale.neoforge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

/**
 * Test.35: frozen Hall arithmetic versus six primitive immutable X/Z
 * bounds per seeded Nether cave region. No cave carving changes allowed.
 */
public final class GrandNetherRoomXZBoundsSmoke {
    private static long tested, overlaps, misses, tangencies, rebuilt;
    private static final Random RNG=new Random(0x35261024L);
    private static void require(boolean v,String message){
        if(!v)throw new AssertionError(message);
    }
    private static Field field(Class<?> klass,String name)throws Exception {
        Field f=klass.getDeclaredField(name);f.setAccessible(true);return f;
    }
    private static Object read(Object obj,String name)throws Exception {
        return field(obj.getClass(),name).get(obj);
    }
    // Frozen Test.34 roomCouldTouch(Hall,...), including inclusive edges
    // and the exact Java double expression order.
    private static boolean frozen(GrandNetherCaveNetwork.Hall h,
            double loX,double hiX,double loZ,double hiZ) {
        return loX<=h.x()+h.radiusX() && hiX>=h.x()-h.radiusX()
            && loZ<=h.z()+h.radiusZ() && hiZ>=h.z()-h.radiusZ();
    }
    private static long raw(double v){return Double.doubleToRawLongBits(v);}
    private static void check(GrandNetherCaveNetwork.Hall h,double[] a,
            Method method,double loX,double hiX,double loZ,double hiZ)
            throws Exception {
        boolean old=frozen(h,loX,hiX,loZ,hiZ);
        boolean actual=(boolean)method.invoke(null,a,loX,hiX,loZ,hiZ);
        require(old==actual,"Cached bounds changed frozen Nether room candidate at "
                +loX+","+hiX+","+loZ+","+hiZ);
        if(old)overlaps++;else misses++;
        tested++;
    }
    private static void room(GrandNetherCaveNetwork.Hall h,double[] a,
            Method method)throws Exception {
        require(a.length==4,"Room AABB must have precisely 4 doubles");
        require(raw(a[0])==raw(h.x()-h.radiusX())
                    &&raw(a[1])==raw(h.x()+h.radiusX())
                    &&raw(a[2])==raw(h.z()-h.radiusZ())
                    &&raw(a[3])==raw(h.z()+h.radiusZ()),
                "Cached AABB doubles differ from original Hall expression");
        // Tangencies at every inclusive corner are all TRUE.
        for(double x:new double[]{a[0],a[1]})
            for(double z:new double[]{a[2],a[3]}) {
                check(h,a,method,x,x,z,z);
                tangencies++;
            }
        // Include actual source-column +/-5 X and +/-4 Z warp extents,
        // broad query boxes, outside positions and fractional boundaries.
        for(int i=0;i<100;i++) {
            double x=h.x()+(RNG.nextDouble()*7.0-3.5)*h.radiusX();
            double z=h.z()+(RNG.nextDouble()*7.0-3.5)*h.radiusZ();
            double bx=i%3==0?5.0:i%3==1?0.0:20.0;
            double bz=i%3==0?4.0:i%3==1?0.0:17.0;
            check(h,a,method,x-bx,x+bx,z-bz,z+bz);
        }
    }
    public static void main(String[] args)throws Exception {
        var net=GrandNetherCaveNetwork.class;
        ThreadLocal<?> local=(ThreadLocal<?>)field(net,"CACHED_HALLS").get(null);
        local.remove();
        Object snapshots=local.get();
        Method select=snapshots.getClass().getDeclaredMethod("get",int.class,int.class);
        select.setAccessible(true);
        Method touch=net.getDeclaredMethod("roomCouldTouch",double[].class,
                double.class,double.class,double.class,double.class);
        touch.setAccessible(true);
        for(int gx=-6;gx<=6;gx++)for(int gz=-6;gz<=6;gz++) {
            Object cell=select.invoke(snapshots,gx,gz);
            var halls=(GrandNetherCaveNetwork.Hall[][])read(cell,"halls");
            var sides=(GrandNetherCaveNetwork.Hall[][])read(cell,"satellites");
            var bounds=(double[][][])read(cell,"roomXZ");
            require(bounds.length==2 && bounds[0].length==3,
                    "Expected 6 bounded primitive per-cell room AABBs");
            for(int layer=0;layer<2;layer++) {
                room(halls[layer][0],bounds[layer][0],touch);
                for(int side=0;side<2;side++)
                    room(sides[layer][side],bounds[layer][side+1],touch);
            }
        }
        // Force real same-slot graph reuse then prove no stale room bounds.
        Object reused=select.invoke(snapshots,0,0);
        var hall=(GrandNetherCaveNetwork.Hall[][])read(reused,"halls");
        var boxes=(double[][][])read(reused,"roomXZ");
        room(hall[0][0],boxes[0][0],touch);
        rebuilt++;
        require(tested>100000 && tangencies>4000
                        && overlaps>5000 && misses>10000 && rebuilt>0,
                "Nether room bounds test missing representative coverage: "
                +tested+" overlap="+overlaps+" misses="+misses);
        System.out.println("NETHER_ROOM_XZ_BOUNDS_EXACT_PASS regions=169"
                +" candidateComparisons="+tested+" positive="+overlaps
                +" negative="+misses+" cornerTangencies="+tangencies
                +" verifiedRegionReuse="+rebuilt);
    }
}
