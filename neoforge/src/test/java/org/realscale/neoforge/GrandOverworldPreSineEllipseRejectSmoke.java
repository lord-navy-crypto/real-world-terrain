package org.realscale.neoforge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

/**
 * Test.40: independent frozen original tunnelStrength 3D parity and
 * conservative combined elliptical pre-sine reject. This verifies the
 * exact baseline density at each newly pruned corner sample; it does
 * NOT claim Minecraft chunks/s or server TPS improvement.
 */
public final class GrandOverworldPreSineEllipseRejectSmoke {
    private GrandOverworldPreSineEllipseRejectSmoke() {}
    private static final Random RNG=new Random(0x40261024L);
    private static long total,carved,newEarlyZero,previousY,previousRadial,
            shaftCandidates,arteryCandidates,nearBounds;
    private static Field field(Class<?> cls,String name)throws Exception {
        Field f=cls.getDeclaredField(name);f.setAccessible(true);return f;
    }
    private static Object get(Object obj,String name)throws Exception {
        return field(obj.getClass(),name).get(obj);
    }
    private static double number(Object obj,String name)throws Exception {
        return field(obj.getClass(),name).getDouble(obj);
    }
    private static int integer(Object obj,String name)throws Exception {
        return field(obj.getClass(),name).getInt(obj);
    }
    private static boolean bool(Object obj,String name)throws Exception {
        return field(obj.getClass(),name).getBoolean(obj);
    }
    private static long raw(double d){return Double.doubleToRawLongBits(d);}
    private static void check(boolean b,String message) {
        if(!b)throw new AssertionError(message);
    }
    private static boolean wouldSkipEllipse(Object g,double x,double y,double z)
            throws Exception {
        if(x<number(g,"minX")||x>number(g,"maxX")
                ||z<number(g,"minZ")||z>number(g,"maxZ")
                ||y<number(g,"minY")||y>number(g,"maxY"))return false;
        double len=number(g,"len");
        if(len<1)return false;
        var a=(GrandOverworldCaveNetwork.Hall)get(g,"a");
        double vx=number(g,"vx"),vy=number(g,"vy"),vz=number(g,"vz");
        double t=((x-a.x())*vx+(y-a.y())*vy+(z-a.z())*vz)/len;
        if(t<=0||t>=1)return false;
        double curve=4*t*(1-t);
        double bendA=integer(g,"bendA")*curve;
        double bendB=integer(g,"bendB")*curve;
        double cx=a.x()+t*vx,cy=a.y()+t*vy,cz=a.z()+t*vz;
        boolean vertical=bool(g,"vertical");
        if(vertical) {
            cx+=bendA*0.6;cz+=bendB*0.6;
        } else if(bool(g,"xDominant")) {
            cz+=bendA;cy+=bendB;
        } else {
            cx+=bendA;cy+=bendB;
        }
        double yy=y-cy;
        if(Math.abs(yy)>number(g,"preSineMaxAbsY")) {
            previousY++;
            return false;
        }
        double xx=x-cx,zz=z-cz;
        double lateral=0;
        double maxWidth=((GrandOverworldCaveNetwork.Passage)get(g,"section"))
                .radius()+1.0;
        if(vertical) {
            if(Math.abs(xx)>maxWidth||Math.abs(zz)>maxWidth) {
                previousRadial++;
                return false;
            }
        } else {
            double horiz=number(g,"horiz");
            if(horiz<1)return false;
            lateral=(-vz*xx+vx*zz)/horiz;
            if(Math.abs(lateral)>maxWidth) {
                previousRadial++;
                return false;
            }
        }
        double radialSq=vertical ? xx*xx+zz*zz : lateral*lateral;
        double yySq=yy*yy;
        double product=radialSq*number(g,"preSineMaxHeightSq")
                +yySq*number(g,"preSineMaxWidthSq");
        return product>number(g,"preSineEllipseThreshold");
    }

