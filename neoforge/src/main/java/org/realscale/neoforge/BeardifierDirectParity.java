package org.realscale.neoforge;

import java.util.concurrent.atomic.LongAdder;

/**
 * Re-run unmodified vanilla Beardifier in an identical compute context.
 * Sample Y exclusions at 1/4096 and independent new XZ-only exits at 1/256.
 * Raw-bit +0.0 equivalence is required; nonzero results throw immediately.
 */
public final class BeardifierDirectParity {
    private static final boolean VERIFY=
            Boolean.getBoolean("realscale.experimental.beardifier.verify");
    private static final ThreadLocal<State> STATE=ThreadLocal.withInitial(State::new);
    private static final LongAdder VERIFIED=new LongAdder();
    private static final LongAdder SKIPPED=new LongAdder();
    private static final LongAdder XZ_ONLY=new LongAdder();
    private static final LongAdder XZ_SAMPLES=new LongAdder();
    private static final class State {
        long yCount,xzCount;
        boolean replaying;
    }
    static {
        if(VERIFY)Runtime.getRuntime().addShutdownHook(new Thread(()->{
            System.err.println("REALSCALE_BEARDIFIER_DIRECT_PARITY_PASS samples="+
                    VERIFIED.sum()+" skipped="+SKIPPED.sum()+
                    " xz_only="+XZ_ONLY.sum()+" xz_samples="+XZ_SAMPLES.sum());
        },"realscale-experimental-beardifier-parity"));
    }
    private BeardifierDirectParity(){}
    public static boolean replaying(){return VERIFY&&STATE.get().replaying;}
    public static boolean shouldVerifySkip(boolean xzOnly){
        if(!VERIFY)return false;
        SKIPPED.increment();
        State s=STATE.get();
        if(xzOnly){
            XZ_ONLY.increment();
            return (++s.xzCount & 255L)==0;
        }
        return (++s.yCount & 4095L)==0;
    }
    public static void setReplaying(boolean enabled){STATE.get().replaying=enabled;}
    public static void assertZero(double actual,int x,int y,int z,boolean xzOnly){
        if(Double.doubleToRawLongBits(actual)!=Double.doubleToRawLongBits(0.0))
            throw new AssertionError("Beardifier spatial zero-cull was WRONG at "+
                    x+","+y+","+z+" xzOnly="+xzOnly+" vanilla="+actual);
        VERIFIED.increment();
        if(xzOnly)XZ_SAMPLES.increment();
    }
}
