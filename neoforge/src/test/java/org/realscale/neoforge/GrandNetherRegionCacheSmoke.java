package org.realscale.neoforge;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test.19 cache-retention proof. The original cave strengths are also checked
 * against the independent frozen pre-optimization Nether cave reference.
 */
public final class GrandNetherRegionCacheSmoke {
    private GrandNetherRegionCacheSmoke() {}

    private static void require(boolean ok,String message) {
        if(!ok)throw new AssertionError(message);
    }
    private static int bucket(int gx,int gz) {
        int hash=gx*0x9e3779b9 ^ Integer.rotateLeft(gz*0x85ebca6b,13);
        return (hash^(hash>>>16))&1;
    }
    private static Field field(Class<?> type,String name) throws Exception {
        Field f=type.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }
    private static Object[] regions(ThreadLocal<?> store) throws Exception {
        Object region=store.get();
        Object[] cells=(Object[])field(region.getClass(),"regions").get(region);
        require(cells.length==4,"Region snapshots must remain bounded to four");
        return cells;
    }
    private static Object snapshot(ThreadLocal<?> store,int gx,int gz)
            throws Exception {
        for(Object cell:regions(store)) {
            if(cell==null)continue;
            Class<?> type=cell.getClass();
            if(field(type,"gx").getInt(cell)==gx
                    && field(type,"gz").getInt(cell)==gz)
                return cell;
        }
        return null;
    }
    private static void probe(int gx,int gz,int layer) {
        var center=GrandNetherCaveNetwork.hall(gx,gz,layer);
        for(int i=0;i<5;i++) {
            double x=center.x()+(i-2)*13.125;
            double y=center.y()+(i-2)*3.375;
            double z=center.z()+(2-i)*6.5;
            long baseline=Double.doubleToRawLongBits(
                    GrandNetherCaveNetworkReference.strength(x,y,z));
            long current=Double.doubleToRawLongBits(
                    GrandNetherCaveNetwork.strength(x,y,z));
            require(baseline==current,
                    "Nether graph parity changed in cell "+gx+","+gz);
            int ox=(int)center.x(), oz=(int)center.z();
            long masked=Double.doubleToRawLongBits(
                    GrandNetherCaveNetwork.strengthFromColumn(
                            ox,oz,x,y,z));
            require(masked==current,
                    "Column mask changed Nether graph in cell "+gx+","+gz);
        }
    }
    public static void main(String[] args) throws Exception {
        Field threadLocal=field(GrandNetherCaveNetwork.class,"CACHED_HALLS");
        ThreadLocal<?> store=(ThreadLocal<?>)threadLocal.get(null);
        store.remove();
        List<int[]> collisions=new ArrayList<>();
        int target=bucket(-7,11);
        outer:for(int gx=-48;gx<48;gx++)for(int gz=-48;gz<48;gz++)
            if(bucket(gx,gz)==target) {
                collisions.add(new int[]{gx,gz});
                if(collisions.size()==3)break outer;
            }
        require(collisions.size()==3,"Expected three colliding cave regions");
        int[] a=collisions.get(0),b=collisions.get(1),c=collisions.get(2);
        probe(a[0],a[1],0);
        Object beforeA=snapshot(store,a[0],a[1]);
        require(beforeA!=null,"Region A was not retained");
        int epochA=field(beforeA.getClass(),"epoch").getInt(beforeA);
        probe(b[0],b[1],1);
        Object beforeB=snapshot(store,b[0],b[1]);
        require(beforeB!=null && beforeB!=beforeA,
                "Two colliding regions must have separate snapshots");
        int epochB=field(beforeB.getClass(),"epoch").getInt(beforeB);
        for(int pass=0;pass<25;pass++) {
            probe(a[0],a[1],pass&1);
            probe(b[0],b[1],(pass+1)&1);
            require(snapshot(store,a[0],a[1])==beforeA
                    && snapshot(store,b[0],b[1])==beforeB,
                    "Alternating region probes rebuilt an existing graph");
            require(field(beforeA.getClass(),"epoch").getInt(beforeA)==epochA
                    && field(beforeB.getClass(),"epoch").getInt(beforeB)==epochB,
                    "Alternation unexpectedly rebuilt a region's column masks");
        }
        probe(c[0],c[1],1); // Deliberate two-way collision eviction
        require(snapshot(store,c[0],c[1])!=null,
                "Third collision was not stored");
        probe(a[0],a[1],0);probe(b[0],b[1],1);
        // Region changes and worker state may not affect the cave results.
        AtomicReference<Throwable> failure=new AtomicReference<>();
        Thread[] workers=new Thread[4];
        for(int worker=0;worker<workers.length;worker++) {
            final int id=worker;
            workers[worker]=new Thread(()->{
                try {
                    for(int round=0;round<40;round++) {
                        int[] cell=collisions.get((round+id)%collisions.size());
                        probe(cell[0],cell[1],round&1);
                    }
                }catch(Throwable error){failure.compareAndSet(null,error);}
            },"nether-region-reuse-"+worker);
            workers[worker].start();
        }
        for(Thread worker:workers)worker.join();
        if(failure.get()!=null)
            throw new AssertionError("Nether per-worker cache isolation",failure.get());
        require(regions(store).length==4,
                "Nether snapshots grew beyond fixed bound");
        System.out.println("NETHER_FOUR_REGION_GRAPH_REUSE_PASS: "
                +"two-way collisions, A/B/A identity, epoch retention, "
                +"frozen-bit cave parity, forced eviction and four workers");
    }
}
