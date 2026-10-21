package org.realscale.neoforge;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Real regional geometry and seed-classifier cache parity for alternating
 * structure-start height queries, negative cell edges, evictions and workers.
 */
public final class MountainReliefSiteCacheSmoke {
    private MountainReliefSiteCacheSmoke(){}
    private static void check(boolean ok,String what){
        if(!ok)throw new AssertionError(what);
    }
    private static void checkEquivalent(MountainReliefSiteCache.Site site) {
        int gx=site.gx,gz=site.gz;
        long hash=MountainReliefMath.siteHash(gx,gz);
        long x=(long)gx*MountainReliefMath.CELL+MountainReliefMath.CELL/2
                +MountainReliefMath.centerOffsetX(hash);
        long z=(long)gz*MountainReliefMath.CELL+MountainReliefMath.CELL/2
                +MountainReliefMath.centerOffsetZ(hash);
        check(site.hash==hash,"site hash differs");
        check(site.centerX==Math.toIntExact(x)&&site.centerZ==Math.toIntExact(z),
                "site center differs");
        check(Double.doubleToRawLongBits(site.radius)==
                Double.doubleToRawLongBits(MountainReliefMath.radius(hash)),
                "site radius differs");
        for(double dx:new double[]{-1600,-1100,-850,-730,-580,-1,0,1,560,730,850,1100,1600})
            for(double dz:new double[]{-1300,-800,-500,-1,0,1,500,800,1300}) {
                check(site.definitelyOutside(dx,dz)==
                        MountainReliefMath.definitelyOutsideEnvelope(
                                dx,dz,site.radius),
                        "Site envelope differs from original at "+dx+","+dz);
            }
    }
    public static void main(String[] args)throws Exception{
        MountainReliefSiteCache cache=new MountainReliefSiteCache();
        int checked=0;
        for(int gx=-2048;gx<=2048;gx+=43)
            for(int gz=-2048;gz<=2048;gz+=47) {
                var site=cache.get(gx,gz);
                checkEquivalent(site);
                check(cache.get(gx,gz)==site,"last region not reused");
                checked++;
            }
        // Preserve the original site geometry at 2048-block boundaries
        // and across negative-to-positive world coordinates.
        for(int x=-6147;x<=6150;x+=3)
            for(int z=-4103;z<=4102;z+=7) {
                int gx=Math.floorDiv(x,MountainReliefMath.CELL);
                int gz=Math.floorDiv(z,MountainReliefMath.CELL);
                var site=cache.get(gx,gz);
                check(site.gx==gx&&site.gz==gz,
                        "incorrect cached region on signed boundary");
            }
        // Alternating between distinct regions must preserve classification.
        // That is the specific performance weakness of the old last-site-only cache.
        int[][] regions={{0,0},{1,0},{0,1},{-1,0},{0,-1},{-1,-1},{2,-1},{1,-2}};
        var entries=new MountainReliefSiteCache.Site[regions.length];
        for(int i=0;i<regions.length;i++) {
            entries[i]=cache.get(regions[i][0],regions[i][1]);
            entries[i].classified=true;
            entries[i].type=MountainReliefMath.MESA;
            entries[i].anchorHeight=100+i;
        }
        for(int cycle=0;cycle<400;cycle++)
            for(int i=0;i<regions.length;i++) {
                var site=cache.get(regions[i][0],regions[i][1]);
                check(site==entries[i]&&site.classified
                                &&site.type==MountainReliefMath.MESA
                                &&site.anchorHeight==100+i,
                        "Alternating structure probes lost seeded classification");
            }
        // Exercise two keys that deliberately share the same two-way bucket.
        // They must retain their classified noise result and object identity
        // across thousands of alternating 2096-high structure queries.
        int[][] colliding=new int[3][2];
        int found=0;
        int target=(-71*0x9e3779b9 ^ 29*0x85ebca6b)&31;
        outer:for(int gx=-256;gx<256;gx++)
            for(int gz=-256;gz<256;gz++)
                if(((gx*0x9e3779b9 ^ gz*0x85ebca6b)&31)==target) {
                    colliding[found++]=new int[]{gx,gz};
                    if(found==3)break outer;
                }
        check(found==3,"cannot find three colliding relief sites");
        MountainReliefSiteCache collisionCache=new MountainReliefSiteCache();
        var alpha=collisionCache.get(colliding[0][0],colliding[0][1]);
        alpha.classified=true;
        alpha.type=MountainReliefMath.BASIN;
        alpha.anchorHeight=12.5;
        var beta=collisionCache.get(colliding[1][0],colliding[1][1]);
        beta.classified=true;
        beta.type=MountainReliefMath.MESA;
        beta.anchorHeight=95.25;
        for(int i=0;i<4000;i++) {
            check(collisionCache.get(colliding[0][0],colliding[0][1])==alpha
                    && alpha.classified && alpha.type==MountainReliefMath.BASIN
                    && alpha.anchorHeight==12.5,
                    "A/B alternation lost first seeded relief classification");
            check(collisionCache.get(colliding[1][0],colliding[1][1])==beta
                    && beta.classified && beta.type==MountainReliefMath.MESA
                    && beta.anchorHeight==95.25,
                    "A/B alternation lost second seeded relief classification");
        }
        checkEquivalent(collisionCache.get(colliding[2][0],colliding[2][1]));
        // Eviction must never return the other site's geometry or classification.
        for(int i=0;i<90;i++)for(int[] pair:colliding) {
            var v=collisionCache.get(pair[0],pair[1]);
            check(v.gx==pair[0] && v.gz==pair[1],
                    "Hash collision incorrectly reused another mountain site");
            checkEquivalent(v);
        }
        // Forced collision and eviction affect only cost, never results.
        for(int gx=-300;gx<=300;gx++)
            for(int gz=-20;gz<=20;gz++)checkEquivalent(cache.get(gx,gz));
        for(var region:regions)checkEquivalent(cache.get(region[0],region[1]));

        final AtomicReference<Throwable> error=new AtomicReference<>();
        Thread[] workers=new Thread[4];
        for(int w=0;w<workers.length;w++){
            final int worker=w;
            workers[w]=new Thread(()->{
                try{
                    MountainReliefSiteCache local=new MountainReliefSiteCache();
                    for(int i=0;i<20000;i++){
                        int gx=(i*31+worker*29)%211-105;
                        int gz=(i*43-worker*17)%197-98;
                        checkEquivalent(local.get(gx,gz));
                    }
                }catch(Throwable t){error.compareAndSet(null,t);}
            },"mountain-site-worker-"+w);
            workers[w].start();
        }
        for(Thread worker:workers)worker.join();
        if(error.get()!=null)
            throw new AssertionError("Concurrent relief site cache",error.get());
        System.out.println("TVA_MOUNTAIN_SITE_CACHE_PASS samples="+checked
                +" worker_count="+workers.length);
    }
}
