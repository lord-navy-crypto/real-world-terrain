package org.realscale.neoforge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test.26: a same-region collision must no longer make two hot Nether
 * columns invalidate one another. Proves exact 19-bit masks, bounded storage,
 * safe three-way eviction, different region epochs and thread isolation.
 * Count avoided old direct-map misses, NOT a speculative chunks/s speedup.
 */
public final class GrandNetherColumnMaskTwoWaySmoke {
    private GrandNetherColumnMaskTwoWaySmoke() {}

    private static Field field(Class<?> c,String name)throws Exception {
        Field f=c.getDeclaredField(name);f.setAccessible(true);return f;
    }
    private static Object read(Object x,String name)throws Exception {
        return field(x.getClass(),name).get(x);
    }
    private static void yes(boolean value,String why) {
        if(!value)throw new AssertionError(why);
    }
    private static int oldSlot(int x,int z) {
        return (x*0x9e3779b9 ^ z*0x85ebca6b)&511;
    }
    private static int bucket(int x,int z) {
        return (x*0x9e3779b9 ^ z*0x85ebca6b)&255;
    }
    private static boolean retained(Object cache,int x,int z)throws Exception {
        int[] xx=(int[])read(cache,"columnX");
        int[] zz=(int[])read(cache,"columnZ");
        int[] epochs=(int[])read(cache,"columnEpoch");
        int epoch=field(cache.getClass(),"epoch").getInt(cache);
        int first=bucket(x,z)<<1;
        for(int i=first;i<first+2;i++)
            if(epochs[i]==epoch && xx[i]==x && zz[i]==z)
                return true;
        return false;
    }
    private static int freshMask(Object cell,Method evaluate,int x,int z)
            throws Exception {
        return (int)evaluate.invoke(null,cell,x-5.0,x+5.0,z-4.0,z+4.0);
    }
    public static void main(String[] args)throws Exception {
        Class<?> network=GrandNetherCaveNetwork.class;
        var store=(ThreadLocal<?>)field(network,"CACHED_HALLS").get(null);
        store.remove();
        Object regions=store.get();
        Method get=regions.getClass().getDeclaredMethod("get",int.class,int.class);
        get.setAccessible(true);
        Class<?> cellType=Class.forName(network.getName()+"$CellCache");
        Method evaluate=network.getDeclaredMethod("componentMask",cellType,
                double.class,double.class,double.class,double.class);
        evaluate.setAccessible(true);
        Object cell=get.invoke(regions,0,0);
        yes(((int[])read(cell,"columnX")).length==512,
                "The cache must keep its original 512 entry bound");
        yes(((byte[])read(cell,"columnVictim")).length==256,
                "Two-way cache must have 256 buckets");
        int wanted=oldSlot(47,59);
        List<int[]> colliders=new ArrayList<>();
        outer:for(int x=20;x<236;x++)for(int z=20;z<236;z++) {
            if(oldSlot(x,z)==wanted) {
                colliders.add(new int[]{x,z});
                if(colliders.size()==3)break outer;
            }
        }
        yes(colliders.size()==3,"Expected 3 old-direct-map collisions");
        int[] a=colliders.get(0),b=colliders.get(1),c=colliders.get(2);
        yes(bucket(a[0],a[1])==bucket(b[0],b[1])
                && bucket(a[0],a[1])==bucket(c[0],c[1]),
                "All three columns must collide in the optimized bucket");

        // The Test.25 direct-mapped baseline lost EVERY entry on A/B/A.
        int baselineOldSlot=-1, oldMisses=0;
        int newMisses=0, compared=0;
        for(int i=0;i<12000;i++) {
            int[] pos=(i&1)==0?a:b;
            if(baselineOldSlot!=i%2+1) {
                oldMisses++;
                baselineOldSlot=i%2+1;
            }
            if(!retained(cell,pos[0],pos[1]))newMisses++;
            int mask=GrandNetherCaveNetwork.componentMaskForColumn(pos[0],pos[1]);
            yes(mask==freshMask(cell,evaluate,pos[0],pos[1]),
                    "Two-way cache returned the wrong 19-bit mask");
            compared++;
        }
        yes(newMisses==2 && oldMisses==12000,
                "A/B cache retention did not remove repeated mask recomputations: "
                +oldMisses+" original vs "+newMisses+" two-way");
        yes(retained(cell,a[0],a[1]) && retained(cell,b[0],b[1]),
                "Colliding hot columns failed to retain two ways");
        // An extra colliding column forces replacement but must NEVER return
        // a stale mask from either of the two evicted cache entries.
        int third=GrandNetherCaveNetwork.componentMaskForColumn(c[0],c[1]);
        yes(third==freshMask(cell,evaluate,c[0],c[1]),
                "Third-way collision returned stale column components");
        yes(retained(cell,c[0],c[1]),
                "Third column not present after bounded eviction");
        for(int[] pos:colliders) {
            int expected=freshMask(cell,evaluate,pos[0],pos[1]);
            int actual=GrandNetherCaveNetwork.componentMaskForColumn(pos[0],pos[1]);
            yes(actual==expected,"Cache eviction altered column mask bits");
            for(int y:new int[]{144,205,280,348,440}) {
                double wx=pos[0]+2.0,wz=pos[1]-1.5;
                long raw=Double.doubleToRawLongBits(
                        GrandNetherCaveNetwork.strength(wx,y,wz));
                long optimized=Double.doubleToRawLongBits(
                        GrandNetherCaveNetwork.strengthFromColumnWithMask(
                                pos[0],pos[1],wx,y,wz,actual));
                yes(raw==optimized,"Mask collision altered warped cave density");
            }
        }
        // Cross-region transitions retain separate slot epoch and geometry.
        GrandNetherCaveNetwork.componentMaskForColumn(350,78);
        int maskA=GrandNetherCaveNetwork.componentMaskForColumn(a[0],a[1]);
        yes(maskA==freshMask(cell,evaluate,a[0],a[1]),
                "Switching Nether graph changed a 19-bit column mask");

        AtomicReference<Throwable> failure=new AtomicReference<>();
        Thread[] threads=new Thread[4];
        for(int i=0;i<threads.length;i++) {
            final int worker=i;
            threads[i]=new Thread(()->{
                try{
                    Random rng=new Random(240626L+worker);
                    for(int k=0;k<2400;k++) {
                        int[] pos=colliders.get((k+worker)%colliders.size());
                        int x=pos[0],z=pos[1];
                        int mask=GrandNetherCaveNetwork.componentMaskForColumn(x,z);
                        // Reference does NOT use any cache lookup for its mask.
                        int expected=freshMask(get.invoke(store.get(),0,0),evaluate,x,z);
                        yes(mask==expected,"Thread-local mask collision parity");
                        if((k&63)==0) {
                            double shift=rng.nextDouble()*2-1;
                            double wx=x+shift*5.0,wz=z-shift*4.0;
                            double wy=205+shift*2.0;
                            long raw=Double.doubleToRawLongBits(
                                    GrandNetherCaveNetwork.strength(wx,wy,wz));
                            long masked=Double.doubleToRawLongBits(
                                    GrandNetherCaveNetwork.strengthFromColumnWithMask(
                                            x,z,wx,wy,wz,mask));
                            yes(raw==masked,"Per-worker cached mask changed strength");
                        }
                    }
                }catch(Throwable e){failure.compareAndSet(null,e);}
            },"nether-column-two-way-"+i);
            threads[i].start();
        }
        for(Thread t:threads)t.join();
        if(failure.get()!=null)
            throw new AssertionError("Two-way Nether cache worker isolation",failure.get());
        System.out.println("NETHER_512_SLOT_TWO_WAY_MASK_PASS: paired_requests=12000"
                +" original_direct_misses="+oldMisses
                +" candidate_two_way_misses="+newMisses
                +" strict_mask_parity="+compared+" workers=4");
    }
}
