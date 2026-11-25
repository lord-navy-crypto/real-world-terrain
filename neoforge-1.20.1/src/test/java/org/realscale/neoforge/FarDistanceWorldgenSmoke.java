package org.realscale.neoforge;

/**
 * Pure-Java hostile-coordinate regression for RealScale's unusually tall
 * worlds. A chunk near the world border cannot lose caves, mountains, or
 * sky islands merely because a signed region index/cache was reused.
 *
 * This test does not render pixels or prove actual 180s RCON completion:
 * saved-world Anvil and client visual checks remain independent release gates.
 */
public final class FarDistanceWorldgenSmoke {
    private FarDistanceWorldgenSmoke() {}
    private static void check(boolean condition,String reason) {
        if(!condition)throw new AssertionError(reason);
    }
    private static long bits(double value) {
        return Double.doubleToRawLongBits(value);
    }
    public static void main(String[] args) {
        final int[] coords={-29_999_000,-1_000_000,-65_536,-8_192,
                            -1_024,-1,0,1,1_024,8_192,65_536,
                            1_000_000,29_999_000};
        final int[] elevations={145,205,395,790,1330,1660};
        final double[] displacements={-1.0,0.0,1.0};
        MountainReliefSiteCache relief=new MountainReliefSiteCache();
        SkyIslandSiteCache sky=new SkyIslandSiteCache();
        int caveChecks=0,islandChecks=0,reliefChecks=0;
        for (int x:coords) for (int z:coords) {
            int gx=Math.floorDiv(x,MountainReliefMath.CELL);
            int gz=Math.floorDiv(z,MountainReliefMath.CELL);
            var region=relief.get(gx,gz);
            long siteHash=MountainReliefMath.siteHash(gx,gz);
            long exactX=(long)gx*MountainReliefMath.CELL
                    +MountainReliefMath.CELL/2
                    +MountainReliefMath.centerOffsetX(siteHash);
            long exactZ=(long)gz*MountainReliefMath.CELL
                    +MountainReliefMath.CELL/2
                    +MountainReliefMath.centerOffsetZ(siteHash);
            check(region.hash==siteHash
                    && region.centerX==exactX&&region.centerZ==exactZ,
                    "Mountain relief region wrapping at "+x+","+z);
            check(region.definitelyOutside(x-region.centerX,z-region.centerZ)
                    ==MountainReliefMath.definitelyOutsideEnvelope(
                        x-region.centerX,z-region.centerZ,region.radius),
                    "Mountain relief envelope discontinuity at "+x+","+z);
            reliefChecks++;
            var skySite=sky.at(x,z);
            var canonical=SkyIslandLayout.at(x,z);
            check(skySite.base().equals(canonical),
                    "Sky site cache alias at "+x+","+z);
            for(int slot=0;slot<SkyIslandLayout.SLOTS;slot++){
                var child=skySite.satellite(slot);
                check(child.equals(SkyIslandLayout.satellite(canonical,slot)),
                        "Sky satellite lost at "+x+","+z);
                if(SkyIslandLayout.insideBiome(child,x,child.topY(),z))
                    check(SkyIslandLayout.mayIntersectIslands(canonical,x,z),
                            "Sky broad-phase hides distant island "+x+","+z);
                islandChecks++;
            }
            // Check actual optimal mask-vs-full 3D cave graphs with the
            // full permitted noise warp at signed/remote chunk coordinates.
            for (int y:elevations) for(double shift:displacements) {
                double ox=x+shift*6.0, oy=y+shift*2.0, oz=z-shift*5.0;
                check(bits(GrandOverworldCaveNetwork.strength(ox,oy,oz))
                    ==bits(GrandOverworldCaveNetwork.strengthFromColumn(
                        x,z,ox,oy,oz)),
                        "Distant Overworld cave mask discontinuity at "
                            +x+","+y+","+z+" shift="+shift);
                double nx=x+shift*5.0,ny=y+shift*2.0,nz=z-shift*4.0;
                check(bits(GrandNetherCaveNetwork.strength(nx,ny,nz))
                    ==bits(GrandNetherCaveNetwork.strengthFromColumn(
                        x,z,nx,ny,nz)),
                        "Distant Nether cave mask discontinuity at "
                            +x+","+y+","+z+" shift="+shift);
                caveChecks++;
            }
        }
        check(caveChecks>2000 && islandChecks>500 && reliefChecks>100,
                "Insufficient remote negative/positive region samples");
        System.out.println("TVA_FAR_DISTANCE_WORLDGEN_PASS cave_parity="+caveChecks
                +" sky_parity="+islandChecks+" mountain_regions="+reliefChecks);
    }
}
