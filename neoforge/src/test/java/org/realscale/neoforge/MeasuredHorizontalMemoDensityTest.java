package org.realscale.neoforge;

import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The memo applies ONLY to the 2D height signal. The complete Y gradient
 * and 3D cave functions are evaluated outside it, unchanged.
 */
public class MeasuredHorizontalMemoDensityTest {
    private static final class PureHorizontal implements DensityFunction.SimpleFunction {
        final AtomicInteger calls=new AtomicInteger();
        final double seed;
        PureHorizontal(double seed) { this.seed=seed; }

        @Override public double compute(DensityFunction.FunctionContext ctx) {
            calls.incrementAndGet();
            return seed + ctx.blockX()*0.03125 + ctx.blockZ()*0.0078125;
        }
        @Override public double minValue() { return -1000000; }
        @Override public double maxValue() { return 1000000; }
        @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return null; // Test-only immutable 2D signal.
        }
    }

    @Test void repeatedVerticalStructureQueriesUseOneExactHorizontalSample() {
        PureHorizontal direct=new PureHorizontal(2.25);
        var memo=new MeasuredHorizontalMemoDensity(direct);
        for(int y=-64;y<2032;y++) {
            var ctx=new DensityFunction.SinglePointContext(1024,y,1024);
            long expected=Double.doubleToRawLongBits(2.25+1024*0.03125+1024*0.0078125);
            assertEquals(expected,Double.doubleToRawLongBits(memo.compute(ctx)));
        }
        assertEquals(1,direct.calls.get(),"2096-height probe should reuse XY-independent work");
    }

    @Test void cacheEvictionsAndNegativeWorldCoordinatesAreBitExact() {
        PureHorizontal input=new PureHorizontal(0.75);
        var memo=new MeasuredHorizontalMemoDensity(input);
        for(int k=0;k<16000;k++) {
            int x=(k*73)%30001-15000;
            int z=(k*127)%32003-16001;
            var ctx=new DensityFunction.SinglePointContext(x,(k*11)%2096-64,z);
            double baseline=0.75+x*0.03125+z*0.0078125;
            assertEquals(Double.doubleToRawLongBits(baseline),
                    Double.doubleToRawLongBits(memo.compute(ctx)));
        }
    }

    @Test void differentSeedMappedInstancesCannotShareCachedHeights() {
        var one=new MeasuredHorizontalMemoDensity(new PureHorizontal(1.0));
        var two=new MeasuredHorizontalMemoDensity(new PureHorizontal(-9.0));
        var point=new DensityFunction.SinglePointContext(-512,700,1024);
        assertNotEquals(Double.doubleToRawLongBits(one.compute(point)),
                        Double.doubleToRawLongBits(two.compute(point)));
    }
}
