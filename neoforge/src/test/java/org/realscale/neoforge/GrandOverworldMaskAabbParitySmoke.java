package org.realscale.neoforge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

/**
 * Rebuild the frozen pre-Test.22 47-bit column mask independently using
 * the original Hall/Passage endpoint bounds. Compare to the candidate
 * cached-AABB mask for the same geometry and sampling coordinates.
 *
 * No Minecraft dependency, no approximate/tolerance based assertions.
 */
public final class GrandOverworldMaskAabbParitySmoke {
    private static long checked,nonzero,zero,rooms,tunnels;
    private static Field getField(Class<?> c,String name)throws Exception {
        Field f=c.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }
    private static Object get(Object o,String field)throws Exception {
        return getField(o.getClass(),field).get(o);
    }
    private static void require(boolean ok,String why) {
        if(!ok)throw new AssertionError(why);
    }
    private static boolean hallCouldTouch(GrandOverworldCaveNetwork.Hall hall,
            double loX,double hiX,double loZ,double hiZ) {
        return loX<=hall.x()+hall.radiusX() && hiX>=hall.x()-hall.radiusX()
            && loZ<=hall.z()+hall.radiusZ() && hiZ>=hall.z()-hall.radiusZ();
    }
    // Frozen original broad-phase formula copied from Test.21.
    private static boolean tunnelCouldTouch(GrandOverworldCaveNetwork.Hall a,
            GrandOverworldCaveNetwork.Hall b,
            GrandOverworldCaveNetwork.Passage section,
            double loX,double hiX,double loZ,double hiZ) {
        double reach=section.radius()+17.0;
        return loX<=Math.max(a.x(),b.x())+reach
            && hiX>=Math.min(a.x(),b.x())-reach
            && loZ<=Math.max(a.z(),b.z())+reach
            && hiZ>=Math.min(a.z(),b.z())-reach;
    }
    private static long originalMask(Object region,
            double loX,double hiX,double loZ,double hiZ)throws Exception {
        var halls=(GrandOverworldCaveNetwork.Hall[][])get(region,"halls");
        var galleries=(GrandOverworldCaveNetwork.Hall[][])get(region,"galleries");
        var widths=(GrandOverworldCaveNetwork.Passage[][])get(region,"widths");
        var sideWidths=(GrandOverworldCaveNetwork.Passage[][])get(region,"sideWidths");
        var shafts=(GrandOverworldCaveNetwork.Passage[])get(region,"shafts");
        var arteryA=(GrandOverworldCaveNetwork.Hall[][][])get(region,"arteryA");
        var arteryB=(GrandOverworldCaveNetwork.Hall[][][])get(region,"arteryB");
        var arteryWidth=(GrandOverworldCaveNetwork.Passage[][][])get(region,"arteryWidth");
        long mask=0L;
        for(int layer=0;layer<GrandOverworldCaveNetwork.LAYERS;layer++) {
            int shift=layer*9;
            var center=halls[layer][0];
            if(hallCouldTouch(center,loX,hiX,loZ,hiZ))mask|=1L<<shift;
            for(int dir=0;dir<4;dir++)
                if(tunnelCouldTouch(center,halls[layer][dir+1],
                        widths[layer][dir],loX,hiX,loZ,hiZ))
                    mask|=1L<<(shift+1+dir);
            for(int i=0;i<2;i++) {
                var satellite=galleries[layer][i];
                if(hallCouldTouch(satellite,loX,hiX,loZ,hiZ))
                    mask|=1L<<(shift+5+2*i);
                if(tunnelCouldTouch(center,satellite,sideWidths[layer][i],
                        loX,hiX,loZ,hiZ))
                    mask|=1L<<(shift+6+2*i);
            }
        }
        for(int layer=0;layer<GrandOverworldCaveNetwork.LAYERS-1;layer++)
            if(tunnelCouldTouch(halls[layer][0],halls[layer+1][0],
                    shafts[layer],loX,hiX,loZ,hiZ))
                mask|=1L<<(36+layer);
        for(int tier=0;tier<2;tier++)for(int axis=0;axis<2;axis++)
            for(int group=0;group<2;group++) {
                var a=arteryA[tier][axis][group];
                if(a!=null && tunnelCouldTouch(a,arteryB[tier][axis][group],
                        arteryWidth[tier][axis][group],loX,hiX,loZ,hiZ))
                    mask|=1L<<(39+tier*4+axis*2+group);
            }
        return mask;
    }
    private static void check(Object region,Method newMask,
            double x,double z)throws Exception {
        double loX=x-6.0,hiX=x+6.0,loZ=z-5.0,hiZ=z+5.0;
        long old=originalMask(region,loX,hiX,loZ,hiZ);
        long now=(long)newMask.invoke(null,region,loX,hiX,loZ,hiZ);
        require(old==now,"47-bit Overworld broad-phase mask changed at "
                +x+","+z+" previous="+Long.toHexString(old)
                +" new="+Long.toHexString(now));
        checked++;
        if(old==0L)zero++;else nonzero++;
        rooms+=Long.bitCount(old & 0x9249249249L);
        tunnels+=Long.bitCount(old);
    }
    public static void main(String[] args)throws Exception {
        Class<?> net=GrandOverworldCaveNetwork.class;
        Field local=getField(net,"LOCAL");
        ThreadLocal<?> worker=(ThreadLocal<?>)local.get(null);
        Object regionCache=worker.get();
        Method get=regionCache.getClass().getDeclaredMethod("get",int.class,int.class);
        get.setAccessible(true);
        Class<?> regionType=Class.forName(net.getName()+"$Cache");
        Method mask=net.getDeclaredMethod("columnComponentMask",
                regionType,double.class,double.class,double.class,double.class);
        mask.setAccessible(true);
        Random rng=new Random(0x22ab17eaL);
        for(int gx=-2;gx<=2;gx++)for(int gz=-2;gz<=2;gz++) {
            Object region=get.invoke(regionCache,gx,gz);
            int centerX=gx*GrandOverworldCaveNetwork.CELL+GrandOverworldCaveNetwork.CELL/2;
            int centerZ=gz*GrandOverworldCaveNetwork.CELL+GrandOverworldCaveNetwork.CELL/2;
            for(int i=0;i<160;i++) {
                double x=centerX+rng.nextInt(1100)-550+(i%5==0?0.5:0);
                double z=centerZ+rng.nextInt(1100)-550+(i%7==0?0.25:0);
                check(region,mask,x,z);
            }
            // Inclusive boundary and exact tangent probes around EVERY hall.
            var halls=(GrandOverworldCaveNetwork.Hall[][])get(region,"halls");
            for(var layer:halls)for(var hall:layer)for(double dx:
                    new double[]{-200,-1,0,1,200})for(double dz:
                    new double[]{-200,0,200})
                check(region,mask,hall.x()+dx,hall.z()+dz);
        }
        require(checked>5000 && nonzero>100 && zero>100 && tunnels>5000,
                "Insufficient empty/active/near-border mask coverage");
        System.out.println("OVERWORLD_PRECOMPUTED_MASK_AABB_EXACT_PASS masks="+checked
                +" nonempty="+nonzero+" empty="+zero+" activeBits="+tunnels);
    }
}
