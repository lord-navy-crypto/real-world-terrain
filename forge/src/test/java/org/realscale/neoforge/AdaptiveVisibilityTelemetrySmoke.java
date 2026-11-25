package org.realscale.neoforge;

/** Tiny pure-Java regression for bounded client probe-cost metrics. */
public final class AdaptiveVisibilityTelemetrySmoke {
    private AdaptiveVisibilityTelemetrySmoke(){}
    private static void yes(boolean result,String why){
        if(!result)throw new AssertionError(why);
    }
    public static void main(String[] args){
        var t=new AdaptiveVisibilityTelemetry();
        yes(t.samples()==0&&t.averageMicroseconds()==0
                    &&t.p95BucketUpperMicroseconds()==0,
                "Empty diagnostic histogram must be valid");
        // Vary timing widely; check that 95th percentile is a bounded,
        // conservative logarithmic bucket (NOT a client frametime metric).
        for(int i=0;i<1000;i++) {
            var mode=i<600 ? AdaptiveVisibilityBudget.Mode.SEALED_CAVE
                    : i<900 ? AdaptiveVisibilityBudget.Mode.OPEN_SURFACE
                    : AdaptiveVisibilityBudget.Mode.TRANSITION;
            t.recordSample(mode,i%10!=0,i<950 ? 1_000L : 40_000L);
        }
        yes(t.samples()==1000,"Probe count");
        yes(t.unknownSamples()==100,"Unknown budget");
        yes(t.samplesInMode(AdaptiveVisibilityBudget.Mode.SEALED_CAVE)==600,
                "Cave observation counter");
        yes(t.samplesInMode(AdaptiveVisibilityBudget.Mode.OPEN_SURFACE)==300,
                "Surface observation counter");
        yes(t.samplesInMode(AdaptiveVisibilityBudget.Mode.TRANSITION)==100,
                "Transition observation counter");
        yes(t.averageMicroseconds()==3,"Probe mean should be 2.95us rounded");
        yes(t.maxMicroseconds()==40,"Probe maximum");
        yes(t.p95BucketUpperMicroseconds()>=1
                    && t.p95BucketUpperMicroseconds()<=63,
                "Conservative p95 histogram bucket invalid");
        t.recordSample(AdaptiveVisibilityBudget.Mode.TRANSITION,true,-5);
        yes(t.maxMicroseconds()==40,"Negative clock deltas must be clamped");
        yes(t.summary().contains("NOT FPS or GPU time"),
                "Do not misrepresent detector overhead as frame performance");
        System.out.println("REAL_SCALE_ADAPTIVE_VISIBILITY_TELEMETRY_PASS");
    }
}