    private static void compare(Object tube,Method evaluator)throws Exception {
        if(tube==null)return;
        var a=(GrandOverworldCaveNetwork.Hall)get(tube,"a");
        var b=(GrandOverworldCaveNetwork.Hall)get(tube,"b");
        var p=(GrandOverworldCaveNetwork.Passage)get(tube,"section");
        long key=field(tube.getClass(),"key").getLong(tube);
        double maxWidth=p.radius()+1.0;
        double maxHeight=p.radius()*p.verticalScale()+1.0;
        check(raw(number(tube,"preSineMaxWidthSq"))==raw(maxWidth*maxWidth)
                && raw(number(tube,"preSineMaxHeightSq"))==raw(maxHeight*maxHeight)
                && raw(number(tube,"preSineEllipseThreshold"))==
                    raw((maxWidth*maxWidth)*(maxHeight*maxHeight)*1.05),
                "Cached conservative ellipse parameters not frozen formula");
        for(int n=0;n<136;n++) {
            double t=(n%34)/33.0;
            double reach=p.radius()*(n%4==0?2.4:n%4==1?0.7
                    :n%4==2?1.25:0.25);
            double x=a.x()+(b.x()-a.x())*t
                    +(RNG.nextDouble()-0.5)*2.0*reach;
            double z=a.z()+(b.z()-a.z())*t
                    +(RNG.nextDouble()-0.5)*2.0*reach;
            double y=a.y()+(b.y()-a.y())*t
                    +(RNG.nextDouble()-0.5)*2.0*reach*p.verticalScale();
            if(n%19==0) {
                // Near the conservative elliptical envelope: keep the
                // original exact baseline result as the sole authority.
                y=a.y()+(b.y()-a.y())*t
                        +(n%2==0 ? 0.72:-0.72)*maxHeight;
                x=a.x()+(b.x()-a.x())*t+0.72*maxWidth;
                z=a.z()+(b.z()-a.z())*t;
                nearBounds++;
            }
            boolean excluded=wouldSkipEllipse(tube,x,y,z);
            double old=GrandOverworldCaveNetwork.tunnelStrength(
                    x,y,z,a,b,p,key);
            double now=(double)evaluator.invoke(null,x,y,z,tube);
            check(raw(old)==raw(now),
                    "Overworld Test.40 pre-sine ellipse changed original"
                    +" tube strength x="+x+" y="+y+" z="+z
                    +" original="+old+" new="+now);
            if(excluded) {
                check(raw(old)==raw(0.0),
                        "Test.40 ellipse rejected genuine original cave at "
                        +x+","+y+","+z+" rawStrength="+old);
                newEarlyZero++;
            }
            if(old>0)carved++;
            total++;
        }
    }

    public static void main(String[] args)throws Exception {
        var net=GrandOverworldCaveNetwork.class;
        ThreadLocal<?> worker=(ThreadLocal<?>)field(net,"LOCAL").get(null);
        worker.remove();
        Object regions=worker.get();
        Method fetch=regions.getClass().getDeclaredMethod(
                "get",int.class,int.class);
        fetch.setAccessible(true);
        Class<?> geometry=Class.forName(net.getName()+"$TunnelGeometry");
        Method evaluator=net.getDeclaredMethod("cachedTunnelStrength",
                double.class,double.class,double.class,geometry);
        evaluator.setAccessible(true);
        for(int gx=-4;gx<=4;gx+=2)for(int gz=-4;gz<=4;gz+=2) {
            Object c=fetch.invoke(regions,gx,gz);
            var corridors=(Object[][])get(c,"corridorTubes");
            var gallery=(Object[][])get(c,"sideTubes");
            var shafts=(Object[])get(c,"shaftTubes");
            var arteries=(Object[][][])get(c,"arteryTubes");
            for(Object[] layer:corridors)for(Object tube:layer)
                compare(tube,evaluator);
            for(Object[] layer:gallery)for(Object tube:layer)
                compare(tube,evaluator);
            for(Object shaft:shafts) {
                compare(shaft,evaluator);
                shaftCandidates++;
            }
            for(Object[][] tier:arteries)
                for(Object[] axis:tier)
                    for(Object tube:axis)if(tube!=null) {
                        compare(tube,evaluator);
                        arteryCandidates++;
                    }
        }
        check(total>85_000 && newEarlyZero>100
                && carved>300 && nearBounds>2500
                && previousY>2000 && previousRadial>2000
                && shaftCandidates>0 && arteryCandidates>0,
                "Insufficient independent elliptical-noise-skipping coverage "
                +"total="+total+" ellipse="+newEarlyZero+" positive="+carved
                +" YSkipped="+previousY+" radialSkipped="+previousRadial);
        System.out.println("OVERWORLD_TEST40_PRESINE_ELLIPSE_RAW_PARITY_PASS"
                +" originalRawDouble="+total
                +" actualCarved="+carved
                +" newEllipseNoSin="+newEarlyZero
                +" precedingYSkip="+previousY
                +" precedingRadialSkip="+previousRadial
                +" nearEnvelopeSamples="+nearBounds
                +" shaftSegments="+shaftCandidates
                +" arterySegments="+arteryCandidates
                +" trigCallReductionOnlyNotChunkSpeed=true");
    }
}
