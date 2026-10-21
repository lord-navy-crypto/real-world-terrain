package org.realscale.neoforge;

/** Pure Java 21 opt-in density profiler regression, with no Minecraft imports. */
public final class DensityPerfSamplerSmoke {
    private DensityPerfSamplerSmoke(){}
    static void check(boolean ok,String why) {
        if(!ok)throw new AssertionError(why);
    }
    public static void main(String[] args) {
        var cave=DensityPerfSampler.Stage.OVERWORLD_CAVE;
        var sky=DensityPerfSampler.Stage.SKY_BIOME;
        if(!DensityPerfSampler.ENABLED) {
            for(int i=0;i<10_000;i++) {
                long started=DensityPerfSampler.begin(cave);
                check(started==0,"Disabled profiler read system clock");
                DensityPerfSampler.finish(cave,started);
            }
            check(DensityPerfSampler.snapshot(cave).contains("samples=0"),
                    "Disabled profiler recorded data");
            System.out.println("TVA_DENSITY_SAMPLER_DISABLED_PASS");
            return;
        }
        long collected=0;
        for(int i=0;i<9_216;i++) {
            long started=DensityPerfSampler.begin(cave);
            if(started!=0)collected++;
            DensityPerfSampler.finish(cave,started);
        }
        check(collected==9,"Expected 1/1024 density profiling cadence");
        String snap=DensityPerfSampler.snapshot(cave);
        check(snap.contains("samples=9"),"Missing sample count: "+snap);
        check(snap.contains("stride=1024"),"Missing sampling rate: "+snap);
        check(snap.contains("mean_ns="),"Lost nanosecond precision: "+snap);
        check(snap.contains("p95_bucket_upper_us="),"Missing bounded histogram");
        check(DensityPerfSampler.snapshot(sky).contains("samples=0"),
                "Stages must have independent counters");
        for(int i=0;i<1024;i++)
            DensityPerfSampler.finish(sky,DensityPerfSampler.begin(sky));
        check(DensityPerfSampler.snapshot(sky).contains("samples=1"),
                "Sky sampling should remain independent");
        DensityPerfSampler.ecotoneResolution();
        DensityPerfSampler.ecotoneResolution();
        DensityPerfSampler.ecotoneCacheProbe(true);
        DensityPerfSampler.ecotoneCacheProbe(false);
        String cache=DensityPerfSampler.ecotoneCacheSnapshot();
        check(cache.contains("resolution_calls=2")&&
                cache.contains("base_hits=1 base_misses=1"),
                "Wrong independent climate cache counters: "+cache);
        System.out.println("TVA_DENSITY_SAMPLER_ENABLED_PASS "+snap);
    }
}
