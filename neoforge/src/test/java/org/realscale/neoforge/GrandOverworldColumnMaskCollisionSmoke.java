package org.realscale.neoforge;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Check 512-slot two-way Overworld broad-phase mask reuse and bit parity. */
public final class GrandOverworldColumnMaskCollisionSmoke {
    private GrandOverworldColumnMaskCollisionSmoke() {}
    private static void check(boolean ok,String why) {
        if(!ok)throw new AssertionError(why);
    }
    private static Object read(Object object,String field)throws Exception {
        Field f=object.getClass().getDeclaredField(field);
        f.setAccessible(true);
        return f.get(object);
    }
    public static void main(String[] args)throws Exception {
        List<int[]> pairs=new ArrayList<>();
        int expected=GrandOverworldCaveNetwork.columnBucket(300,300);
        outer:for(int x=155;x<635;x++)for(int z=155;z<635;z++) {
            if(GrandOverworldCaveNetwork.columnBucket(x,z)==expected) {
                pairs.add(new int[]{x,z});
                if(pairs.size()==3)break outer;
            }
        }
        check(pairs.size()==3,"No three safe collision candidates");
        var owner=(ThreadLocal<?>)readStatic(GrandOverworldCaveNetwork.class,"LOCAL");
        Object region=owner.get();
        for(int i=0;i<2;i++)
            GrandOverworldCaveNetwork.mayContainVoidNearColumn(
                    pairs.get(i)[0],pairs.get(i)[1]);
        Object cache=read(region,"last");
        int[] xs=(int[])read(cache,"columnX");
        int[] zs=(int[])read(cache,"columnZ");
        int[] epochs=(int[])read(cache,"columnEpoch");
        int epoch=(int)read(cache,"epoch");
        int base=expected<<1;
        for(int i=0;i<2;i++) {
            boolean match=false;
            for(int k=base;k<=base+1;k++)
                if(epochs[k]==epoch && xs[k]==pairs.get(i)[0]
                        && zs[k]==pairs.get(i)[1])match=true;
            check(match,"Two-way region column was evicted despite free slot");
        }
        for(int i=0;i<2000;i++)for(int j=0;j<2;j++)
            GrandOverworldCaveNetwork.mayContainVoidNearColumn(
                    pairs.get(j)[0],pairs.get(j)[1]);
        // Test the actual optimized geometry over varied Y and warped XZ.
        for(int[] point:pairs)for(int y:new int[]{-40,110,205,395,790,1330,1660}) {
            double x=point[0]+3.0,z=point[1]-2.0;
            long original=Double.doubleToRawLongBits(
                    GrandOverworldCaveNetwork.strength(x,y,z));
            long optimized=Double.doubleToRawLongBits(
                    GrandOverworldCaveNetwork.strengthFromColumn(
                            point[0],point[1],x,y,z));
            check(original==optimized,"Collision altered cave geometry at "+
                    point[0]+","+y+","+point[1]);
        }
        // A third key must force an eviction without returning a false mask.
        int[] third=pairs.get(2);
        GrandOverworldCaveNetwork.mayContainVoidNearColumn(third[0],third[1]);
        for(int[] point:pairs)
            for(int y:new int[]{205,790,1330}) {
                double x=point[0],z=point[1];
                check(Double.doubleToRawLongBits(
                        GrandOverworldCaveNetwork.strength(x,y,z))==
                        Double.doubleToRawLongBits(
                        GrandOverworldCaveNetwork.strengthFromColumn(
                                point[0],point[1],x,y,z)),
                        "Eviction cross-contaminated cave density");
            }
        System.out.println("REALSCALE_OVERWORLD_COLUMN_2WAY_PASS masks=3 "
                +"alternations=4000 bit_parity=30");
    }
    private static Object readStatic(Class<?> cls,String field)throws Exception {
        Field f=cls.getDeclaredField(field);
        f.setAccessible(true);
        return f.get(null);
    }
}
