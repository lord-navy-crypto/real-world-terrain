package org.realscale.neoforge;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Investigation only: bounded-frequency, read-only sampling of the ORIGINAL
 * vanilla 1.21.1 Aquifer.NoiseBasedAquifer.computeSubstance return states.
 *
 * Never caches block states or takes a shortcut. In particular, the original
 * mutable shouldScheduleFluidUpdate() state MUST NOT be reconstructed or
 * changed. All computations, side effects, fluids and tick scheduling remain
 * completely vanilla.
 *
 * One per-worker sampler; only one in 256 original returns is inspected.
 * Return fractions are samples, NOT a reliable direct estimate of milliseconds.
 */
public final class AquiferReadOnlyStats {
    private static final ConcurrentLinkedQueue<AquiferReadOnlyStats> ALL =
            new ConcurrentLinkedQueue<>();
    private static final ThreadLocal<AquiferReadOnlyStats> CURRENT =
            ThreadLocal.withInitial(()->{
                AquiferReadOnlyStats s=new AquiferReadOnlyStats();
                ALL.add(s);
                return s;
            });
    private long seen,sampled,nullResults,airResults,fluidResults,otherResults;
    private long positiveDensity,nonpositiveDensity,scheduleFluidUpdate;
    private long sampledRepeats;
    private boolean lastValid;
    private Object lastAquifer;
    private int lastX,lastY,lastZ;
    private long lastDensity;
    private AquiferReadOnlyStats(){}

    public static AquiferReadOnlyStats current(){return CURRENT.get();}

    /** Expensive blockstate/FluidState lookups happen at most 1/256 calls. */
    public boolean shouldSample(){
        return (++seen&255L)==0L;
    }

    /**
     * Result flags originate from vanilla BlockState and the actual Aquifer
     * public shouldScheduleFluidUpdate() getter AFTER computeSubstance returns.
     * No private fields are read or written.
     */
    public void record(Object aquifer,int x,int y,int z,double density,
            boolean isNull,boolean isAir,boolean isFluid,
            boolean needsFluidUpdate){
        sampled++;
        if(density>0.0)positiveDensity++;
        else nonpositiveDensity++;
        if(isNull)nullResults++;
        else if(isAir)airResults++;
        else if(isFluid)fluidResults++;
        else otherResults++;
        if(needsFluidUpdate)scheduleFluidUpdate++;
        long bits=Double.doubleToRawLongBits(density);
        // Conservative sample repeat count, not a full memo hit rate.
        if(lastValid&&lastAquifer==aquifer&&
                lastX==x&&lastY==y&&lastZ==z&&lastDensity==bits)
            sampledRepeats++;
        lastAquifer=aquifer;
        lastX=x;lastY=y;lastZ=z;lastDensity=bits;
        lastValid=true;
    }

    public long sampled(){return sampled;}
    public long seen(){return seen;}
    public long nullCount(){return nullResults;}
    public long fluidCount(){return fluidResults;}
    public long fluidUpdateCount(){return scheduleFluidUpdate;}

    public static String report(){
        long seen=0,sampled=0,nonpositive=0,positive=0;
        long nulls=0,air=0,fluid=0,other=0,updates=0,repeats=0;
        for(AquiferReadOnlyStats s:ALL){
            seen+=s.seen;sampled+=s.sampled;
            positive+=s.positiveDensity;nonpositive+=s.nonpositiveDensity;
            nulls+=s.nullResults;air+=s.airResults;
            fluid+=s.fluidResults;other+=s.otherResults;
            updates+=s.scheduleFluidUpdate;repeats+=s.sampledRepeats;
        }
        return "REALSCALE_AQUIFER_READONLY_AUDIT"+
                " calls="+seen+" samples="+sampled+
                " positive_density="+positive+
                " nonpositive_density="+nonpositive+
                " returned_null="+nulls+
                " returned_air="+air+
                " returned_fluid="+fluid+
                " returned_other="+other+
                " schedule_fluid_update="+updates+
                " adjacent_sample_repeat="+repeats+
                " method=read_only_original_results_one_in_256";
    }
}
