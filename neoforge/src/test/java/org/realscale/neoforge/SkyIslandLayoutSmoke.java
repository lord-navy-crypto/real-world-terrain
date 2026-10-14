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
                check(site.x()>Math.floorDiv(x,512)*512 + 127,"x jitter bounds");
                check(site.z()>Math.floorDiv(z,512)*512 + 127,"z jitter bounds");
                check(!SkyIslandLayout.insideBiome(site,site.x(),64,site.z()),"never paint ocean Y64");
                check(SkyIslandLayout.insideBiome(site,site.x(),site.topY(),site.z()),"center biome");
                check(!SkyIslandLayout.insideBiome(site,site.x()+site.radius()+1,
                         site.topY(),site.z()),"radial boundary");
                visited++;
            }
        }
        System.out.println("PASS: "+visited+" deterministic sky island layout samples, ocean Y64 protected");
    }
}
