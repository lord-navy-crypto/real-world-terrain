package org.realscale.neoforge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

/**
 * Independent parity proof for the immutable, per-cell Hall X/Z extents
 * used by the Overworld 47-bit component mask. Test.22's entire original
 * hall-and-passage mask replay remains active alongside this test.
 */
public final class GrandOverworldRoomBoundsParitySmoke {
    private GrandOverworldRoomBoundsParitySmoke() {}
    private static long comparisons, hits, misses, tangentCases;
    private static final Random RANDOM=new Random(0x326c41a0L);

    private static Field field(Class<?> type,String name)throws Exception {
        Field f=type.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }
    private static Object get(Object owner,String name)throws Exception {
        return field(owner.getClass(),name).get(owner);
    }
    private static double val(Object owner,String name)throws Exception {
        return field(owner.getClass(),name).getDouble(owner);
    }
    private static void require(boolean ok,String message) {
        if(!ok)throw new AssertionError(message);
    }
    private static boolean frozen(GrandOverworldCaveNetwork.Hall h,
            double loX,double hiX,double loZ,double hiZ) {
        return loX<=h.x()+h.radiusX() && hiX>=h.x()-h.radiusX()
                && loZ<=h.z()+h.radiusZ() && hiZ>=h.z()-h.radiusZ();
    }
    private static void compare(GrandOverworldCaveNetwork.Hall h,
            Object bounds,Method candidate,
            double loX,double hiX,double loZ,double hiZ)throws Exception {
        boolean old=frozen(h,loX,hiX,loZ,hiZ);
        boolean now=(boolean)candidate.invoke(null,bounds,loX,hiX,loZ,hiZ);
        require(old==now,
                "Overworld hall candidate horizontal reach changed at "
                +loX+","+hiX+","+loZ+","+hiZ);
        if(old)hits++;else misses++;
        comparisons++;
    }
    private static void checkRoom(GrandOverworldCaveNetwork.Hall h,
            Object bounds,Method candidate)throws Exception {
        require(Double.doubleToRawLongBits(val(bounds,"minX"))==
                        Double.doubleToRawLongBits(h.x()-h.radiusX()),
                "Room minX changed IEEE-754 bits");
        require(Double.doubleToRawLongBits(val(bounds,"maxX"))==
                        Double.doubleToRawLongBits(h.x()+h.radiusX()),
                "Room maxX changed IEEE-754 bits");
        require(Double.doubleToRawLongBits(val(bounds,"minZ"))==
                        Double.doubleToRawLongBits(h.z()-h.radiusZ()),
                "Room minZ changed IEEE-754 bits");
        require(Double.doubleToRawLongBits(val(bounds,"maxZ"))==
                        Double.doubleToRawLongBits(h.z()+h.radiusZ()),
                "Room maxZ changed IEEE-754 bits");

        double minX=h.x()-h.radiusX(),maxX=h.x()+h.radiusX();
        double minZ=h.z()-h.radiusZ(),maxZ=h.z()+h.radiusZ();
        // Inclusive exact tangencies must remain true, including min/max.
        compare(h,bounds,candidate,minX,minX,minZ,minZ);
        compare(h,bounds,candidate,maxX,maxX,maxZ,maxZ);
        compare(h,bounds,candidate,minX,minX,maxZ,maxZ);
        compare(h,bounds,candidate,maxX,maxX,minZ,minZ);
        tangentCases+=4;
        for(int i=0;i<48;i++) {
            double x=h.x()+(RANDOM.nextDouble()*6.0-3.0)*h.radiusX();
            double z=h.z()+(RANDOM.nextDouble()*6.0-3.0)*h.radiusZ();
            double rx=i%3==0?0:i%3==1?6:20.0;
            double rz=i%4==0?0:i%4==1?5:i%4==2?11:30.0;
            compare(h,bounds,candidate,x-rx,x+rx,z-rz,z+rz);
        }
    }
    public static void main(String[] args)throws Exception {
        var network=GrandOverworldCaveNetwork.class;
        var thread=(ThreadLocal<?>)field(network,"LOCAL").get(null);
        thread.remove();
        Object store=thread.get();
        Method select=store.getClass().getDeclaredMethod("get",int.class,int.class);
        select.setAccessible(true);
        Class<?> boundsClass=Class.forName(
                "org.realscale.neoforge.GrandOverworldCaveNetwork$RoomXZBounds");
        Method probe=network.getDeclaredMethod("roomCouldTouch",
                boundsClass,double.class,double.class,double.class,double.class);
        probe.setAccessible(true);

        for(int gx=-5;gx<=5;gx++)for(int gz=-5;gz<=5;gz++) {
            Object cell=select.invoke(store,gx,gz);
            var halls=(GrandOverworldCaveNetwork.Hall[][])get(cell,"halls");
            var galleries=(GrandOverworldCaveNetwork.Hall[][])get(cell,"galleries");
            var centers=(Object[])get(cell,"centerXZ");
            var sideBounds=(Object[][])get(cell,"galleryXZ");
            require(centers.length==4 && sideBounds.length==4,
                    "Expected exactly four cavern strata");
            for(int layer=0;layer<4;layer++) {
                checkRoom(halls[layer][0],centers[layer],probe);
                for(int gallery=0;gallery<2;gallery++)
                    checkRoom(galleries[layer][gallery],
                            sideBounds[layer][gallery],probe);
            }
        }
        // Check a reused region after deliberate forced evictions.
        Object rebuilt=select.invoke(store,0,0);
        var centers=(Object[])get(rebuilt,"centerXZ");
        var halls=(GrandOverworldCaveNetwork.Hall[][])get(rebuilt,"halls");
        checkRoom(halls[0][0],centers[0],probe);

        require(comparisons>70_000 && hits>5_000 && misses>5_000
                        && tangentCases>=5_808,
                "Insufficient cached-room bounds comparisons: "+comparisons
                +" hit="+hits+" miss="+misses+" tangencies="+tangentCases);
        System.out.println("OVERWORLD_CACHED_12_ROOM_XZ_BOUNDS_PARITY_PASS "
                +"boxes="+comparisons+" true="+hits+" false="+misses
                +" tangencies="+tangentCases);
    }
}
