package org.realscale.neoforge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Verify cached per-passage constants independently against original Test.24
 * arithmetic, including the exact signed-seed floorMod and axis tie order.
 * Full voxel density raw-bit parity is also checked by the original frozen
 * GrandNetherTubeGeometrySmoke and GrandNetherCaveNetworkParitySmoke.
 */
public final class GrandNetherTubeBendConstantsSmoke {
    private static int segments;
    private static final int[] axes=new int[3];

    private static Field field(Class<?> c,String name)throws Exception {
        Field f=c.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }
    private static Object read(Object instance,String name)throws Exception {
        return field(instance.getClass(),name).get(instance);
    }
    private static void verify(Object o)throws Exception {
        if(o==null)throw new AssertionError("A cave segment was not initialized");
        Class<?> cl=o.getClass();
        long key=field(cl,"key").getLong(o);
        int seed1=field(cl,"bendSeed1").getInt(o);
        int seed2=field(cl,"bendSeed2").getInt(o);
        int axis=field(cl,"bendAxis").getInt(o);
        int old1=((int)Math.floorMod(key,9L)-4);
        int old2=((int)Math.floorMod(key >>> 24,7L)-3);
        if(seed1!=old1||seed2!=old2)
            throw new AssertionError("Signed old bend modulo changed: "+key);
        double vx=field(cl,"vx").getDouble(o);
        double vy=field(cl,"vy").getDouble(o);
        double vz=field(cl,"vz").getDouble(o);
        int oldAxis=Math.abs(vx)>=Math.abs(vz) && Math.abs(vx)>=Math.abs(vy)
                ? 0 : Math.abs(vz)>=Math.abs(vy) ? 1 : 2;
        if(axis!=oldAxis)
            throw new AssertionError("Canonical direction ties changed");
        double radius=field(cl,"radius").getDouble(o);
        double r2=field(cl,"radiusSquared").getDouble(o);
        if(Double.doubleToRawLongBits(r2)!=
                Double.doubleToRawLongBits(radius*radius))
            throw new AssertionError("Tube quadratic radius changed");
        axes[axis]++;
        segments++;
    }
    public static void main(String[] args)throws Exception {
        Field cacheField=field(GrandNetherCaveNetwork.class,"CACHED_HALLS");
        ThreadLocal<?> local=(ThreadLocal<?>)cacheField.get(null);
        Object pool=local.get();
        Method select=pool.getClass().getDeclaredMethod("get",int.class,int.class);
        select.setAccessible(true);
        for(int gx=-15;gx<=15;gx++)for(int gz=-15;gz<=15;gz++) {
            Object c=select.invoke(pool,gx,gz);
            Object[][] links=(Object[][])read(c,"corridors");
            Object[][] side=(Object[][])read(c,"branches");
            for(Object[] layer:links)for(Object segment:layer)verify(segment);
            for(Object[] layer:side)for(Object segment:layer)verify(segment);
            verify(read(c,"interlayer"));
        }
        if(segments!=31*31*13||axes[0]==0||axes[1]==0||axes[2]==0)
            throw new AssertionError("Missing path orientation coverage: "+
                    segments+" axes="+axes[0]+","+axes[1]+","+axes[2]);
        System.out.println("NETHER_TUBE_CONSTANTS_EXACT_PASS segments="+segments
                +" x="+axes[0]+" z="+axes[1]+" vertical="+axes[2]);
    }
}
