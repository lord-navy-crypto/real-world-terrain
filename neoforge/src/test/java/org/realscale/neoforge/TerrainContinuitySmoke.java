package org.realscale.neoforge;

/** Deterministic DEM interpolation/weight geometry checks. No world mutation. */
public final class TerrainContinuitySmoke {
    private TerrainContinuitySmoke() {}
    private static void require(boolean b, String label) {
        if (!b) throw new AssertionError(label);
    }
    public static void main(String[] args) {
        RealDemTile tile = RealDemTile.load();
        int minX=tile.originX(), minZ=tile.originZ(), maxX=tile.maxX(), maxZ=tile.maxZ();
        double largestSlope=0, largestWeightJump=0;
        long samples=0;
        // Sample ALL adjacent block positions inside the measured tile.
        for (int z=minZ;z<=maxZ;z++) for (int x=minX;x<=maxX;x++) {
            double h=tile.worldSolidYAt(x,z);
            require(Double.isFinite(h) && h>=-64 && h<=2031,"elevation outside bounds");
            if (x<maxX) {
                double next=tile.worldSolidYAt(x+1,z);
                largestSlope=Math.max(largestSlope,Math.abs(next-h));
            }
            if (z<maxZ) {
                double next=tile.worldSolidYAt(x,z+1);
                largestSlope=Math.max(largestSlope,Math.abs(next-h));
            }
            double w=tile.influence(x,z,96);
            require(Double.isFinite(w)&&w>=0&&w<=1,"blend weight invalid");
            if(x<maxX) largestWeightJump=Math.max(largestWeightJump,
                    Math.abs(tile.influence(x+1,z,96)-w));
            if(z<maxZ) largestWeightJump=Math.max(largestWeightJump,
                    Math.abs(tile.influence(x,z+1,96)-w));
            samples++;
        }
        require(tile.influence(minX,minZ,96)==0,"corner edge mismatch");
        require(tile.influence(maxX,maxZ,96)==0,"far corner edge mismatch");
        require(tile.influence(minX-1,minZ,96)==0,"outside tile mismatch");
        require(tile.influence(maxX+1,maxZ,96)==0,"outside tile mismatch");
        // A 96-block smoothstep has theoretical maximum derivative 1.5/96.
        require(largestWeightJump<=1.5/96+0.0001,"abrupt blend weight boundary");
        // Verify exact shared grid node continuity on both sides.
        for(int x=minX+32;x<maxX;x+=32) for(int z=minZ+1;z<maxZ;z+=13) {
            double left=tile.worldSolidYAt(x-0.0001,z);
            double right=tile.worldSolidYAt(x+0.0001,z);
            require(Math.abs(left-right)<0.1,"DEM grid x seam "+x+","+z);
        }
        for(int z=minZ+32;z<maxZ;z+=32) for(int x=minX+1;x<maxX;x+=13) {
            double a=tile.worldSolidYAt(x,z-0.0001);
            double b=tile.worldSolidYAt(x,z+0.0001);
            require(Math.abs(a-b)<0.1,"DEM grid z seam "+x+","+z);
        }
        System.out.printf("PASS: %d DEM adjacent-column samples; max elevation delta=%.4f blocks, max blend delta=%.6f%n",
                samples,largestSlope,largestWeightJump);
        // These are measured statistics, not guarantees about full generated chunks.
        if(largestSlope>20) System.out.println("WARN: high source DEM local slope; inspect terrain visually.");
    }
}
