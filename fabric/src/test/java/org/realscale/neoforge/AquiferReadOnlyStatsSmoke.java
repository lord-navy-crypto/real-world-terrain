package org.realscale.neoforge;

public final class AquiferReadOnlyStatsSmoke {
    public static void main(String[] args){
        AquiferReadOnlyStats stats=AquiferReadOnlyStats.current();
        int count=0;
        for(int i=0;i<100_000;i++){
            if(!stats.shouldSample())continue;
            count++;
            stats.record(stats,i,0,-i,
                    i%2==0?0.1:-0.1,
                    i%4==0,i%4==1,i%4==2,i%4==3);
        }
        if(stats.seen()!=100_000L)
            throw new AssertionError("Unexpected observed call count");
        if(stats.sampled()!=count||count!=390)
            throw new AssertionError("Sampler must observe exactly one in 256");
        if(stats.nullCount()+stats.fluidCount()>count)
            throw new AssertionError("Outcome categories are disjoint");
        if(!stats.report().contains("method=read_only_original_results_one_in_256"))
            throw new AssertionError("Missing explicit sample interpretation");
        System.out.println("REALSCALE_AQUIFER_READONLY_AUDIT_SMOKE_PASS samples="+count);
    }
}
