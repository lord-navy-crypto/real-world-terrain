package org.realscale.neoforge;

import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiPredicate;

/**
 * Exact offshore isolation tier parity against original clockwise probe order.
 * Also checks lower predicate call count for representative directional coasts.
 */
public final class SkyIslandOceanTierProbeOrderSmoke {
    private static final int[] RADII={64,128,192,256,352,448,544,640};
    private static final int DIRECTIONS=16;
    private static void require(boolean b,String msg){
        if(!b)throw new AssertionError(msg);
    }
    private static int original(SkyIslandLayout.Site base,
            BiPredicate<Integer,Integer> deep,BiPredicate<Integer,Integer> ocean) {
        if(!deep.test(base.x(),base.z()))return 0;
        for(int ring=0;ring<RADII.length;ring++)
            for(int i=0;i<DIRECTIONS;i++) {
                double angle=i*2.0*Math.PI/DIRECTIONS;
                int x=base.x()+(int)Math.round(RADII[ring]*Math.cos(angle));
                int z=base.z()+(int)Math.round(RADII[ring]*Math.sin(angle));
                if(!ocean.test(x,z))return ring<4?0:ring<6?1:2;
            }
        return 3;
    }
    public static void main(String[] args) {
        var home=SkyIslandLayout.at(-30123,13571);
        // Test all 128 original positions on every ring. Exact checkerboard
        // and random land responses cover all possible per-ring failures.
        var random=new Random(20261021L);
        for(int i=0;i<12000;i++) {
            int cell=i;
            int seed=random.nextInt();
            BiPredicate<Integer,Integer> island=(x,z)->{
                long v=(long)(x-home.x())*73856093L
                        ^(long)(z-home.z())*19349663L
                        ^ seed*83492791L ^cell;
                return (v&7)!=0;
            };
            boolean deep= (i&3)!=0;
            BiPredicate<Integer,Integer> gate=(x,z)->deep;
            require(original(home,gate,island)==SkyIslandLayout.oceanTier(
                    home,gate,island),"Ocean tier changed for randomized coast");
        }
        int actualCalls=0,referenceCalls=0,coasts=0;
        for(int distance:new int[]{0,70,150,210,270,340,470}) {
            for(int phase=0;phase<360;phase++) {
                double theta=phase*2.0*Math.PI/360.0;
                double nx=Math.cos(theta),nz=Math.sin(theta);
                BiPredicate<Integer,Integer> coast=(x,z)->
                    (x-home.x())*nx+(z-home.z())*nz<=distance;
                var oldCount=new AtomicLong();
                var newCount=new AtomicLong();
                int old=original(home,(x,z)->true,(x,z)->{
                    oldCount.incrementAndGet();return coast.test(x,z);
                });
                int now=SkyIslandLayout.oceanTier(home,(x,z)->true,(x,z)->{
                    newCount.incrementAndGet();return coast.test(x,z);
                });
                require(old==now,"Directional coastline tier changed");
                referenceCalls+=oldCount.intValue();
                actualCalls+=newCount.intValue();
                coasts++;
            }
        }
        require(actualCalls<referenceCalls*0.97,
                "Spaced coastal probes did not reduce actual climate calls: "+
                actualCalls+" vs "+referenceCalls);
        // Open ocean still receives all 128 checks and retains tier 3.
        AtomicLong open=new AtomicLong();
        require(SkyIslandLayout.oceanTier(home,(x,z)->true,
                (x,z)->{open.incrementAndGet();return true;})==3,
                "Open ocean tier or island count changed");
        require(open.get()==128,"Ocean core lost deterministic probes");
        System.out.println("REALSCALE_SKY_OCEAN_SPREAD_PROBE_PASS coasts="+coasts
                +" baseline_calls="+referenceCalls+" optimized_calls="+actualCalls);
    }
}
