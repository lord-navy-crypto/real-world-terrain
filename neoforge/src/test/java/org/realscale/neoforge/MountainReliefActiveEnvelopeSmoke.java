package org.realscale.neoforge;

/** Deterministic bit-exact proof of early exclusion of plateau / mesa support. */
public final class MountainReliefActiveEnvelopeSmoke {
    private static void check(boolean yes,String msg) {
        if(!yes)throw new AssertionError(msg);
    }
    private static long bits(double value) {
        return Double.doubleToRawLongBits(value);
    }
    private static void verify(int kind,double dx,double dz,double radius) {
        boolean rejected=MountainReliefMath.definitelyOutsideActiveRelief(
                kind,dx,dz,radius);
        if(!rejected)return;
        for(double baseline:new double[]{0.650001,0.9,1.7,2.2,3.5,6.0,15.0}) {
            for(double continent:new double[]{-0.2,-0.099,0.1,0.22,0.7,1.0}) {
                double gate=MountainReliefMath.inlandUplandGate(
                        continent,baseline);
                for(double anchor:new double[]{1.2,2.5,7.0,13.0}) {
                    double distance=Math.hypot(dx,dz)/radius;
                    double expected=MountainReliefMath.reliefWithGate(
                            baseline,anchor,continent,kind,distance,
                            1.5,0.9,gate);
                    check(bits(expected)==bits(baseline),
                            "False rejection for kind="+kind+" r="+distance+
                            " original="+baseline+" old="+expected);
                }
            }
        }
    }
    public static void main(String[] args) {
        long checks=0;
        for(double radius:new double[]{470,600,700}) {
            for(int kind:new int[]{MountainReliefMath.BASIN,
                    MountainReliefMath.PLATEAU,MountainReliefMath.MESA}) {
                for(int dx=-900;dx<=900;dx+=13)
                    for(int dz=-900;dz<=900;dz+=17) {
                        verify(kind,dx,dz,radius);
                        checks++;
                    }
                // Edge and ULP neighborhoods to prevent crest discontinuity.
                double limit=radius*(kind==MountainReliefMath.MESA ?
                        0.58 : kind==MountainReliefMath.PLATEAU ? 1.0 : 1.1);
                for(int k=-100;k<=100;k++) {
                    double x=Math.nextAfter(limit,
                            k<0?Double.NEGATIVE_INFINITY:Double.POSITIVE_INFINITY);
                    for(int j=0;j<Math.abs(k);j++)x=Math.nextAfter(
                            x,k<0?Double.NEGATIVE_INFINITY:Double.POSITIVE_INFINITY);
                    verify(kind,x,0,radius);
                    verify(kind,-x,0,radius);
                }
            }
        }
        for(double distance:new double[]{470,700,950}) {
            check(!MountainReliefMath.definitelyOutsideActiveRelief(
                    MountainReliefMath.BASIN,distance,0,700),
                    "Basin must keep existing 1.10 support envelope");
        }
        check(checks>80_000,"insufficient coverage");
        System.out.println("REALSCALE_MOUNTAIN_ACTIVE_ENVELOPE_BIT_EXACT_PASS cases="+checks);
    }
}
