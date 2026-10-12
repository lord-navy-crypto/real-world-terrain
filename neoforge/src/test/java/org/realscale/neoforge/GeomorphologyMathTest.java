package org.realscale.neoforge;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure deterministic codec math; vanilla server runtime is tested separately. */
public class GeomorphologyMathTest {
    @Test
    public void ridgeAndErosionHaveBoundedInfluence() {
        double base=1.25, ridgeGain=.14, erosionGain=.08;
        double low=base-erosionGain;
        double high=base+ridgeGain;
        for(double ridge=-2;ridge<=2;ridge+=0.25) {
            for(double erode=-2;erode<=2;erode+=0.25) {
                double out=GeoMath.ridgeErosion(base,ridge,erode,ridgeGain,erosionGain);
                assertTrue(Double.isFinite(out));
                assertTrue(out>=low-1e-9 && out<=high+1e-9);
            }
        }
    }
    @Test
    public void ridgeCrestLiftsTerrainMoreThanFlatSide() {
        double crest=GeoMath.ridgeErosion(0,0,-1,.12,.05);
        double flank=GeoMath.ridgeErosion(0,1,-1,.12,.05);
        assertTrue(crest>flank);
    }
    @Test
    public void strataHasNoBiasAtZeroStrengthAndBoundedAtPositiveStrength() {
        for (int y=-60;y<=960;y+=11) {
            assertEquals(0.5,GeoMath.strata(0.5,0.23,y,0,64),1e-9);
            assertTrue(Math.abs(GeoMath.strata(0.5,0.23,y,.04,64)-.5)<=.04000001);
        }
    }
}
