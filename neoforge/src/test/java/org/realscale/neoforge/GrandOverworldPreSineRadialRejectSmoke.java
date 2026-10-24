package org.realscale.neoforge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

/**
 * Test.39: enforce bit-for-bit equality with the independent original
 * tunnelStrength(Hall, Passage, bend seed) for every sample whose original
 * 3D tube may carve, and original raw +0.0 for every new pre-sine
 * horizontal/lateral exclusion. This test reconstructs the new guard from
 * immutable source geometry WITHOUT calling the optimized guard itself.
 */
public final class GrandOverworldPreSineRadialRejectSmoke {
    private GrandOverworldPreSineRadialRejectSmoke() {}
    private static final Random RANDOM=new Random(0x39261024L);
    private static long samples,originalPositive,newEarlyZero,verticalEarly,
            lateralEarly,crossingYBound,shafts,arteries,nearRadius;
    private static Field field(Class<?> cls,String name)throws Exception {
        Field f=cls.getDeclaredField(name);f.setAccessible(true);return f;
    }
    private static Object get(Object obj,String name)throws Exception {
        return field(obj.getClass(),name).get(obj);
    }
    private static double dbl(Object obj,String name)throws Exception {
        return field(obj.getClass(),name).getDouble(obj);
    }
    private static int integer(Object obj,String name)throws Exception {
        return field(obj.getClass(),name).getInt(obj);
    }
    private static boolean flag(Object obj,String name)throws Exception {
        return field(obj.getClass(),name).getBoolean(obj);
    }
    private static long raw(double d){return Double.doubleToRawLongBits(d);}
    private static void require(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }
    private static int exclusionReason(Object g,double x,double y,double z)
            throws Exception {
        // Precisely reconstruct the original frozen tube() AABB, t,
        // bending and signed offsets; only these values may determine
        // whether Math.sin is provably unnecessary at this sample.
        if(x<dbl(g,"minX")||x>dbl(g,"maxX")
                ||z<dbl(g,"minZ")||z>dbl(g,"maxZ")
                ||y<dbl(g,"minY")||y>dbl(g,"maxY"))return 0;
        if(dbl(g,"len")<1)return 0;
        var a=(GrandOverworldCaveNetwork.Hall)get(g,"a");
        double vx=dbl(g,"vx"),vy=dbl(g,"vy"),vz=dbl(g,"vz");
        double t=((x-a.x())*vx+(y-a.y())*vy+(z-a.z())*vz)/dbl(g,"len");
        if(t<=0||t>=1)return 0;
        double curve=4*t*(1-t);
        double bendA=integer(g,"bendA")*curve;
        double bendB=integer(g,"bendB")*curve;
        double cx=a.x()+t*vx,cy=a.y()+t*vy,cz=a.z()+t*vz;
        boolean vertical=flag(g,"vertical");
        if(vertical) {
            cx+=bendA*0.6;cz+=bendB*0.6;
        } else if(flag(g,"xDominant")) {
            cz+=bendA;cy+=bendB;
        } else {
            cx+=bendA;cy+=bendB;
        }
        double yy=y-cy;
        if(Math.abs(yy)>dbl(g,"preSineMaxAbsY")){
            crossingYBound++;
            return 0;
        }
        double width=((GrandOverworldCaveNetwork.Passage)get(g,"section"))
                .radius()+1.0;
        double xx=x-cx,zz=z-cz;
        if(vertical) {
            if(Math.abs(xx)>width||Math.abs(zz)>width)return 1;
        } else {
            double horiz=dbl(g,"horiz");
            if(horiz<1)return 0;
            double lateral=(-vz*xx+vx*zz)/horiz;
            if(Math.abs(lateral)>width)return 2;
        }
        return 0;
    }

