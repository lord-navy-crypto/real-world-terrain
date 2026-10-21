package org.realscale.neoforge;

import java.util.Random;

/**
 * Checks exact parity of twice-looked-up cave mask vs passing one immutable
 * per-voxel mask from the preliminary void test into the cave evaluator.
 */
public final class GrandOverworldSingleMaskParitySmoke {
    private GrandOverworldSingleMaskParitySmoke() {}
    static long raw(double v){return Double.doubleToRawLongBits(v);}
    static void check(boolean b,String reason){if(!b)throw new AssertionError(reason);}

    public static void main(String[] args) {
        Random random=new Random(20261021L);
        long compared=0, empty=0, nonempty=0, edge=0,invalid=0;
        int[] heights={-50,-40,0,125,395,790,1200,1330,1600,1698,1700,1703,1790};
        for(int i=0;i<7000;i++) {
            int x=i%8==0 ? -29_999_000+random.nextInt(3000)
                    : i%8==1 ? 29_999_000-random.nextInt(3000)
                    : random.nextInt(13000)-6500;
            int z=i%8==2 ? -29_999_000+random.nextInt(3000)
                    : i%8==3 ? 29_999_000-random.nextInt(3000)
                    : random.nextInt(13000)-6500;
            long mask=GrandOverworldCaveNetwork.componentMaskForColumn(x,z);
            if(mask==0L)empty++;else nonempty++;
            if(mask==-1L)edge++;
            // Change world-absolute cells between checks to force all eight
            // region entries / two-way column cache groups to be evicted.
            for(int y:heights)for(double shift:new double[]{-1.0,-0.73,0,0.43,1.0}) {
                double wx=x+shift*6.0, wy=y+shift*2.0,wz=z-shift*5.0;
                long a=raw(GrandOverworldCaveNetwork.strengthFromColumn(
                        x,z,wx,wy,wz));
                long b=raw(GrandOverworldCaveNetwork.strengthFromColumnWithMask(
                        x,z,wx,wy,wz,mask));
                check(a==b,"Known-mask parity failed at "+x+","+y+","+z+
                        ", shift="+shift+" mask="+mask);
                compared++;
            }
            // An unsupported warp MUST fall back to the old evaluator, even
            // if an empty mask has been passed.
            double wx=x+31.0,wz=z-26.0;
            long a=raw(GrandOverworldCaveNetwork.strengthFromColumn(x,z,wx,790,wz));
            long b=raw(GrandOverworldCaveNetwork.strengthFromColumnWithMask(
                    x,z,wx,790,wz,0L));
            check(a==b,"Unsupported warp accepted a stale empty mask");
            invalid++;
        }
        check(compared>350_000 && empty>0 && nonempty>0 && edge>0,
                "Coverage excluded empty or edge columns");
        System.out.println("REALSCALE_CAVE_SINGLE_MASK_BIT_PARITY_PASS cases="+
                compared+" empty="+empty+" nonempty="+nonempty+
                " edges="+edge+" bad_warp_checks="+invalid);
    }
}
