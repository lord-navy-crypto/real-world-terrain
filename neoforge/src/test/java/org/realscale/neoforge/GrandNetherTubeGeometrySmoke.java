package org.realscale.neoforge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

/** Independent frozen Test.19 tube expression versus Test.20 compiled segments. */
public final class GrandNetherTubeGeometrySmoke {
    private static int checked;
    private static void require(boolean ok,String message) {
        if(!ok)throw new AssertionError(message);
    }
    private static Field field(Class<?> type,String name) throws Exception {
        Field f=type.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }
    private static Object get(Field f,Object instance) throws Exception {
        return f.get(instance);
    }
    private static double frozenTube(double x,double y,double z,
            GrandNetherCaveNetwork.Hall a,GrandNetherCaveNetwork.Hall b,
            double radius,long key) {
        // EXACT expression from the previous candidate, including endpoint
        // canonicalization, division (not reciprocal), and arithmetic order.
        if (a.x() > b.x() || (a.x() == b.x() && a.z() > b.z())
                || (a.x() == b.x() && a.z() == b.z() && a.y() > b.y())) {
            GrandNetherCaveNetwork.Hall temp=a; a=b; b=temp;
        }
        double reach = radius + 4.0;
        if (x < Math.min(a.x(),b.x())-reach
                || x > Math.max(a.x(),b.x())+reach
                || y < Math.min(a.y(),b.y())-reach
                || y > Math.max(a.y(),b.y())+reach
                || z < Math.min(a.z(),b.z())-reach
                || z > Math.max(a.z(),b.z())+reach) return 0;
        double vx=b.x()-a.x(), vy=b.y()-a.y(), vz=b.z()-a.z();
        double t=((x-a.x())*vx+(y-a.y())*vy+(z-a.z())*vz)
                  /(vx*vx+vy*vy+vz*vz);
        if (t <= 0 || t >= 1) return 0;
        double curve=4.0*t*(1.0-t);
        double bend1=((int)Math.floorMod(key,9L)-4)*curve;
        double bend2=((int)Math.floorMod(key >>> 24,7L)-3)*curve;
        double cx=a.x()+t*vx, cy=a.y()+t*vy, cz=a.z()+t*vz;
        if (Math.abs(vx)>=Math.abs(vz) && Math.abs(vx)>=Math.abs(vy)) {
            cy+=bend2; cz+=bend1;
        } else if (Math.abs(vz)>=Math.abs(vy)) {
            cx+=bend1; cy+=bend2;
        } else {
            cx+=bend1*0.5; cz+=bend2*0.5;
        }
        double dx=x-cx, dy=y-cy, dz=z-cz;
        double ratio=(dx*dx+dy*dy+dz*dz)/(radius*radius);
        return ratio>=1 ? 0 : 1-ratio;
    }

    private static void check(Object segment,Method optimized,Random random)
            throws Exception {
        Class<?> type=segment.getClass();
        var a=(GrandNetherCaveNetwork.Hall)get(field(type,"a"),segment);
        var b=(GrandNetherCaveNetwork.Hall)get(field(type,"b"),segment);
        double radius=field(type,"radius").getDouble(segment);
        long key=field(type,"key").getLong(segment);
        // Probe along the entire corridor as well as well outside its box.
        for(int n=0;n<120;n++) {
            double t=(n%30)/29.0;
            double dx=(random.nextDouble()-0.5)*radius*4.0;
            double dy=(random.nextDouble()-0.5)*radius*4.0;
            double dz=(random.nextDouble()-0.5)*radius*4.0;
            double x=a.x()+(b.x()-a.x())*t+dx;
            double y=a.y()+(b.y()-a.y())*t+dy;
            double z=a.z()+(b.z()-a.z())*t+dz;
            double before=frozenTube(x,y,z,a,b,radius,key);
            double after=(double)optimized.invoke(null,x,y,z,segment);
            require(Double.doubleToRawLongBits(before)==Double.doubleToRawLongBits(after),
                    "Precomputed Nether tunnel changed raw density bits at "+x+","+y+","+z);
            checked++;
        }
    }
    public static void main(String[] args) throws Exception {
        Field cacheField=field(GrandNetherCaveNetwork.class,"CACHED_HALLS");
        ThreadLocal<?> perWorker=(ThreadLocal<?>)cacheField.get(null);
        Object regions=perWorker.get();
        Method load=regions.getClass().getDeclaredMethod("get",int.class,int.class);
        load.setAccessible(true);
        Class<?> tubeClass=Class.forName(GrandNetherCaveNetwork.class.getName()+"$TubeSegment");
        Method tube=GrandNetherCaveNetwork.class.getDeclaredMethod("tube",
                double.class,double.class,double.class,tubeClass);
        tube.setAccessible(true);
        Random random=new Random(0x204a7d0aL);
        for(int gx=-4;gx<=4;gx+=2)for(int gz=-4;gz<=4;gz+=2) {
            Object cell=load.invoke(regions,gx,gz);
            Object[][] corridors=(Object[][])get(field(cell.getClass(),"corridors"),cell);
            Object[][] branches=(Object[][])get(field(cell.getClass(),"branches"),cell);
            for(Object[] row:corridors)for(Object segment:row)
                check(segment,tube,random);
            for(Object[] row:branches)for(Object segment:row)
                check(segment,tube,random);
            check(get(field(cell.getClass(),"interlayer"),cell),tube,random);
        }
        require(checked>30000,"Tube geometry replay must exercise more than 30k points");
        System.out.println("NETHER_CACHED_TUBE_RAW_BIT_PARITY_PASS probes="+checked
                +" across regions, halls, galleries and interlayer shafts");
    }
}
