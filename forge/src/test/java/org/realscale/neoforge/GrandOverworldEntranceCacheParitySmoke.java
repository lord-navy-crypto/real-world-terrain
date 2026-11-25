package org.realscale.neoforge;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Frozen pre-optimization cave entrance formula. Prove the per-worker cell
 * cache changes ONLY execution cost, never cave/shaft geometry or raw bits.
 *
 * This is especially important because 2096-high structure height probes
 * repeatedly evaluate the same 768x768 entrance cell for hundreds of Y's.
 */
public final class GrandOverworldEntranceCacheParitySmoke {
    private GrandOverworldEntranceCacheParitySmoke(){}
    private static void yes(boolean pass,String message) {
        if(!pass)throw new AssertionError(message);
    }
    private static long mix(long value) {
        value^=value>>>30;value*=0xbf58476d1ce4e5b9L;
        value^=value>>>27;value*=0x94d049bb133111ebL;
        return value^(value>>>31);
    }
    private static long oldKey(int gx,int gz) {
        return mix((long)gx*0x9e3779b97f4a7c15L ^
                (long)gz*0xc2b2ae3d27d4eb4fL ^
                0x39ce758a1d4e2b67L);
    }
    private static double uncachedOriginal(double x,double y,double z) {
        if(y<110 || y>GrandOverworldCaveNetwork.MAX_Y)return 0;
        int gx=(int)Math.floor(x/GrandOverworldCaveNetwork.CELL);
        int gz=(int)Math.floor(z/GrandOverworldCaveNetwork.CELL);
        long key=oldKey(gx,gz);
        if(Math.floorMod(key,9L)!=0)return 0;
        int layer=(key&3L)==0 ? 1 : 0;
        GrandOverworldCaveNetwork.Hall anchor=GrandOverworldCaveNetwork.hall(
                gx,gz,layer);
        if(y<anchor.y())return 0;
        double width=11+Math.floorMod(key>>>17,8L);
        if(Math.abs(x-anchor.x())>width+5 || Math.abs(z-anchor.z())>width+4)
            return 0;
        double rise=y-anchor.y();
        double centerX=anchor.x()+Math.sin(rise/92.0)*5;
        double centerZ=anchor.z()+Math.sin(rise/119.0)*4;
        double dx=(x-centerX)/width;
        if(Math.abs(dx)>=1)return 0;
        double dz=(z-centerZ)/width;
        double q=dx*dx+dz*dz;
        return q>=1 ? 0 : 1-q;
    }

    private static void same(double x,double y,double z) {
        long before=Double.doubleToRawLongBits(uncachedOriginal(x,y,z));
        long after=Double.doubleToRawLongBits(
                GrandOverworldCaveNetwork.entranceStrength(x,y,z));
        yes(before==after,"Cave entrance changed at "+x+","+y+","+z
                +" old="+Double.longBitsToDouble(before)
                +" new="+Double.longBitsToDouble(after));
    }
    public static void main(String[] args) throws Exception {
        int n=0,active=0,real=0;
        // Alternate cells intentionally: evictions must never change values.
        for(int gx=-17;gx<=17;gx++)for(int gz=-17;gz<=17;gz++) {
            long key=oldKey(gx,gz);
            boolean enabled=Math.floorMod(key,9L)==0;
            if(enabled)active++;
            int layer=(key&3L)==0?1:0;
            var anchor=GrandOverworldCaveNetwork.hall(gx,gz,layer);
            for(double rise:new double[]{-50,0,1,28,220,830,1700}) {
                double y=anchor.y()+rise;
                double sx=anchor.x()+Math.sin(rise/92.0)*5;
                double sz=anchor.z()+Math.sin(rise/119.0)*4;
                for(double offset:new double[]{-20,-11,-1,0,1,11,20}) {
                    same(sx+offset,y,sz);
                    same(sx,y,sz+offset);
                    n+=2;
                }
                if(enabled && y>=110&&y<=GrandOverworldCaveNetwork.MAX_Y
                        && uncachedOriginal(sx,y,sz)>0)
                    real++;
            }
            // Exercise cell-coordinate change and correct negative floor.
            same(gx*GrandOverworldCaveNetwork.CELL-0.5,250,
                    gz*GrandOverworldCaveNetwork.CELL+0.5);
        }
        yes(active>80 && real>30 && n>100000,
                "Too few real connected cave entrances were checked");

        // Reuse cell + Y column for thousands of visits in each worker.
        AtomicReference<Throwable> error=new AtomicReference<>();
        Thread[] workers=new Thread[4];
        for(int w=0;w<workers.length;w++){
            final int worker=w;
            workers[w]=new Thread(()->{
                try{
                    for(int i=0;i<6000;i++){
                        int gx=(i*29+worker*47)%29-14;
                        int gz=(i*37-worker*31)%31-15;
                        var hall=GrandOverworldCaveNetwork.hall(gx,gz,0);
                        double x=hall.x()+Math.sin(i*0.21)*12;
                        double z=hall.z()+Math.cos(i*0.17)*13;
                        double y=115+(i%1100);
                        same(x,y,z);
                    }
                }catch(Throwable failure){error.compareAndSet(null,failure);}
            },"realscale-entrance-cache-parity-"+w);
            workers[w].start();
        }
        for(Thread worker:workers)worker.join();
        if(error.get()!=null)
            throw new AssertionError("Thread-local entrance cache parity",error.get());

        System.out.println("TVA_ENTRANCE_CACHE_BIT_PARITY_PASS samples="+n
                +" active_sites="+active+" genuine_open_shafts="+real
                +" worker_threads="+workers.length);
    }
}
