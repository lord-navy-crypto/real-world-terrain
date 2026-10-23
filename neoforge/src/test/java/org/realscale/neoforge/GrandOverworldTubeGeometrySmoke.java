package org.realscale.neoforge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

/**
 * Test.21: strict raw-bit equality of the frozen per-voxel tunnelStrength()
 * and the worker-local precomputed geometry that production worldgen uses.
 * Includes corridors, galleries, vertical shafts and distant arteries.
 */
public final class GrandOverworldTubeGeometrySmoke {
    private static long checked, carved, shaft, artery;
    private static final Random RANDOM=new Random(0x210a7be1L);

    private static Field field(Class<?> cls,String name) throws Exception {
        Field f=cls.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }
    private static Object value(Object obj,String name) throws Exception {
        return field(obj.getClass(),name).get(obj);
    }
    private static void require(boolean ok,String msg) {
        if(!ok)throw new AssertionError(msg);
    }
    private static void compare(Object geom,Method cached) throws Exception {
        if(geom==null)return;
        GrandOverworldCaveNetwork.Hall a=
                (GrandOverworldCaveNetwork.Hall)value(geom,"a");
        GrandOverworldCaveNetwork.Hall b=
                (GrandOverworldCaveNetwork.Hall)value(geom,"b");
        GrandOverworldCaveNetwork.Passage section=
                (GrandOverworldCaveNetwork.Passage)value(geom,"section");
        // Key affects bend displacement only and is fixed for each passage.
        // The original seeded-key formula is independent of the test
        // because the same key is stored in its immutable descriptor.
        long key=field(geom.getClass(),"key").getLong(geom);
        for(int n=0;n<112;n++) {
            double t=(n%28)/27.0;
            double reach=section.radius()*(n%3==0?2.5:1.0);
            double x=a.x()+(b.x()-a.x())*t
                    +(RANDOM.nextDouble()-0.5)*2*reach;
            double y=a.y()+(b.y()-a.y())*t
                    +(RANDOM.nextDouble()-0.5)*2*reach*section.verticalScale();
            double z=a.z()+(b.z()-a.z())*t
                    +(RANDOM.nextDouble()-0.5)*2*reach;
            double old=GrandOverworldCaveNetwork.tunnelStrength(
                    x,y,z,a,b,section,key);
            double now=(double)cached.invoke(null,x,y,z,geom);
            require(Double.doubleToRawLongBits(old)==Double.doubleToRawLongBits(now),
                    "Overworld cave tunnel density bits diverged at "+x+","+y+","+z
                    +" reference="+old+" candidate="+now);
            checked++;
            if(old>0)carved++;
        }
    }
    public static void main(String[] args) throws Exception {
        Field local=field(GrandOverworldCaveNetwork.class,"LOCAL");
        ThreadLocal<?> worker=(ThreadLocal<?>)local.get(null);
        Object region=worker.get();
        Method load=region.getClass().getDeclaredMethod("get",int.class,int.class);
        load.setAccessible(true);
        Class<?> geometry=Class.forName(
                "org.realscale.neoforge.GrandOverworldCaveNetwork$TunnelGeometry");
        Method cached=GrandOverworldCaveNetwork.class.getDeclaredMethod(
                "cachedTunnelStrength",double.class,double.class,double.class,geometry);
        cached.setAccessible(true);
        for(int gx=-4;gx<=4;gx+=2)for(int gz=-4;gz<=4;gz+=2) {
            Object site=load.invoke(region,gx,gz);
            Object[][] corridors=(Object[][])value(site,"corridorTubes");
            Object[][] sides=(Object[][])value(site,"sideTubes");
            Object[] shafts=(Object[])value(site,"shaftTubes");
            Object[][][] arteries=(Object[][][])value(site,"arteryTubes");
            for(Object[] layer:corridors)for(Object tube:layer)compare(tube,cached);
            for(Object[] layer:sides)for(Object tube:layer)compare(tube,cached);
            for(Object tube:shafts){compare(tube,cached);shaft++;}
            for(Object[][] tier:arteries)
                for(Object[] axis:tier)
                    for(Object tube:axis)
                        if(tube!=null){compare(tube,cached);artery++;}
        }
        require(checked>75_000 && carved>300 && shaft>0 && artery>0,
                "Insufficient realistic Overworld tunnel and artery coverage");
        System.out.println("OVERWORLD_PRECOMPUTED_TUNNEL_RAW_BITS_PASS: "
                +checked+" sampled densities, "+carved+" inside shafts/corridors"
                +", "+shaft+" shaft paths, "+artery+" arterial paths");
    }
}
