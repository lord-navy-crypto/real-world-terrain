package org.realscale.neoforge;

import java.util.Arrays;
import java.util.Locale;

/**
 * Actual before/after microbenchmark, not Minecraft TPS/FPS.
 * Frozen Phase-24 implementation versus the Phase-25 production class.
 *
 * The benchmark exercises the exact same CPU cave strength, X/Z warps,
 * multi-region switching and colliding columns with strict raw-bit parity.
 * No artificial sleeps, no fabricated metrics, no speed pass/fail threshold.
 */
public final class GrandNetherCaveCacheBenchmark {
    private GrandNetherCaveCacheBenchmark() {}

    private record Probe(int x,int z,double y,double wx,double wz) {}
    private static volatile long consumed;

    private static int oldBucket(int x,int z) {
        return (x*0x9e3779b9 ^ z*0x85ebca6b)&511;
    }

    private static Probe[] collidingColumns(int gx,int gz) {
        int[] xs=new int[512],zs=new int[512];
        boolean[] seen=new boolean[512];
        for(int lx=24;lx<232;lx++) for(int lz=24;lz<232;lz++) {
            int x=gx*256+lx,z=gz*256+lz,slot=oldBucket(x,z);
            if(seen[slot] && (xs[slot]!=x || zs[slot]!=z)) {
                double y=GrandNetherCaveNetwork.hall(gx,gz,0).y();
                return new Probe[]{new Probe(xs[slot],zs[slot],y,.375,-.625),
                        new Probe(x,z,y,.375,-.625)};
            }
            xs[slot]=x;zs[slot]=z;seen[slot]=true;
        }
        throw new AssertionError("No colliding Nether columns found");
    }

    private static Probe[] data(boolean multiRegion) {
        Probe[] pair=multiRegion
                ?new Probe[]{
                    center(-9,-7),center(2,3),center(-1,4),center(11,-6)}
                :collidingColumns(0,0);
        Probe[] probes=new Probe[pair.length*7];
        int k=0;
        for(int i=0;i<7;i++) for(Probe p:pair) {
            // Multiple height samples per X/Z column. Same arithmetic for
            // baseline and candidate; offsets do not escape +/-5, +/-4 warp.
            probes[k++]=new Probe(p.x,p.z,p.y+(i-3)*5.25,p.wx,p.wz);
        }
        return probes;
    }

    private static Probe center(int gx,int gz) {
        var h=GrandNetherCaveNetwork.hall(gx,gz,0);
        return new Probe((int)h.x(),(int)h.z(),h.y(),1.25,-1.50);
    }

    private static long bits(boolean old,Probe p) {
        double value=old
                ?GrandNetherCaveNetworkPrePhase25.strengthFromColumn(
                        p.x,p.z,p.x+p.wx,p.y,p.z+p.wz)
                :GrandNetherCaveNetwork.strengthFromColumn(
                        p.x,p.z,p.x+p.wx,p.y,p.z+p.wz);
        return Double.doubleToRawLongBits(value);
    }

    private static void verify(Probe[] probes) {
        for(int repeat=0;repeat<6;repeat++)
            for(Probe probe:probes) {
                long expected=bits(true,probe),actual=bits(false,probe);
                if(expected!=actual)throw new AssertionError(
                        "Phase 25 changed Nether cave density: "+probe);
            }
    }

    private static long time(boolean old,Probe[] probes,int rounds) {
        long sum=0;
        long begin=System.nanoTime();
        for(int i=0;i<rounds;i++) {
            Probe p=probes[i%probes.length];
            long bit=bits(old,p);
            // Consume all outputs; keep JIT from deleting either evaluator.
            sum=Long.rotateLeft(sum,1)^bit^(long)i*0x9e3779b97f4a7c15L;
        }
        consumed=sum;
        return System.nanoTime()-begin;
    }

    private static double median(double[] values) {
        double[] copy=values.clone();
        Arrays.sort(copy);
        return copy[copy.length/2];
    }

    private static void run(String scenario,Probe[] probes) {
        verify(probes);
        for(int round=0;round<3;round++) {
            time(true,probes,12000);
            time(false,probes,12000);
        }
        double[] before=new double[7],after=new double[7];
        final int iterations=48000;
        for(int round=0;round<7;round++) {
            if((round&1)==0) {
                before[round]=time(true,probes,iterations)/(double)iterations;
                after[round]=time(false,probes,iterations)/(double)iterations;
            } else {
                after[round]=time(false,probes,iterations)/(double)iterations;
                before[round]=time(true,probes,iterations)/(double)iterations;
            }
        }
        double oldMedian=median(before),newMedian=median(after);
        System.out.printf(Locale.ROOT,
                "REALSCALE_NETHER_CACHE_AB scenario=%s probes=%d iterations_per_round=%d " +
                "repetitions=%d pre_phase25_ns_per_sample=%.2f " +
                "phase25_ns_per_sample=%.2f speedup=%.3fx " +
                "raw_ieee_parity=PASS method=median_7_jit_warmup_no_fps_claim%n",
                scenario,probes.length,iterations,before.length,
                oldMedian,newMedian,oldMedian/newMedian);
    }

    public static void main(String[] args) {
        run("region_alternating",data(true));
        run("column_hash_collision",data(false));
        System.out.println("REALSCALE_NETHER_CACHE_AB_PASS");
    }
}
