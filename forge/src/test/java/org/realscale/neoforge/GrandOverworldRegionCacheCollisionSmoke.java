package org.realscale.neoforge;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Proves alternating same-hash regions keep distinct immutable geometry
 * snapshots without changing world-absolute cave density bits.
 */
public final class GrandOverworldRegionCacheCollisionSmoke {
    private GrandOverworldRegionCacheCollisionSmoke() {}
    private static void check(boolean yes,String message) {
        if(!yes)throw new AssertionError(message);
    }
    private static int directSlot(int gx,int gz) {
        int hash=gx*0x9e3779b9 ^ Integer.rotateLeft(gz*0x85ebca6b,13);
        return (hash^(hash>>>16))&7;
    }
    public static void main(String[] args) throws Exception {
        // Deliberately collide in the original eight-slot direct map as
        // well as in the new four-bucket, two-entry cache.
        int target=directSlot(-19,13);
        List<int[]> sites=new ArrayList<>();
        outer: for(int gx=-30;gx<=30;gx++) for(int gz=-30;gz<=30;gz++) {
            if(directSlot(gx,gz)==target) {
                sites.add(new int[]{gx,gz});
                if(sites.size()==3)break outer;
            }
        }
        check(sites.size()==3,"Unable to find three colliding region keys");
        Class<?> region=Class.forName(
                "org.realscale.neoforge.GrandOverworldCaveNetwork$RegionCache");
        Constructor<?> constructor=region.getDeclaredConstructor();
        constructor.setAccessible(true);
        Method get=region.getDeclaredMethod("get",int.class,int.class);
        get.setAccessible(true);
        Object cache=constructor.newInstance();
        int[] a=sites.get(0),b=sites.get(1),c=sites.get(2);
        Object regionA=get.invoke(cache,a[0],a[1]);
        Object regionB=get.invoke(cache,b[0],b[1]);
        check(regionA!=regionB,"Distinct region keys share a mutable snapshot");
        for(int iteration=0;iteration<1000;iteration++) {
            check(get.invoke(cache,a[0],a[1])==regionA,
                    "Two-way cache rebuilt A during alternating probes");
            check(get.invoke(cache,b[0],b[1])==regionB,
                    "Two-way cache rebuilt B during alternating probes");
        }
        Object regionC=get.invoke(cache,c[0],c[1]);
        check(regionC!=null,"Third colliding site did not load");
        // Stable world-position output must not depend on probe order,
        // even after forcing set-associative victim replacement.
        final long[] baseline=new long[sites.size()*4];
        int n=0;
        for(int[] site:sites)for(int layer=0;layer<4;layer++) {
            var hall=GrandOverworldCaveNetwork.hall(site[0],site[1],layer);
            baseline[n++]=Double.doubleToRawLongBits(
                    GrandOverworldCaveNetwork.strength(hall.x(),hall.y(),hall.z()));
        }
        for(int pass=0;pass<10;pass++) {
            get.invoke(cache,b[0],b[1]);
            get.invoke(cache,c[0],c[1]);
            get.invoke(cache,a[0],a[1]);
            n=0;
            for(int[] site:sites)for(int layer=0;layer<4;layer++) {
                var hall=GrandOverworldCaveNetwork.hall(site[0],site[1],layer);
                long actual=Double.doubleToRawLongBits(
                        GrandOverworldCaveNetwork.strength(hall.x(),hall.y(),hall.z()));
                check(actual==baseline[n++],"Region collision changed cave density");
            }
        }
        System.out.println("REALSCALE_OVERWORLD_REGION_2WAY_PASS alternating_hits=2000 "
                +"region_collision_sites=3 cave_bit_parity=120");
    }
}
