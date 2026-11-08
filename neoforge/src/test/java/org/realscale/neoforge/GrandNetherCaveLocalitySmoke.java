package org.realscale.neoforge;

import java.util.ArrayList;
import java.util.List;

/**
 * Cache-locality correctness test. Source geometry is compared against the
 * immutable pre-optimization oracle for every warmed and alternating query.
 * This executable is pure Java and does not start a Minecraft world.
 */
public final class GrandNetherCaveLocalitySmoke {
    private GrandNetherCaveLocalitySmoke() {}

    private static void yes(boolean ok,String message) {
        if(!ok)throw new AssertionError(message);
    }
    private static void parity(double x,double y,double z) {
        long expected=Double.doubleToRawLongBits(
                GrandNetherCaveNetworkReference.strength(x,y,z));
        long actual=Double.doubleToRawLongBits(
                GrandNetherCaveNetwork.strength(x,y,z));
        yes(expected==actual,"Nether cave changed at "+x+","+y+","+z);
    }
    public static void main(String[] args) {
        // No eager region initialization and no shared worker state.
        long[] before=GrandNetherCaveNetwork.cacheLocalityStats();
        final int[][] regions={{-9,-7},{2,3},{-1,4},{11,-6}};
        final List<double[]> probes=new ArrayList<>();
        for(int[] r:regions) {
            for(int layer=0;layer<2;layer++) {
                var hall=GrandNetherCaveNetwork.hall(r[0],r[1],layer);
                probes.add(new double[]{hall.x(),hall.y(),hall.z()});
                probes.add(new double[]{hall.x()+17.25,hall.y()-7.5,hall.z()-12.25});
            }
        }
        for(double[] q:probes)parity(q[0],q[1],q[2]);
        long[] afterInitial=GrandNetherCaveNetwork.cacheLocalityStats();
        yes(afterInitial[0]>before[0],"Region geometry was never loaded");
        // Alternating the same few regions must not allocate or regenerate
        // immutable regional halls and corridor keys on every revisit.
        for(int repeat=0;repeat<10;repeat++)
            for(double[] q:probes)
                parity(q[0],q[1],q[2]);
        long[] afterRepeats=GrandNetherCaveNetwork.cacheLocalityStats();
        yes(afterRepeats[0]-afterInitial[0]<probes.size()*3L,
                "Region cache did not retain locality");

        // Two distinct interior columns sharing a 256-bucket table hash
        // must each retain their conservative mask through A,B,A.
        int ax=0,az=0,bx=0,bz=0,used=0;
        int[] xx=new int[256],zz=new int[256];
        boolean[] filled=new boolean[256];
        outer: for(int x=32;x<240;x++)for(int z=32;z<240;z++) {
            int hash=x*0x9e3779b9 ^ z*0x85ebca6b;
            int slot=(hash^(hash>>>16))&255;
            if(filled[slot] && (xx[slot]!=x || zz[slot]!=z)) {
                ax=xx[slot];az=zz[slot];bx=x;bz=z;used++;
                break outer;
            }
            filled[slot]=true;xx[slot]=x;zz[slot]=z;
        }
        yes(used==1,"Could not find colliding interior Nether columns");
        long[] start=GrandNetherCaveNetwork.cacheLocalityStats();
        GrandNetherCaveNetwork.mayContainVoidNearColumn(ax,az);
        GrandNetherCaveNetwork.mayContainVoidNearColumn(bx,bz);
        long[] warm=GrandNetherCaveNetwork.cacheLocalityStats();
        for(int k=0;k<64;k++) {
            GrandNetherCaveNetwork.mayContainVoidNearColumn(ax,az);
            GrandNetherCaveNetwork.mayContainVoidNearColumn(bx,bz);
        }
        long[] retained=GrandNetherCaveNetwork.cacheLocalityStats();
        yes(retained[1]==warm[1],
                "Two-way Nether column masks repeatedly evicted one another");
        yes(warm[1]>=start[1]+1,
                "Colliding Nether masks were never calculated");

        // Negative coordinates and seeded +/-5 X, +/-4 Z displacements
        // must preserve the original geometry and masked evaluator.
        for(int gx=-3;gx<=3;gx++)for(int gz=-3;gz<=3;gz++)
            for(int layer=0;layer<2;layer++) {
                var h=GrandNetherCaveNetwork.hall(gx,gz,layer);
                for(double dy:new double[]{-20.5,-0.25,0,4.75,21.25}) {
                    double x=h.x()+2.125,z=h.z()-3.375,y=h.y()+dy;
                    parity(x,y,z);
                    int sx=(int)h.x(),sz=(int)h.z();
                    double wx=sx+2.125,wz=sz-3.375;
                    long original=Double.doubleToRawLongBits(
                            GrandNetherCaveNetwork.strength(wx,y,wz));
                    long optimized=Double.doubleToRawLongBits(
                            GrandNetherCaveNetwork.strengthFromColumn(
                                    sx,sz,wx,y,wz));
                    yes(original==optimized,
                            "Nether masked cave density changed");
                }
            }
        System.out.println("REALSCALE_GRAND_NETHER_LOCALITY_PASS");
    }
}
