package org.realscale.neoforge;

import java.util.Random;

/** Bit-exact sine reuse for curved Grand Overworld cave corridor widths. */
public final class GrandOverworldCorridorSineParitySmoke {
    private static void compare(double t) {
        double original=0.80+0.20*Math.sin(Math.PI*t)*Math.sin(Math.PI*t);
        double reused=Math.sin(Math.PI*t);
        double optimized=0.80+0.20*reused*reused;
        if(Double.doubleToRawLongBits(original)!=Double.doubleToRawLongBits(optimized))
            throw new AssertionError("Sine reuse changed corridor width at t="+t);
    }
    public static void main(String[] args) {
        for(int i=1;i<100_000;i++)compare(i/100_000.0);
        var random=new Random(20261021L);
        for(int i=0;i<120_000;i++)compare(random.nextDouble());
        for(double t:new double[]{Math.nextUp(0.0),0.25,0.5,0.75,
                                  Math.nextDown(1.0)})compare(t);
        System.out.println("REALSCALE_OVERWORLD_CORRIDOR_SINE_BIT_PARITY_PASS samples=220004");
    }
}
