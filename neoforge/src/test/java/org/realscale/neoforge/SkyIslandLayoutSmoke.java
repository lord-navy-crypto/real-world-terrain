package org.realscale.neoforge;

/** Pure deterministic island placement and below-surface safety checks. */
public final class SkyIslandLayoutSmoke {
    private static void check(boolean pass, String name) {
        if (!pass) throw new AssertionError(name);
    }
    private static long distSq(SkyIslandLayout.Site center,int x,int z) {
        long dx=(long)x-center.x(),dz=(long)z-center.z();
        return dx*dx+dz*dz;
    }
    public static void main(String[] args) {
        int visited = 0;
        for (int z=-3072;z<=3072;z+=127) {
            for(int x=-3072;x<=3072;x+=113) {
                var site = SkyIslandLayout.at(x,z);
                check(site.equals(SkyIslandLayout.at(x,z)),"site determinism");
                check(site.radius()>=10&&site.radius()<=14,"radius");
                check(site.topY()>=183&&site.topY()<=203,"height");
                long gx = Math.floorDiv((long)x + SkyIslandLayout.GRID/2, SkyIslandLayout.GRID);
                long gz = Math.floorDiv((long)z + SkyIslandLayout.GRID/2, SkyIslandLayout.GRID);
                check(site.x() >= gx*SkyIslandLayout.GRID-48 && site.x() <= gx*SkyIslandLayout.GRID+47,"x jitter bounds");
                check(site.z() >= gz*SkyIslandLayout.GRID-48 && site.z() <= gz*SkyIslandLayout.GRID+47,"z jitter bounds");
                check(!SkyIslandLayout.insideBiome(site,site.x(),64,site.z()),"never paint ocean Y64");
                check(SkyIslandLayout.insideBiome(site,site.x(),site.topY(),site.z()),"center biome");
                check(!SkyIslandLayout.insideBiome(site,site.x()+site.radius()+1,
                         site.topY(),site.z()),"radial boundary");
                visited++;
            }
        }
        var s = SkyIslandLayout.at(0, 0);
        check(Math.abs(s.x())<=48 && Math.abs(s.z())<=48, "origin-grid jitter bounds");
        check(SkyIslandLayout.remoteDeepOcean(s, (x,z)->true, (x,z)->true),"open ocean qualifies");
        check(!SkyIslandLayout.remoteDeepOcean(s, (x,z)->false, (x,z)->true),
                "shallow open ocean cannot qualify");
        check(!SkyIslandLayout.remoteDeepOcean(s, (x,z)->true, (x,z)->x!=s.x()+256),
                "sampled coastline blocks island");
        int wedgeX = (int)Math.round(256 * Math.cos(Math.PI / 8));
        int wedgeZ = (int)Math.round(256 * Math.sin(Math.PI / 8));
        check(!SkyIslandLayout.remoteDeepOcean(s, (x,z)->true,
                    (x,z)->x != s.x()+wedgeX || z != s.z()+wedgeZ),
                "coastline between old compass rays must veto island");
        // A coast farther away creates a smooth *stepwise* increase in island
        // count: sparse fringe (1), distant ocean (3), ocean core (5).
        for (int cut=300;cut<=1000;cut+=100) {
            final int limit=cut;
            int tier=SkyIslandLayout.oceanTier(s, (x,z)->true,
                (x,z)->{
                    long dx=(long)x-s.x(),dz=(long)z-s.z();
                    return dx*dx+dz*dz < (long)limit*limit;
                });
            check(tier>=0&&tier<=3,"isolation tier bounded");
        }
        check(SkyIslandLayout.oceanTier(s,(x,z)->true,
            (x,z)->distSq(s,x,z)<220L*220)==0,"near coast rejects island");
        check(SkyIslandLayout.oceanTier(s,(x,z)->true,
            (x,z)->distSq(s,x,z)<320L*320)==1,"outer shelf yields single sky island");
        check(SkyIslandLayout.oceanTier(s,(x,z)->true,
            (x,z)->distSq(s,x,z)<520L*520)==2,"middle ocean yields three islands");
        check(SkyIslandLayout.oceanTier(s,(x,z)->true,
            (x,z)->distSq(s,x,z)<800L*800)==3,"remote ocean yields five islands");
        check(SkyIslandLayout.countForTier(0)==0&&SkyIslandLayout.countForTier(1)==1
            &&SkyIslandLayout.countForTier(2)==3&&SkyIslandLayout.countForTier(3)==5,
            "archipelago density strictly increases away from coast");
        for (int slot=0;slot<SkyIslandLayout.SLOTS;slot++) {
            var island=SkyIslandLayout.satellite(s,slot);
            check(island.equals(SkyIslandLayout.satellite(s,slot)),"satellite deterministic");
            check(SkyIslandLayout.at(island.x(), island.z()).equals(s),
                "satellite home chunk must resolve its parent lattice site");
            check(island.radius()>=7&&island.radius()<=14,"satellite radius");
            check(island.topY()>=180&&island.topY()<=206,"satellite vertical envelope");
            check(SkyIslandLayout.insideBiome(island,island.x(),island.topY(),island.z()),
                "satellite center must own SKY biome");
            check(!SkyIslandLayout.insideBiome(island,island.x(),62,island.z()),
                "satellites cannot change vanilla deep sea biome");
            for (int other=0;other<slot;other++) {
                var prev=SkyIslandLayout.satellite(s,other);
                long dx=(long)prev.x()-island.x(),dz=(long)prev.z()-island.z();
                check(dx*dx+dz*dz>=(long)(prev.radius()+island.radius()+8)
                    *(prev.radius()+island.radius()+8),"cluster islands cannot intersect");
            }
        }
        System.out.println("PASS: "+visited+" deterministic sky island layout samples, ocean Y64 protected");
    }
}
