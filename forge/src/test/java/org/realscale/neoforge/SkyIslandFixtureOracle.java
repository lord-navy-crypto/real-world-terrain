package org.realscale.neoforge;

/** Prints Java's actual signed-long sky archipelago coordinates for QA parity. */
public final class SkyIslandFixtureOracle {
    public static void main(String[] args) {
        int[][] positions={{0,0},{400,-750},{-301,901},{-1,-1},
                           {4096,-8192},{-10000,-10000},{12345,54321}};
        for (int[] p : positions) {
            SkyIslandLayout.Site site=SkyIslandLayout.at(p[0],p[1]);
            System.out.println("BASE "+p[0]+" "+p[1]+" "+site.x()+" "+site.z()
                               +" "+site.topY()+" "+site.radius());
            for (int slot=0;slot<SkyIslandLayout.SLOTS;slot++) {
                SkyIslandLayout.Site s=SkyIslandLayout.satellite(site,slot);
                System.out.println("SLOT "+p[0]+" "+p[1]+" "+slot+" "
                                   +s.x()+" "+s.z()+" "+s.topY()+" "+s.radius());
            }
        }
    }
}
