package org.realscale.neoforge;

import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

/** Standalone Java regression: collision, eviction, seed and bitwise fidelity. */
public final class MeasuredColumnMemoCollisionSmoke {
    private static void check(boolean ok,String description) {
        if (!ok) throw new AssertionError(description);
    }
    private static long oracle(int x,int z,int seed) {
        long n = ((long)x << 32) | (z & 0xffffffffL);
        n ^= seed * 0x9e3779b97f4a7c15L;
        n ^= n >>> 30;
        n *= 0xbf58476d1ce4e5b9L;
        n ^= n >>> 27;
        n *= 0x94d049bb133111ebL;
        return n ^ (n >>> 31);
    }
    private static double sample(int x,int z,int seed) {
        long raw = oracle(x,z,seed);
        return Double.longBitsToDouble((raw & 0x800fffffffffffffL) | 0x3ff0000000000000L);
    }
    private static void verify(MeasuredColumnMemo memo,int x,int z,int seed) {
        if (!memo.lookup(x,z)) memo.store(sample(x,z,seed));
        check(Double.doubleToRawLongBits(memo.value()) ==
                Double.doubleToRawLongBits(sample(x,z,seed)),
                "Mismatched column ("+x+","+z+") seed="+seed);
    }
    private static int oldDirectSlot(int x,int z) {
        int hash=x*0x9e3779b9 ^ Integer.rotateLeft(z*0x85ebca6b,13);
        return (hash^(hash>>>16)) & (MeasuredColumnMemo.SLOTS-1);
    }
    public static void main(String[] args) throws Exception {
        int x0=-29_998_997,z0=17;
        int collisionX=0,collisionZ=0;
        boolean found=false;
        int expectedBucket=MeasuredColumnMemo.bucket(x0,z0);
        outer: for (int x=-350; x<=350; x++) for (int z=-350; z<=350; z++) {
            if(x==x0 && z==z0)continue;
            if (oldDirectSlot(x,z)==oldDirectSlot(x0,z0)) {
                collisionX=x;collisionZ=z;found=true;break outer;
            }
        }
        check(found,"No colliding signed coordinate pair found");
        check(MeasuredColumnMemo.bucket(collisionX,collisionZ)==expectedBucket,
                "Old direct-map collision must also collide in two-way bucket");
        var cache=new MeasuredColumnMemo();
        check(!cache.lookup(x0,z0),"Uninitialized slot unexpectedly valid");
        cache.store(-0.0);
        check(!cache.lookup(collisionX,collisionZ),"Collision returned wrong entry");
        cache.store(Double.longBitsToDouble(0x7ff8000000000001L));
        // Exact raw bits retained simultaneously, even after the hot entry changes.
        check(cache.lookup(x0,z0) && Double.doubleToRawLongBits(cache.value()) ==
                Double.doubleToRawLongBits(-0.0),"First colliding value lost");
        check(cache.lookup(collisionX,collisionZ) && Double.doubleToRawLongBits(cache.value()) ==
                0x7ff8000000000001L,"Second colliding NaN lost");
        // A third key in the same bucket forces eviction, never false results.
        int thirdX=0,thirdZ=0;
        boolean thirdFound=false;
        outer: for(int x=-350;x<=350;x++) for(int z=-350;z<=350;z++) {
            if(x==collisionX && z==collisionZ)continue;
            if(oldDirectSlot(x,z)==oldDirectSlot(x0,z0)) {
                thirdX=x;thirdZ=z;thirdFound=true;break outer;
            }
        }
        check(thirdFound,"No third coordinate found in collision bucket");
        check(!cache.lookup(thirdX,thirdZ),"Third collision incorrectly hit");
        cache.store(1.25);
        check(cache.lookup(thirdX,thirdZ) && cache.value()==1.25,
                "Third collision could not be stored");
        if (cache.lookup(x0,z0))
            check(Double.doubleToRawLongBits(cache.value())==
                    Double.doubleToRawLongBits(-0.0),"Eviction aliased first value");
        if (cache.lookup(collisionX,collisionZ))
            check(Double.doubleToRawLongBits(cache.value())==
                    0x7ff8000000000001L,"Eviction aliased second value");
        var probe=new MeasuredColumnMemo();
        var random=new Random(20261021L);
        for(int i=0;i<80000;i++) {
            int x = switch (i % 6) {
                case 0 -> Integer.MIN_VALUE + i;
                case 1 -> Integer.MAX_VALUE - i;
                case 2 -> -29_999_000 + random.nextInt(2000);
                case 3 -> 29_999_000 - random.nextInt(2000);
                default -> random.nextInt(20000)-10000;
            };
            int z= random.nextInt(4000000)-2000000;
            verify(probe,x,z,7);
        }
        // Alternating conflicting columns: cold after two inserts, hot thereafter.
        cache=new MeasuredColumnMemo();
        int misses=0;
        for(int i=0;i<10000;i++) {
            int x=(i&1)==0?x0:collisionX;
            int z=(i&1)==0?z0:collisionZ;
            if(!cache.lookup(x,z)) {
                misses++;
                cache.store(sample(x,z,2));
            }
            check(Double.doubleToRawLongBits(cache.value())==
                    Double.doubleToRawLongBits(sample(x,z,2)),"Alternating collision parity");
        }
        check(misses==2,"Two-way collision protection failed: misses="+misses);
        var other=new MeasuredColumnMemo();
        verify(other,x0,z0,123);
        check(!cache.lookup(x0,z0) ||
                Double.doubleToRawLongBits(cache.value())==
                Double.doubleToRawLongBits(sample(x0,z0,2)),"Cross-seed pollution");
        AtomicReference<Throwable> failure=new AtomicReference<>();
        Thread[] workers=new Thread[4];
        for(int t=0;t<workers.length;t++) {
            final int seed=t+100;
            workers[t]=new Thread(()->{
                try {
                    var workerMemo=new MeasuredColumnMemo();
                    for(int i=0;i<20000;i++)
                        verify(workerMemo,(i*73)%60001-30000,
                                (i*47)%70001-35000,seed);
                } catch(Throwable e) {failure.compareAndSet(null,e);}
            });
            workers[t].start();
        }
        for(Thread worker:workers)worker.join();
        if(failure.get()!=null)throw new AssertionError("Thread isolation failed",failure.get());
        System.out.println("REALSCALE_MEASURED_COLUMN_2WAY_PASS random=80000 "
                +"alternating_hits=9998 workers=4 slots="+MeasuredColumnMemo.SLOTS);
    }
}
