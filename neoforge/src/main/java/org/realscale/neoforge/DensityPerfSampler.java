package org.realscale.neoforge;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.LongAdder;

/**
 * Optional, bounded sampling of actual terrain density and biome-source costs.
 *
 * Disabled by default. Set -Drealscale.density.profile=true on the Minecraft
 * JVM to sample 1/1024 invocations per worker and stage (not every voxel).
 * Complements WorldgenProfiler, which measures placed Feature.place() calls.
 *
 * The stage profiles are per-call samples, NOT per-chunk completion latency.
 * No worldgen code is changed when measuring and no per-coordinate state is
 * retained. A graceful shutdown reports samples even on short play sessions.
 */
final class DensityPerfSampler {
    static final boolean ENABLED=Boolean.getBoolean("realscale.density.profile");
    static final int STRIDE=1024;
    enum Stage { OVERWORLD_CAVE, NETHER_CAVE, ECOTONE_BIOME, SKY_BIOME }
    private static final ThreadLocal<int[]> CALLS=ThreadLocal.withInitial(
            ()->new int[Stage.values().length]);
    private static final Measurement[] METRICS={
            new Measurement(),new Measurement(),new Measurement(),new Measurement()
    };

    static {
        if(ENABLED)Runtime.getRuntime().addShutdownHook(new Thread(()->{
            for(Stage stage:Stage.values())
                if(METRICS[stage.ordinal()].samples.get()>0)
                    System.err.println("[RealScale density profile] "+snapshot(stage));
        },"realscale-density-profile-summary"));
    }

    static long begin(Stage stage) {
        if(!ENABLED)return 0;
        int[] calls=CALLS.get();
        int index=stage.ordinal();
        if((++calls[index] & (STRIDE-1)) != 0) return 0;
        return System.nanoTime();
    }

    static void finish(Stage stage,long started) {
        if(!ENABLED||started==0)return;
        long nanos=Math.max(0,System.nanoTime()-started);
        Measurement m=METRICS[stage.ordinal()];
        m.nanos.add(nanos);
        m.max.accumulateAndGet(nanos,Math::max);
        int bucket=bucket(nanos);
        m.histogram.incrementAndGet(bucket);
        long n=m.samples.incrementAndGet();
        if((n&4095)==0)
            System.err.println("[RealScale density profile] "+snapshot(stage));
    }

    static String snapshot(Stage stage) {
        Measurement m=METRICS[stage.ordinal()];
        long n=m.samples.get();
        long meanMicros=n==0?0:Math.round(m.nanos.sum()/(double)n/1000.0);
        long target=(long)Math.ceil(n*.95),observed=0,upper=0;
        for(int i=0;i<m.histogram.length();i++) {
            observed+=m.histogram.get(i);
            if(observed>=target&&n>0) {
                upper=i==0?0:(1L<<i)-1;
                break;
            }
        }
        return stage.name()+" samples="+n+" stride="+STRIDE+
                " mean_us="+meanMicros+" p95_bucket_upper_us="+upper+
                " max_us="+(m.max.get()/1000);
    }

    private static int bucket(long nanos) {
        long micros=nanos/1000;
        if(micros<=0)return 0;
        return Math.min(30,64-Long.numberOfLeadingZeros(micros));
    }

    private static final class Measurement {
        final LongAdder nanos=new LongAdder();
        final AtomicLong samples=new AtomicLong();
        final AtomicLong max=new AtomicLong();
        final AtomicLongArray histogram=new AtomicLongArray(31);
    }
    private DensityPerfSampler(){}
}
