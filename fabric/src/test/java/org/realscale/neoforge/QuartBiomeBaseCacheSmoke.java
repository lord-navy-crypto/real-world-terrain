package org.realscale.neoforge;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** Standalone Java 21 regression for the allocation-free ecotone input cache. */
public final class QuartBiomeBaseCacheSmoke {
    private QuartBiomeBaseCacheSmoke() {}
    private static void check(boolean test,String message) {
        if(!test)throw new AssertionError(message);
    }
    private static long sampleKey(int x,int y,int z) {
        // Same bit layout used by net.minecraft.core.BlockPos.asLong.
        return ((long)x&0x3ffffffL)<<38 |
               ((long)z&0x3ffffffL)<<12 |
               ((long)y&0xfffL);
    }
    private static long scrambled(long n) {
        n^=n>>>30;n*=0xbf58476d1ce4e5b9L;
        n^=n>>>27;n*=0x94d049bb133111ebL;
        return n^(n>>>31);
    }
    private static String sample(long key,long samplerSeed) {
        return Long.toHexString(scrambled(key ^ samplerSeed));
    }

    public static void main(String[] args) throws Exception {
        QuartBiomeBaseCache<String> cache=new QuartBiomeBaseCache<>();
        // A cache miss must re-evaluate the original deterministic source.
        // It may never invent a different biome when eviction occurs.
        long seed=0x33b9ac5eL,requests=0,hits=0;
        for(int z=-128;z<=128;z+=2)
            for(int x=-128;x<=128;x+=2)
                for(int y=-24;y<=480;y+=24) {
                    long key=sampleKey(x,y,z);
                    String expected=sample(key,seed);
                    String first=cache.get(key);
                    if(first!=null) {
                        hits++;
                        check(first.equals(expected),"incorrect cached biome");
                    } else {
                        cache.put(key,expected);
                    }
                    check(expected.equals(cache.get(key)),"new entry not readable");
                    requests++;
                }
        check(requests>100000,"insufficient mixed-Y quart samples");

        // Ecotone rays repeatedly sample very small neighborhoods.
        // Test positions on all sides of zero and with negative altitude.
        for(int qx=-70;qx<=70;qx+=7)
            for(int qz=-70;qz<=70;qz+=7)
                for(int qy=-8;qy<=400;qy+=19) {
                    long center=sampleKey(qx,qy,qz);
                    for(int dx=-5;dx<=5;dx++)
                        for(int dz=-5;dz<=5;dz++) {
                            long key=sampleKey(qx+dx,qy,qz+dz);
                            String expected=sample(key,seed);
                            String result=cache.get(key);
                            if(result!=null)check(result.equals(expected),"ray cache parity");
                            else cache.put(key,expected);
                        }
                    String checkAgain=cache.get(center);
                    if(checkAgain!=null)
                        check(checkAgain.equals(sample(center,seed)),"ray center parity");
                }

        // Exercise two keys deliberately mapped to one bucket, as well as
        // eviction and a sampler-identity change.
        long[] colliding=new long[3];
        int found=0;
        int target=-1;
        for(long k=1;k<=2_000_000&&found<3;k++) {
            int bucket=bucketForTest(k);
            if(target<0)target=bucket;
            if(bucket==target)colliding[found++]=k;
        }
        check(found==3,"unable to find forced cache collisions");
        for(long key:colliding)cache.put(key,sample(key,seed));
        for(long key:colliding) {
            String result=cache.get(key);
            if(result!=null)check(result.equals(sample(key,seed)),
                    "eviction returned a wrong holder");
        }
        cache.clear();
        for(long key:colliding)check(cache.get(key)==null,"sampler switch leaked stale biome");
        for(long key:colliding)cache.put(key,sample(key,seed+1));
        for(long key:colliding) {
            String result=cache.get(key);
            if(result!=null)check(result.equals(sample(key,seed+1)),
                    "changed sampler returned stale biome");
        }

        // Distinct generation workers must never share mutable results.
        AtomicReference<Throwable> error=new AtomicReference<>();
        Thread[] workers=new Thread[4];
        for(int w=0;w<workers.length;w++) {
            final int worker=w;
            workers[w]=new Thread(()->{
                try{
                    QuartBiomeBaseCache<String> local=new QuartBiomeBaseCache<>();
                    long workerSeed=seed+worker;
                    for(int i=0;i<25_000;i++) {
                        long key=sampleKey(i%517-280,worker*80-i%89,i%401-210);
                        String expected=sample(key,workerSeed);
                        String actual=local.get(key);
                        if(actual==null)local.put(key,expected);
                        else check(actual.equals(expected),"worker cached a different sampler");
                        if(i%3071==0)local.clear();
                    }
                }catch(Throwable e){error.compareAndSet(null,e);}
            },"ecotone-cache-worker-"+w);
            workers[w].start();
        }
        for(Thread worker:workers)worker.join();
        if(error.get()!=null)throw new AssertionError("concurrent cache smoke",error.get());
        System.out.println("PASS: primitive ecotone cache request parity="+requests
                +" immediate_hits="+hits+" workers="+workers.length);
    }

    private static int bucketForTest(long key) {
        long h=key^(key>>>21)^(key>>>42);
        h^=h>>>33;
        h*=0xff51afd7ed558ccdL;
        h^=h>>>33;
        return (int)h & ((QuartBiomeBaseCache.SLOTS/2)-1);
    }
}
