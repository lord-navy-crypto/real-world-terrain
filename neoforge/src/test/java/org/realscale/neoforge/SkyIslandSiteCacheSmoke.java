package org.realscale.neoforge;

import java.util.concurrent.atomic.AtomicReference;

/** Pure Java 21 parity tests for site lookup, satellite order and tier reset. */
public final class SkyIslandSiteCacheSmoke {
    private SkyIslandSiteCacheSmoke() {}

    private static void check(boolean ok,String why) {
        if(!ok)throw new AssertionError(why);
    }

    public static void main(String[] args) throws Exception {
        SkyIslandSiteCache cache=new SkyIslandSiteCache();
        long checked=0;
        for(int x=-4096;x<=4096;x+=29)
            for(int z=-4096;z<=4096;z+=31) {
                var entry=cache.at(x,z);
                var original=SkyIslandLayout.at(x,z);
                check(entry.base().equals(original),"Base changed at "+x+","+z);
                for(int slot=0;slot<SkyIslandLayout.SLOTS;slot++) {
                    var satellite=entry.satellite(slot);
                    check(satellite.equals(SkyIslandLayout.satellite(original,slot)),
                            "Satellite changed at "+x+","+z+" slot "+slot);
                    check(entry.satellite(slot)==satellite,
                            "Same worker should reuse candidate site object");
                    check(satellite.topY()-13>=SkyIslandLayout.MIN_BIOME_Y
                            && satellite.topY()+8<=SkyIslandLayout.MAX_BIOME_Y,
                            "Sky biome height gate would hide an existing island");
                    for(int y:new int[]{160,164,216,220,224}) {
                        check(!SkyIslandLayout.insideBiome(satellite,
                                satellite.x(),y,satellite.z()),
                                "Sky biome early-out changed vertical geometry");
                    }
                }
                check(cache.at(x,z)==entry,"Last accessed cell must hit cache");
                checked++;
            }

        // Exhaustively exercise both negative and positive lattice boundaries.
        for(int gx=-10;gx<=10;gx++)
            for(int gz=-10;gz<=10;gz++) {
                int cx=gx*SkyIslandLayout.GRID-SkyIslandLayout.GRID/2;
                int cz=gz*SkyIslandLayout.GRID-SkyIslandLayout.GRID/2;
                for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++) {
                    int x=cx+dx,z=cz+dz;
                    var entry=cache.at(x,z);
                    check(entry.base().equals(SkyIslandLayout.at(x,z)),
                            "Cell transition changed base site near boundary");
                }
            }

        // Forced eviction cannot return a stale site after slot reuse.
        for(int i=0;i<18_000;i++) {
            int x=(i*113)-1_000_000;
            int z=(i*179)-1_800_000;
            var entry=cache.at(x,z);
            check(entry.base().equals(SkyIslandLayout.at(x,z)),
                    "Cache collision reused a different lattice entry");
        }

        // The ocean tier may be reused ONLY while the Climate.Sampler is the
        // same. SkyOceanBiomeSource.prepare() calls clear() on sampler change.
        int x=83,z=-997;
        var old=cache.at(x,z);
        old.tier=3;
        check(cache.at(x,z).tier==3,"Tier memo lost within same sampler");
        cache.clear();
        var fresh=cache.at(x,z);
        check(fresh.tier==-1,"Ocean tier leaked across sampler identities");
        check(fresh.base().equals(old.base()),"Sampler reset altered geometry");

        // The site cache is owned by a worldgen thread, never shared globally.
        AtomicReference<Throwable> failure=new AtomicReference<>();
        Thread[] workers=new Thread[4];
        for(int w=0;w<workers.length;w++) {
            final int id=w;
            workers[w]=new Thread(()->{
                try {
                    SkyIslandSiteCache local=new SkyIslandSiteCache();
                    for(int i=0;i<35_000;i++) {
                        int sx=(i*79+id*97)%10000-5000;
                        int sz=(i*43-id*151)%12000-6000;
                        var entry=local.at(sx,sz);
                        check(entry.base().equals(SkyIslandLayout.at(sx,sz)),
                                "Worker site mismatch");
                        if(i%199==0)local.clear();
                    }
                }catch(Throwable ex){failure.compareAndSet(null,ex);}
            },"sky-site-cache-worker-"+w);
            workers[w].start();
        }
        for(Thread worker:workers)worker.join();
        if(failure.get()!=null)throw new AssertionError("Parallel site cache",failure.get());
        System.out.println("PASS: "+checked+" sky-site lattice samples, boundaries, "+
                "collisions, tier reset and worker isolation");
    }
}
