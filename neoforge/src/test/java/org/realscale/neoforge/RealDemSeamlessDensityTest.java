package org.realscale.neoforge;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The independent v2 DEM never changes historical sampled altitudes. */
public final class RealDemSeamlessDensityTest {
    private static final RealDemTile ORIGINAL=RealDemTile.load();

    @Test void exactHeightAtAll256MeasuredGridPoints() {
        for(int z=-240;z<=240;z+=32)
            for(int x=-240;x<=240;x+=32)
                assertEquals(ORIGINAL.worldSolidYAt(x,z),
                             RealDemSeamlessMath.measuredHeight(x,z),1.0e-6,
                             "Sample mismatch "+x+","+z);
    }

    @Test void interpolationNeverIntroducesNewPeaksBetweenFourNeighbors() {
        for(int z=-240;z<240;z+=32)
            for(int x=-240;x<240;x+=32) {
                double a=ORIGINAL.worldSolidYAt(x,z);
                double b=ORIGINAL.worldSolidYAt(x+32,z);
                double c=ORIGINAL.worldSolidYAt(x,z+32);
                double d=ORIGINAL.worldSolidYAt(x+32,z+32);
                double low=Math.min(Math.min(a,b),Math.min(c,d));
                double high=Math.max(Math.max(a,b),Math.max(c,d));
                for(int dz=0;dz<=32;dz+=4)
                    for(int dx=0;dx<=32;dx+=4) {
                        double actual=RealDemSeamlessMath.measuredHeight(x+dx,z+dz);
                        assertTrue(actual>=low-1.0e-6 && actual<=high+1.0e-6,
                                   "Overshoot "+(x+dx)+","+(z+dz)+": "+actual);
                    }
            }
    }

    @Test void measurementStaysFullStrengthThroughOriginalTileEdge() {
        for(double x : new double[]{-240,-239,-128,0,128,239,240})
            for(double z : new double[]{-240,-128,0,128,240})
                assertEquals(1.0,RealDemSeamlessMath.weightOutside(x,z,768),1e-12);
        assertEquals(0.0,RealDemSeamlessMath.weightOutside(1008,0,768),1e-12);
        assertEquals(0.0,RealDemSeamlessMath.weightOutside(0,-1008,768),1e-12);
    }

    @Test void exteriorBlendIsContinuousAtEveryTileBorder() {
        for(double z : new double[]{-240,-128,0,128,240})
            for(int side : new int[]{-1,1}) {
                double edge=side*240;
                double a=RealDemSeamlessMath.extendedHeight(edge-0.01,z);
                double b=RealDemSeamlessMath.extendedHeight(edge+0.01,z);
                assertTrue(Math.abs(b-a)<2.0,"DEM edge discontinuity at "+edge+","+z);
                double weightA=RealDemSeamlessMath.weightOutside(edge-0.01,z,768);
                double weightB=RealDemSeamlessMath.weightOutside(edge+0.01,z,768);
                assertTrue(Math.abs(weightA-weightB)<0.001);
            }
    }

    @Test void extrapolatedSlopeTapersToFlatWithoutHardCrease() {
        assertEquals(0.0,RealDemSeamlessMath.taperedOutsideDisplacement(0),1e-12);
        assertEquals(64.0,RealDemSeamlessMath.taperedOutsideDisplacement(128),1e-12);
        assertEquals(64.0,RealDemSeamlessMath.taperedOutsideDisplacement(192),1e-12);
        assertEquals(-64.0,RealDemSeamlessMath.taperedOutsideDisplacement(-192),1e-12);
        double before=RealDemSeamlessMath.taperedOutsideDisplacement(128)
                    -RealDemSeamlessMath.taperedOutsideDisplacement(127);
        double after=RealDemSeamlessMath.taperedOutsideDisplacement(129)
                   -RealDemSeamlessMath.taperedOutsideDisplacement(128);
        assertTrue(Math.abs(before-after)<0.01,
                   "An artificial exterior terrace kink remains");
    }

    @Test void exteriorHeightStaysWithinWorldBounds() {
        for(int x=-1500;x<=1500;x+=100)
            for(int z=-1500;z<=1500;z+=100) {
                double h=RealDemSeamlessMath.extendedHeight(x,z);
                assertTrue(Double.isFinite(h) && h>=-64 && h<=2031);
                double w=RealDemSeamlessMath.weightOutside(x,z,768);
                assertTrue(w>=0 && w<=1);
            }
    }
}
