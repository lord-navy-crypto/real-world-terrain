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
        assertEquals(0.0,RealDemSeamlessMath.weightOutside(2288,0,2048),1e-12);
        assertEquals(0.0,RealDemSeamlessMath.weightOutside(0,-2288,2048),1e-12);
        assertEquals(1.0,RealDemSeamlessMath.weightOutside(240,0,2048),1e-12);
        assertTrue(RealDemSeamlessMath.weightOutside(1008,0,2048)>0.5);
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

    @Test void cachedHeightIsIdenticalForEverySampleAndRepeatedVerticalUse() {
        for(int x=-2464;x<=2464;x+=37)
            for(int z=-2464;z<=2464;z+=43) {
                double expected=RealDemSeamlessMath.extendedHeight(x,z);
                assertEquals(expected,RealDemSeamlessMath.cachedExtendedHeight(x,z),0.0);
                assertEquals(expected,RealDemSeamlessMath.cachedExtendedHeight(x,z),0.0);
            }
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
    @Test void boundedThreadLocalDEMCacheIsBitExactAcrossEdgesAndNegativeCoords() {
        for(int z=-2400;z<=2400;z+=137)
            for(int x=-2400;x<=2400;x+=109) {
                double a=RealDemSeamlessMath.extendedHeight(x,z);
                double b=RealDemSeamlessMath.cachedExtendedHeight(x,z);
                double c=RealDemSeamlessMath.cachedExtendedHeight(x,z);
                assertEquals(a,b,0.0,"Cache changed source height at "+x+","+z);
                assertEquals(b,c,0.0,"Repeated X/Z queries must be stable");
            }
        java.util.stream.IntStream.range(0,8).parallel().forEach(thread -> {
            int x=thread*257-512;
            int z=thread*313-2048;
            for(int j=0;j<1000;j++) {
                int px=x+j%31;
                int pz=z+j%23;
                assertEquals(RealDemSeamlessMath.extendedHeight(px,pz),
                             RealDemSeamlessMath.cachedExtendedHeight(px,pz),0.0,
                             "Parallel worldgen must share no mutable DEM cache state");
            }
        });
    }

    @Test void wideExteriorTransitionCannotCreateWorldFloorWallFromDemHighlands() {
        // The pilot tile is >1km above procedural land. Blending it
        // to a lowland profile in 96/768m made pathological walls.
        // Evaluate each axis independently with an intentionally low
        // hypothetical baseline and ensure gentle outward gradients.
        for(double baselineY : new double[]{-32,63,400}) {
            for(int axis=0;axis<2;axis++)
                for(int direction : new int[]{-1,1}) {
                    double previous=Double.NaN;
                    for(int d=0;d<=2080;d++) {
                        double x=axis==0 ? direction*(240+d) : 0;
                        double z=axis==1 ? direction*(240+d) : 0;
                        double weight=RealDemSeamlessMath.weightOutside(x,z,2048);
                        double blended=weight*RealDemSeamlessMath.extendedHeight(x,z)
                                        +(1-weight)*baselineY;
                        if(!Double.isNaN(previous)) {
                            assertTrue(Math.abs(blended-previous)<5.0,
                                       "Steep constructed DEM exterior wall at "
                                       +x+","+z+" delta="+(blended-previous));
                        }
                        previous=blended;
                    }
                    assertEquals(baselineY,previous,1.0e-6);
                }
        }
    }


    @Test void cachedFadeWeightIsBitExactAcrossBordersAndFarField() {
        for (int fade : new int[]{256, 768, 2048, 4096}) {
            // Multiple directions, both coordinate signs, corners and far field.
            for (int x = -4608; x <= 4608; x += 83) {
                for (int z = -4608; z <= 4608; z += 97) {
                    assertFadeWeightBits(x, z, fade);
                    assertFadeWeightBits(x, z, fade); // warm cache hit
                }
            }
            for (int x : new int[]{-240-fade, -241, -240, -239, 0, 239, 240,
                    241, 240+fade, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
                for (int z : new int[]{-240-fade, -241, -240, -239, 0, 239, 240,
                        241, 240+fade, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
                    assertFadeWeightBits(x, z, fade);
                }
            }
        }
    }

    @Test void cachedFadeWeightIsBitExactAcrossWorkerThreads() {
        java.util.stream.IntStream.range(0, 8).parallel().forEach(worker -> {
            for (int i = 0; i < 3000; i++) {
                int x = ((worker * 131 + i * 53) % 8400) - 4200;
                int z = ((worker * 197 + i * 71) % 8400) - 4200;
                int fade = (i & 1) == 0 ? 768 : 2048;
                assertFadeWeightBits(x, z, fade);
                assertFadeWeightBits(x, z, fade);
            }
        });
    }

    private static void assertFadeWeightBits(int x, int z, int fade) {
        double expected = RealDemSeamlessMath.weightOutside(x, z, fade);
        double actual = RealDemSeamlessMath.cachedWeightOutside(x, z, fade);
        assertEquals(Double.doubleToRawLongBits(expected),
                     Double.doubleToRawLongBits(actual),
                     "Fade weight changed at " + x + "," + z + " (width=" + fade + ")");
    }
}
