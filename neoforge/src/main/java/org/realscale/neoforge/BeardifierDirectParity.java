package org.realscale.neoforge;

import java.util.concurrent.atomic.LongAdder;

/**
 * In one actual worldgen invocation, occasionally run the vanilla Beardifier
 * compute method on the identical context that would be culled, and compare
 * its result to +0.0 bit-for-bit. This verifies correctness without depending
 * on fluid tick ordering, tree decoration or NBT palette order across servers.
 */
public final class BeardifierDirectParity {
    private static final boolean VERIFY=
            Boolean.getBoolean("realscale.experimental.beardifier.verify");
    private static final ThreadLocal<State> STATE=ThreadLocal.withInitial(State::new);
    private static final LongAdder VERIFIED=new LongAdder();
    private static final LongAdder SKIPPED=new LongAdder();
    private static final LongAdder TIGHT_ONLY=new LongAdder();
    private static final LongAdder TIGHT_VERIFIED=new LongAdder();

    private static final class State {
        long count;
        long tightCount;
        boolean replaying;
    }
    static {
        if(VERIFY)Runtime.getRuntime().addShutdownHook(new Thread(()->{
            System.err.println("REALSCALE_BEARDIFIER_DIRECT_PARITY_PASS samples="+
                    VERIFIED.sum()+" skipped="+SKIPPED.sum()+
                    " tight_only="+TIGHT_ONLY.sum()+
                    " tight_samples="+TIGHT_VERIFIED.sum());
        },"realscale-experimental-beardifier-parity"));
    }
    private BeardifierDirectParity(){}

    public static boolean replaying() {
        return VERIFY && STATE.get().replaying;
    }
    public static boolean shouldVerifySkip(){
        return shouldVerifySkip(false);
    }
    public static boolean shouldVerifySkip(boolean tightOnly) {
        if(!VERIFY)return false;
        SKIPPED.increment();
        if(tightOnly){
            TIGHT_ONLY.increment();
            return (++STATE.get().tightCount & 255L)==0;
        }
        return (++STATE.get().count & 4095L)==0;
    }
    public static void setReplaying(boolean enabled) {
        STATE.get().replaying=enabled;
    }
    public static void assertTightZero(double actual,int x,int y,int z) {
        assertZero(actual,x,y,z);
        TIGHT_VERIFIED.increment();
    }
    public static void assertZero(double actual,int x,int y,int z) {
        if(Double.doubleToRawLongBits(actual)!=Double.doubleToRawLongBits(0.0))
            throw new AssertionError("Beardifier vertical exclusion was WRONG at "+
                    x+","+y+","+z+" vanilla="+actual);
        VERIFIED.increment();
    }
}
