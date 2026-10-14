package org.realscale.neoforge;

/** Pure deterministic island placement and below-surface safety checks. */
public final class SkyIslandLayoutSmoke {
    private static void check(boolean pass, String name) {
        if (!pass) throw new AssertionError(name);
    }
    public static void main(String[] args) {
        int visited = 0;
        for (int z=-3072;z<=3072;z+=127) {
            for(int x=-3072;x<=3072;x+=113) {
                var site = SkyIslandLayout.at(x,z);
                check(site.equals(SkyIslandLayout.at(x,z)),"site determinism");
                check(site.radius()>=10&&site.radius()<=14,"radius");
                check(site.topY()>=183&&site.topY()<=203,"height");
                long gx = Math.floorDiv((long)x + 256, 512);
                long gz = Math.floorDiv((long)z + 256, 512);
                check(site.x() >= gx*512-128 && site.x() <= gx*512+127,"x jitter bounds");
                check(site.z() >= gz*512-128 && site.z() <= gz*512+127,"z jitter bounds");
                check(!SkyIslandLayout.insideBiome(site,site.x(),64,site.z()),"never paint ocean Y64");
                check(SkyIslandLayout.insideBiome(site,site.x(),site.topY(),site.z()),"center biome");
                check(!SkyIslandLayout.insideBiome(site,site.x()+site.radius()+1,
                         site.topY(),site.z()),"radial boundary");
                visited++;
            }
        }
        var s = SkyIslandLayout.at(0, 0);
        check(s.x() == 32 && s.z() == 63, "origin-grid deterministic test site");
        check(SkyIslandLayout.remoteDeepOcean(s, (x,z)->true, (x,z)->true),"open ocean qualifies");
        check(!SkyIslandLayout.remoteDeepOcean(s, (x,z)->false, (x,z)->true),
                "shallow open ocean cannot qualify");
        check(!SkyIslandLayout.remoteDeepOcean(s, (x,z)->true, (x,z)->x!=s.x()+384),
                "sampled coastline blocks island");
        int wedgeX = (int)Math.round(384 * Math.cos(Math.PI / 8));
        int wedgeZ = (int)Math.round(384 * Math.sin(Math.PI / 8));
        check(!SkyIslandLayout.remoteDeepOcean(s, (x,z)->true,
                    (x,z)->x != s.x()+wedgeX || z != s.z()+wedgeZ),
                "coastline between old compass rays must veto island");
        System.out.println("PASS: "+visited+" deterministic sky island layout samples, ocean Y64 protected");
    }
}
