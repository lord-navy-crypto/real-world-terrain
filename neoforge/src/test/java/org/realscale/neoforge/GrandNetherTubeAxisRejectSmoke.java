package org.realscale.neoforge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

/**
 * Test.41 immutable bent-centerline axis reject, against frozen Test.19
 * Nether tube math. No approximate carving or changed density allowed.
 * Work counters are per-test, not Minecraft chunk generation throughput.
 */
public final class GrandNetherTubeAxisRejectSmoke {
    private GrandNetherTubeAxisRejectSmoke() {}
    private static final Random RANDOM=new Random(0x41261024L);
    private static long checked,oldCarved,axisPruned,nearRadius,branchTubes,
            corridorTubes,chimneyTubes;
    private static Field field(Class<?> cls,String name)throws Exception {
        Field f=cls.getDeclaredField(name);f.setAccessible(true);return f;
    }
    private static Object get(Object object,String name)throws Exception {
        return field(object.getClass(),name).get(object);
    }
    private static double dbl(Object object,String name)throws Exception {
        return field(object.getClass(),name).getDouble(object);
    }
    private static int integer(Object object,String name)throws Exception {
        return field(object.getClass(),name).getInt(object);
    }
    private static long raw(double d){return Double.doubleToRawLongBits(d);}
    private static void require(boolean v,String text) {
        if(!v)throw new AssertionError(text);
    }
    private static boolean trulyAxisDisjoint(Object seg,double x,double y,double z)
            throws Exception {
        if(x<dbl(seg,"minX")||x>dbl(seg,"maxX")
                ||y<dbl(seg,"minY")||y>dbl(seg,"maxY")
                ||z<dbl(seg,"minZ")||z>dbl(seg,"maxZ"))return false;
        var a=(GrandNetherCaveNetwork.Hall)get(seg,"a");
        double vx=dbl(seg,"vx"),vy=dbl(seg,"vy"),vz=dbl(seg,"vz");
        double t=((x-a.x())*vx+(y-a.y())*vy+(z-a.z())*vz)
                /dbl(seg,"lenSq");
        if(t<=0||t>=1)return false;
        double curve=4.0*t*(1.0-t);
        double bend1=integer(seg,"bendSeed1")*curve;
        double bend2=integer(seg,"bendSeed2")*curve;
        double cx=a.x()+t*vx,cy=a.y()+t*vy,cz=a.z()+t*vz;
        int axis=integer(seg,"bendAxis");
        if(axis==0){
            cy+=bend2;cz+=bend1;
        }else if(axis==1){
            cx+=bend1;cy+=bend2;
        }else{
            cx+=bend1*0.5;cz+=bend2*0.5;
        }
        double guard=dbl(seg,"radius")+1.0;
        return Math.abs(x-cx)>guard||Math.abs(y-cy)>guard
                ||Math.abs(z-cz)>guard;
    }
    private static void checkTube(Object seg,Method frozen,Method optimized)
            throws Exception {
        var a=(GrandNetherCaveNetwork.Hall)get(seg,"a");
        var b=(GrandNetherCaveNetwork.Hall)get(seg,"b");
        double radius=dbl(seg,"radius");
        long key=field(seg.getClass(),"key").getLong(seg);
        for(int n=0;n<200;n++){
            double t=(n%40)/39.0;
            double extent=radius*(n%5==0?2.5:n%5==1?1.3:
                    n%5==2?0.7:n%5==3?0.1:1.0);
            double x=a.x()+(b.x()-a.x())*t
                    +(RANDOM.nextDouble()-0.5)*2.0*extent;
            double y=a.y()+(b.y()-a.y())*t
                    +(RANDOM.nextDouble()-0.5)*2.0*extent;
            double z=a.z()+(b.z()-a.z())*t
                    +(RANDOM.nextDouble()-0.5)*2.0*extent;
            if(n%19==0) {
                // Sample just inside and outside the one-block slack
                // around a tube centerline. The ORIGINAL formula decides.
                x=a.x()+(b.x()-a.x())*t+radius+
                        (n%2==0?0.999999:1.000001);
                nearRadius++;
            }
            double original=(double)frozen.invoke(null,x,y,z,a,b,radius,key);
            double now=(double)optimized.invoke(null,x,y,z,seg);
            require(raw(original)==raw(now),
                    "Test.41 original Nether 3D tube bits changed at "
                    +x+","+y+","+z+" old="+original+" now="+now);
            if(trulyAxisDisjoint(seg,x,y,z)){
                require(raw(original)==raw(0.0),
                        "Conservative axis guard excluded a real Nether void");
                axisPruned++;
            }
            if(original>0)oldCarved++;
            checked++;
        }
    }
    public static void main(String[] args)throws Exception {
        var net=GrandNetherCaveNetwork.class;
        var geom=GrandNetherTubeGeometrySmoke.class;
        Method frozen=geom.getDeclaredMethod("frozenTube",
                double.class,double.class,double.class,
                GrandNetherCaveNetwork.Hall.class,
                GrandNetherCaveNetwork.Hall.class,double.class,long.class);
        frozen.setAccessible(true);
        Class<?> descriptor=Class.forName(net.getName()+"$TubeSegment");
        Method candidate=net.getDeclaredMethod("tube",double.class,
                double.class,double.class,descriptor);
        candidate.setAccessible(true);
        ThreadLocal<?> thread=(ThreadLocal<?>)field(net,"CACHED_HALLS").get(null);
        thread.remove();
        Object regions=thread.get();
        Method lookup=regions.getClass().getDeclaredMethod(
                "get",int.class,int.class);
        lookup.setAccessible(true);
        for(int gx=-4;gx<=4;gx+=2)for(int gz=-4;gz<=4;gz+=2) {
            Object cell=lookup.invoke(regions,gx,gz);
            Object[][] corridor=(Object[][])get(cell,"corridors");
            Object[][] branch=(Object[][])get(cell,"branches");
            for(Object[] layer:corridor)
                for(Object tube:layer) {
                    checkTube(tube,frozen,candidate);
                    corridorTubes++;
                }
            for(Object[] layer:branch)
                for(Object tube:layer) {
                    checkTube(tube,frozen,candidate);
                    branchTubes++;
                }
            checkTube(get(cell,"interlayer"),frozen,candidate);
            chimneyTubes++;
        }
        require(checked>60000&&axisPruned>2000
                &&oldCarved>300&&nearRadius>2000
                &&corridorTubes>0&&branchTubes>0&&chimneyTubes>0,
                "Nether tube original-bit reference undercoverage: probes="
                +checked+" axisSkip="+axisPruned+" carved="+oldCarved);
        System.out.println("NETHER_TEST41_AXIS_BENT_TUBE_EXACT_PARITY_PASS"
                +" rawOriginalProbes="+checked
                +" positiveCarved="+oldCarved
                +" preNormNoVoid="+axisPruned
                +" nearConservativeBoundary="+nearRadius
                +" corridorSegments="+corridorTubes
                +" gallerySegments="+branchTubes
                +" interlayerSegments="+chimneyTubes
                +" functionOnlyNotChunksPerSecond=true");
    }
}
