package org.realscale.neoforge;

import java.util.Random;

/**
 * Test.24: strict original two-lookups vs single already-computed column
 * mask parity. Repeated cell changes, negative coordinates, near-cell edges,
 * out-of-range Y and unsupported noise warps must never change cave density.
 */
public final class GrandNetherSingleMaskParitySmoke {
    private GrandNetherSingleMaskParitySmoke() {}

    private static long compared,empty,nonempty,edges,invalid,positive;
    private static long raw(double v){return Double.doubleToRawLongBits(v);}
    private static void require(boolean ok,String why){
        if(!ok)throw new AssertionError(why);
    }
    public static void main(String[] args) {
        Random rng=new Random(0x24abce57L);
        final int[] heights={120,143,144,190,205,250,300,348,399,440,441};
        final double[] warps={-1.0,-0.73,-0.25,0.0,0.40,0.83,1.0};

        for(int i=0;i<2800;i++) {
            final int x,z;
            if(i%9==0) {
                // Test the exact cell-crossing fallback on negative
                // and positive boundaries, including the 0 crossing.
                x=(rng.nextInt(41)-20)*GrandNetherCaveNetwork.CELL
                        +(i%2==0?2:GrandNetherCaveNetwork.CELL-3);
                z=rng.nextInt(10001)-5000;
            } else if(i%9==1) {
                x=rng.nextInt(10001)-5000;
                z=(rng.nextInt(41)-20)*GrandNetherCaveNetwork.CELL
                        +(i%2==0?2:GrandNetherCaveNetwork.CELL-3);
            } else if(i%9==2) {
                x=-29_999_000+rng.nextInt(3000);
                z=29_999_000-rng.nextInt(3000);
            } else if(i%9==3) {
                x=29_999_000-rng.nextInt(3000);
                z=-29_999_000+rng.nextInt(3000);
            } else {
                x=rng.nextInt(16001)-8000;
                z=rng.nextInt(16001)-8000;
            }
            int mask=GrandNetherCaveNetwork.componentMaskForColumn(x,z);
            if(mask==0)empty++; else nonempty++;
            if(mask==-1)edges++;
            for(int y:heights)for(double warp:warps) {
                double wx=x+warp*5.0,wy=y+warp*2.0,
                        wz=z-warp*4.0;
                double baseline=GrandNetherCaveNetwork.strengthFromColumn(
                        x,z,wx,wy,wz);
                double optimized=GrandNetherCaveNetwork.strengthFromColumnWithMask(
                        x,z,wx,wy,wz,mask);
                require(raw(baseline)==raw(optimized),
                        "Nether cached-mask density differs at "
                        +x+","+y+","+z+" displacement="+warp+" mask="+mask);
                compared++;
                if(baseline>0)positive++;
            }
            // A deliberately invalid displaced position MUST bypass even
            // a supplied zero mask and evaluate the legacy whole network.
            double otherX=x+41.0,otherZ=z-37.0;
            long reference=raw(GrandNetherCaveNetwork.strength(otherX,205,otherZ));
            long actual=raw(GrandNetherCaveNetwork.strengthFromColumnWithMask(
                    x,z,otherX,205,otherZ,0));
            require(reference==actual,
                    "Unsupported warp wrongly accepted caller's zero mask");
            invalid++;
        }
        // Targeted real cave centers ensure nonzero density coverage even
        // when most random probes lie outside the sparse tunnel network.
        for(int gx=-5;gx<=5;gx++)for(int gz=-5;gz<=5;gz++)
            for(int layer=0;layer<2;layer++) {
                var hall=GrandNetherCaveNetwork.hall(gx,gz,layer);
                int x=(int)hall.x(),z=(int)hall.z();
                int y=(int)hall.y();
                int mask=GrandNetherCaveNetwork.componentMaskForColumn(x,z);
                long baseline=raw(GrandNetherCaveNetwork.strengthFromColumn(
                        x,z,x,y,z));
                long candidate=raw(GrandNetherCaveNetwork.strengthFromColumnWithMask(
                        x,z,x,y,z,mask));
                require(baseline==candidate,
                        "Known Nether hall core differs after single-mask rewrite");
                require(Double.longBitsToDouble(candidate)>0.0,
                        "Known hall core was lost");
                positive++;
                compared++;
            }
        require(compared>200_000 && empty>0 && nonempty>0
                && edges>0 && invalid==2800 && positive>200,
                "Missing positive, empty, boundary or unsupported-warp coverage");
        System.out.println("NETHER_SINGLE_MASK_RAW_BIT_PARITY_PASS compared="
                +compared+" active="+nonempty+" empty="+empty
                +" border="+edges+" positive="+positive+" fallback="+invalid);
    }
}
