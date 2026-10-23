package org.realscale.neoforge;

import java.util.Arrays;
import java.util.Random;

/** Trace-exact boxed versus primitive ocean isolation-ring classifier. */
public final class SkyOceanPrimitiveTierSmoke {
    private static final class Trace {
        final long[] coordinates=new long[130];
        final boolean[] deepRead=new boolean[130];
        final int stopAt;
        final boolean deepAllowed;
        int reads, oceans;

        Trace(int stopAt,boolean deepAllowed) {
            this.stopAt=stopAt;
            this.deepAllowed=deepAllowed;
        }
        private void record(int x,int z,boolean isDeep) {
            if(reads==coordinates.length)throw new AssertionError("Unexpected extra ocean probe");
            coordinates[reads]=((long)x<<32) | (z&0xffffffffL);
            deepRead[reads]=isDeep;
            reads++;
        }
        boolean deep(int x,int z) {
            record(x,z,true);
            return deepAllowed;
        }
        boolean ocean(int x,int z) {
            record(x,z,false);
            return stopAt != oceans++;
        }
    }

    private static int compared;
    private static void verify(SkyIslandLayout.Site site,int stopAt,boolean deepAllowed) {
        Trace original=new Trace(stopAt,deepAllowed);
        Trace optimized=new Trace(stopAt,deepAllowed);
        int prior=SkyIslandLayout.oceanTier(site,original::deep,original::ocean);
        int next=SkyIslandLayout.oceanTierPrimitive(site,
                optimized::deep,optimized::ocean);
        if(prior!=next || original.reads!=optimized.reads
                || original.oceans!=optimized.oceans
                || !Arrays.equals(original.coordinates,0,original.reads,
                        optimized.coordinates,0,optimized.reads)
                || !Arrays.equals(original.deepRead,0,original.reads,
                        optimized.deepRead,0,optimized.reads)) {
            throw new AssertionError("Ocean isolation changed at "+site
                    +" cutoff="+stopAt+" deep="+deepAllowed);
        }
        if (!deepAllowed && (prior!=0 || original.reads!=1))
            throw new AssertionError("Shallow base must not read rings");
        if (deepAllowed && stopAt<0 && (prior!=3 || original.reads!=129))
            throw new AssertionError("Open ocean must read all 128 ring positions");
        compared++;
    }

    public static void main(String[] args) {
        int[] extremes={Integer.MIN_VALUE,Integer.MIN_VALUE+32,
                -30_000_000,-1024,-1,0,1,1024,30_000_000,
                Integer.MAX_VALUE-32,Integer.MAX_VALUE};
        for(int x:extremes)for(int z:extremes) {
            var site=new SkyIslandLayout.Site(x,z,196,12);
            verify(site,-1,true);
            verify(site,0,true);
            verify(site,39,true);
            verify(site,79,true);
            verify(site,127,true);
            verify(site,-1,false);
        }
        // Test every possible first-failed ray, including 16/32/64/96
        // transition boundaries, in both negative and positive regions.
        for(int cutoff=-1;cutoff<128;cutoff++) {
            verify(SkyIslandLayout.at(0,0),cutoff,true);
            verify(SkyIslandLayout.at(-4321,9876),cutoff,true);
        }
        Random rng=new Random(0x17a0cea52L);
        for(int i=0;i<18000;i++) {
            var site=SkyIslandLayout.at(rng.nextInt(),rng.nextInt());
            int cutoff=rng.nextInt(130)-1;
            verify(site,cutoff<128?cutoff:-1,rng.nextInt(15)!=0);
        }
        System.out.println("SKY_OCEAN_PRIMITIVE_TIER_PARITY_PASS runs="+compared);
    }
}
