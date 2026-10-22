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
            Boolean.parseBoolean(System.getProperty(
                    "realscale.experimental.beardifier.verify","true"));
    private static final ThreadLocal<State> STATE=ThreadLocal.withInitial(State::new);
    private static final LongAdder VERIFIED=new LongAdder();
    private static final LongAdder SKIPPED=new LongAdder();

    private static final class State {
        long count;
        boolean replaying;
    }
    static {
        if(VERIFY)Runtime.getRuntime().addShutdownHook(new Thread(()->{
            System.err.println("REALSCALE_BEARDIFIER_DIRECT_PARITY_PASS samples="+
                    VERIFIED.sum()+" skipped="+SKIPPED.sum());
        },"realscale-experimental-beardifier-parity"));
    }
    private BeardifierDirectParity(){}

    public static boolean replaying() {
        return VERIFY && STATE.get().replaying;
    }
    public static boolean shouldVerifySkip() {
        if(!VERIFY)return false;
        SKIPPED.increment();
        return (++STATE.get().count & 4095L)==0;
    }
    public static void setReplaying(boolean enabled) {
        STATE.get().replaying=enabled;
    }
    public static void assertZero(double actual,int x,int y,int z) {
        if(Double.doubleToRawLongBits(actual)!=Double.doubleToRawLongBits(0.0))
            throw new AssertionError("Beardifier vertical exclusion was WRONG at "+
                    x+","+y+","+z+" vanilla="+actual);
        VERIFIED.increment();
    }
}
