package org.realscale.neoforge;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Frozen original BiPredicate<Integer,Integer> versus Test.17's primitive
 * ocean classifier. Every climate coordinate AND early-exit order must match.
 * This is math/predicate parity, not a Minecraft chunks-per-second benchmark.
 */
public final class SkyOceanPrimitiveTierSmoke {
    private static int cases;
    private static long climatePoints;
    private static long pack(int x,int z) {
        return ((long)x<<32)|(z&0xffffffffL);
    }
    private static void require(boolean value,String message) {
        if(!value)throw new AssertionError(message);
    }

    private static void compare(SkyIslandLayout.Site site,boolean deepOcean,
                                int firstFailedOceanProbe) {
        List<Long> original=new ArrayList<>();
        List<Long> optimized=new ArrayList<>();
        List<Long> deepOriginal=new ArrayList<>();
        List<Long> deepOptimized=new ArrayList<>();
        int[] oldIndex={0},newIndex={0};
        int before=SkyIslandLayout.oceanTier(site,
                (x,z)->{
                    deepOriginal.add(pack(x,z));
                    return deepOcean;
                },
                (x,z)->{
                    original.add(pack(x,z));
                    return ++oldIndex[0]!=firstFailedOceanProbe;
                });
        int after=SkyIslandLayout.oceanTierUnboxed(site,
                (x,z)->{
                    deepOptimized.add(pack(x,z));
                    return deepOcean;
                },
                (x,z)->{
                    optimized.add(pack(x,z));
                    return ++newIndex[0]!=firstFailedOceanProbe;
                });
        require(before==after,"Ocean tier mismatch: "+site+" fail="+firstFailedOceanProbe);
        require(deepOriginal.equals(deepOptimized),
                "Deep-ocean gating changed: "+site);
        require(original.equals(optimized),
                "Ocean climate coordinates/order changed: "+site+" fail="+firstFailedOceanProbe);
        require(oldIndex[0]==newIndex[0],"Early exit changed");
        if(!deepOcean) {
            require(before==0 && original.isEmpty(),"Shallow sea queried remote rings");
        } else if(firstFailedOceanProbe>128) {
            require(before==3&&oldIndex[0]==128,"Complete ocean rings missing");
        }
        climatePoints+=oldIndex[0];
        cases++;
    }

    public static void main(String[] args) {
        int[] coords={
                0,1,-1,47,-48,319,-320,1024,-1024,
                30_000_000,-30_000_000,Integer.MAX_VALUE,Integer.MIN_VALUE,
                Integer.MAX_VALUE-79,Integer.MIN_VALUE+79
        };
        for(int x:coords)for(int z:coords) {
            SkyIslandLayout.Site site=SkyIslandLayout.at(x,z);
            compare(site,false,129);
            for(int fail:new int[]{1,2,15,16,17,32,63,64,65,96,97,112,127,128,129})
                compare(site,true,fail);
        }
        Random random=new Random(0x170c3a4eL);
        for(int n=0;n<48;n++) {
            SkyIslandLayout.Site site=SkyIslandLayout.at(random.nextInt(),random.nextInt());
            for(int fail=1;fail<=129;fail++) compare(site,true,fail);
        }
        require(cases>5000&&climatePoints>100000,
                "Primitive offshore smoke lacked realistic early-exit coverage");
        System.out.println("SKY_OCEAN_UNBOXED_TIER_PARITY_PASS cases="+cases
                +" ocean-probe-visits="+climatePoints);
    }
}
