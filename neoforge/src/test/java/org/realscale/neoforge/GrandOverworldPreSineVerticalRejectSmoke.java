package org.realscale.neoforge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

/**
 * Test.38: the unchanged original tunnelStrength() is the independent
 * bit-for-bit oracle for a new pre-sine conservative vertical rejection.
 * Every sampled voxel is compared against the original full 3D tube, with
 * deliberately disjoint centerline heights, true carved points, tangent
 * neighborhoods and all four seeded cavern graph component families.
 */
public final class GrandOverworldPreSineVerticalRejectSmoke {
    private GrandOverworldPreSineVerticalRejectSmoke() {}
    private static final Random RNG=new Random(0x38261024L);
    private static long checked,carved,preSineNoVoid,reachCrossed,shafts,arteries;
    private static volatile long timingSink;

    private static Field field(Class<?> cls,String name)throws Exception {
        Field f=cls.getDeclaredField(name);f.setAccessible(true);return f;
    }
    private static Object get(Object obj,String name)throws Exception {
        return field(obj.getClass(),name).get(obj);
    }
    private static double num(Object o,String name)throws Exception {
        return field(o.getClass(),name).getDouble(o);
    }
    private static boolean flag(Object o,String name)throws Exception {
        return field(o.getClass(),name).getBoolean(o);
    }
    private static int numInt(Object o,String name)throws Exception {
        return field(o.getClass(),name).getInt(o);
    }
    private static void require(boolean b,String reason){
        if(!b)throw new AssertionError(reason);
    }
    private static long raw(double d){return Double.doubleToRawLongBits(d);}

    private static boolean wouldRejectBeforeSine(Object tube,
            double x,double y,double z)throws Exception {
        if(x<num(tube,"minX")||x>num(tube,"maxX")
                ||z<num(tube,"minZ")||z>num(tube,"maxZ")
                ||y<num(tube,"minY")||y>num(tube,"maxY"))return false;
        double len=num(tube,"len");
        if(len<1)return false;
        var a=(GrandOverworldCaveNetwork.Hall)get(tube,"a");
        double vx=num(tube,"vx"),vy=num(tube,"vy"),vz=num(tube,"vz");
        double t=((x-a.x())*vx+(y-a.y())*vy+(z-a.z())*vz)/len;
        if(t<=0||t>=1)return false;
        double curve=4*t*(1-t);
        double cy=a.y()+t*vy;
        if(!flag(tube,"vertical")) {
            double displacementB=numInt(tube,"bendB")*curve;
            cy+=displacementB;
        }
        return Math.abs(y-cy)>num(tube,"preSineMaxAbsY");
    }

    private static void compare(Object tube,Method cached)throws Exception {
        if(tube==null)return;
        var a=(GrandOverworldCaveNetwork.Hall)get(tube,"a");
        var b=(GrandOverworldCaveNetwork.Hall)get(tube,"b");
        var p=(GrandOverworldCaveNetwork.Passage)get(tube,"section");
        long key=field(tube.getClass(),"key").getLong(tube);
        require(raw(num(tube,"preSineMaxAbsY"))==
                raw(p.radius()*p.verticalScale()+1.0),
                "Pre-sine maximum Y was not computed from the frozen passage");
        for(int n=0;n<112;n++) {
            // Mix corridor midpoints with near/far Y offsets. For each
            // point, x/z can be far outside the tube; preserve AABB cases.
            double t=(n%28)/27.0;
            double reach=p.radius()*(n%3==0?2.5:n%3==1?1.0:0.25);
            double x=a.x()+(b.x()-a.x())*t
                    +(RNG.nextDouble()-0.5)*2.0*reach;
            double z=a.z()+(b.z()-a.z())*t
                    +(RNG.nextDouble()-0.5)*2.0*reach;
            double y=a.y()+(b.y()-a.y())*t
                    +(RNG.nextDouble()-0.5)*2.0*reach*p.verticalScale();
            if(n%19==0) {
                // Exercise vicinity of the precalculated conservative
                // boundary; all comparison decisions use the original tube.
                y=a.y()+(b.y()-a.y())*t+p.radius()*p.verticalScale()
                        +(n%2==0?0.999999:1.000001);
            }
            boolean rejected=wouldRejectBeforeSine(tube,x,y,z);
            double original=GrandOverworldCaveNetwork.tunnelStrength(
                    x,y,z,a,b,p,key);
            double optimized=(double)cached.invoke(null,x,y,z,tube);
            require(raw(original)==raw(optimized),
                    "Test.38 changed raw original 3D tunnel strength x="
                    +x+" y="+y+" z="+z+" old="+original+" new="+optimized);
            if(rejected) {
                require(raw(original)==raw(0.0),
                        "Pre-sine vertical rejection hid a carved original tube");
                preSineNoVoid++;
            }
            checked++;
            if(original>0)carved++;
            if(n%19==0)reachCrossed++;
        }
    }

    public static void main(String[] args)throws Exception {
        var net=GrandOverworldCaveNetwork.class;
        var local=field(net,"LOCAL");
        ThreadLocal<?> thread=(ThreadLocal<?>)local.get(null);
        thread.remove();
        Object worker=thread.get();
        Method select=worker.getClass().getDeclaredMethod("get",int.class,int.class);
        select.setAccessible(true);
        Class<?> descriptor=Class.forName(net.getName()+"$TunnelGeometry");
        Method cached=net.getDeclaredMethod("cachedTunnelStrength",
                double.class,double.class,double.class,descriptor);
        cached.setAccessible(true);
        for(int gx=-4;gx<=4;gx+=2)for(int gz=-4;gz<=4;gz+=2) {
            Object cell=select.invoke(worker,gx,gz);
            var corridors=(Object[][])get(cell,"corridorTubes");
            var sides=(Object[][])get(cell,"sideTubes");
            var interlayer=(Object[])get(cell,"shaftTubes");
            var arteriesGrid=(Object[][][])get(cell,"arteryTubes");
            for(Object[] layer:corridors)for(Object tube:layer)
                compare(tube,cached);
            for(Object[] layer:sides)for(Object tube:layer)
                compare(tube,cached);
            for(Object tube:interlayer) {
                compare(tube,cached);shafts++;
            }
            for(Object[][] tier:arteriesGrid)
                for(Object[] axis:tier)
                    for(Object tube:axis)if(tube!=null) {
                        compare(tube,cached);arteries++;
                    }
        }
        require(checked>75000 && preSineNoVoid>700
                    && carved>300 && shafts>0 && arteries>0
                    && reachCrossed>2000,
                "Test.38 under-covered pre-sine branch or original carving: "
                +checked+" earlyRejections="+preSineNoVoid
                +" trueCarved="+carved+" shafts="+shafts
                +" arteries="+arteries);
        System.out.println("OVERWORLD_TEST38_PRESINE_Y_REJECT_RAW_PARITY_PASS"
                +" compared="+checked
                +" positiveCarved="+carved
                +" exactZeroBeforeSin="+preSineNoVoid
                +" nearConservativeBound="+reachCrossed
                +" shaftVariants="+shafts
                +" arteryVariants="+arteries
                +" onlyFunctionLevelSinAvoidance=true");
    }
}