    private static void inspect(Object g,Method optimized)throws Exception {
        if(g==null)return;
        var a=(GrandOverworldCaveNetwork.Hall)get(g,"a");
        var b=(GrandOverworldCaveNetwork.Hall)get(g,"b");
        var passage=(GrandOverworldCaveNetwork.Passage)get(g,"section");
        long key=field(g.getClass(),"key").getLong(g);
        for(int i=0;i<132;i++){
            double t=(i%33)/32.0;
            double reach=passage.radius()*(i%4==0?2.5:i%4==1?1.0
                    :i%4==2?0.25:1.9);
            double x=a.x()+(b.x()-a.x())*t
                    +(RANDOM.nextDouble()-0.5)*2.0*reach;
            double z=a.z()+(b.z()-a.z())*t
                    +(RANDOM.nextDouble()-0.5)*2.0*reach;
            double y=a.y()+(b.y()-a.y())*t
                    +(RANDOM.nextDouble()-0.5)*2.0*reach*passage.verticalScale();
            if(i%23==0) {
                // Fine-grained cases at the maximum cross-axis reach;
                // they should never be prematurely rejected.
                x=a.x()+(b.x()-a.x())*t+passage.radius()
                        +(i%2==0?0.999999:1.000001);
                z=a.z()+(b.z()-a.z())*t;
                nearRadius++;
            }
            double frozen=GrandOverworldCaveNetwork.tunnelStrength(
                    x,y,z,a,b,passage,key);
            double current=(double)optimized.invoke(null,x,y,z,g);
            require(raw(frozen)==raw(current),
                    "Test.39 pre-sine radial gate changed original tunnel"
                    +" at "+x+","+y+","+z+" reference="+frozen
                    +" optimized="+current);
            int reason=exclusionReason(g,x,y,z);
            if(reason>0) {
                require(raw(frozen)==raw(0.0),
                        "Test.39 radial gate excluded original nonzero cave"
                        +" at "+x+","+y+","+z+" original="+frozen);
                newEarlyZero++;
                if(reason==1)verticalEarly++;
                else lateralEarly++;
            }
            if(frozen>0)originalPositive++;
            samples++;
        }
    }

    public static void main(String[] args)throws Exception {
        var net=GrandOverworldCaveNetwork.class;
        ThreadLocal<?> thread=(ThreadLocal<?>)field(net,"LOCAL").get(null);
        thread.remove();
        Object worker=thread.get();
        Method select=worker.getClass().getDeclaredMethod("get",int.class,int.class);
        select.setAccessible(true);
        Class<?> type=Class.forName(net.getName()+"$TunnelGeometry");
        Method candidate=net.getDeclaredMethod("cachedTunnelStrength",
                double.class,double.class,double.class,type);
        candidate.setAccessible(true);
        for(int gx=-4;gx<=4;gx+=2)for(int gz=-4;gz<=4;gz+=2) {
            Object cell=select.invoke(worker,gx,gz);
            var corridors=(Object[][])get(cell,"corridorTubes");
            var satellites=(Object[][])get(cell,"sideTubes");
            var shaftsArray=(Object[])get(cell,"shaftTubes");
            var arteriesArray=(Object[][][])get(cell,"arteryTubes");
            for(Object[] layer:corridors)
                for(Object tube:layer)inspect(tube,candidate);
            for(Object[] layer:satellites)
                for(Object tube:layer)inspect(tube,candidate);
            for(Object shaft:shaftsArray){inspect(shaft,candidate);shafts++;}
            for(Object[][] tier:arteriesArray)
                for(Object[] axis:tier)
                    for(Object tube:axis)if(tube!=null){
                        inspect(tube,candidate);arteries++;
                    }
        }
        require(samples>75000 && originalPositive>300
                    && newEarlyZero>1000 && lateralEarly>500
                    && crossingYBound>1000 && shafts>0 && arteries>0
                    && nearRadius>2000,
                "Test.39 missing realistic proof or lateral rejection "
                +"samples="+samples+" fullPositive="+originalPositive
                +" lateralEarly="+lateralEarly
                +" verticalEarly="+verticalEarly
                +" existingYSkip="+crossingYBound);
        System.out.println("OVERWORLD_TEST39_PRESINE_RADIAL_RAW_PARITY_PASS"
                +" exactOriginalSamples="+samples
                +" positiveOriginalCaves="+originalPositive
                +" newEarlyZero="+newEarlyZero
                +" lateralEarly="+lateralEarly
                +" verticalEarly="+verticalEarly
                +" oldPreSineYSkip="+crossingYBound
                +" nearRadiusChecks="+nearRadius
                +" shaftTubes="+shafts+" arteryTubes="+arteries
                +" avoidedTrigCallsOnlyNotChunkThroughput=true");
    }
}
