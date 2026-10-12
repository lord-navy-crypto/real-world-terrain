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
    public void valleyWarmingIsBoundedAndOnlyAffectsInlandDepressions(){
        double baseline=.10;
        double deep=GeoMath.valleyTemperature(baseline,-.2,.4,.8,90,.35);
        assertTrue(deep>baseline);
        assertTrue(deep<=baseline+.35+1e-10);
        double high=GeoMath.valleyTemperature(baseline,-.2,.4,.8,300,.35);
        assertEquals(baseline,high,1e-10);
        double ridge=GeoMath.valleyTemperature(baseline,.6,.3,.8,90,.35);
        assertEquals(baseline,ridge,1e-10);
        double ocean=GeoMath.valleyTemperature(baseline,-.2,.4,-.9,90,.35);
        assertEquals(baseline,ocean,1e-10);
        double off=GeoMath.valleyTemperature(baseline,-.2,.4,.8,90,0);
        assertEquals(baseline,off,1e-10);
    }

    @Test
    public void latitudeCyclesThroughBothHemispheresAtEarthScale(){
        int q=10_001_966;
        assertEquals(0,GeoMath.latitudeDeg(0,0,q),1e-9);
        assertEquals(90,GeoMath.latitudeDeg(-q,0,q),1e-9);
        assertEquals(0,GeoMath.latitudeDeg(-2*q,0,q),1e-9);
        assertEquals(-90,GeoMath.latitudeDeg(-3*q,0,q),1e-9);
        assertEquals(0,GeoMath.latitudeDeg(-4*q,0,q),1e-9);
        assertEquals(-90,GeoMath.latitudeDeg(q,0,q),1e-9);
        assertEquals(90,GeoMath.latitudeDeg(3*q,0,q),1e-9);
        assertEquals(0,GeoMath.latitudeDeg(1000,1000,1000),1e-9);
        for(int offset=-3_000_000;offset<3_000_000;offset+=145_621){
            double lat=GeoMath.latitudeDeg(offset,0,1000);
            assertTrue(lat<=90 && lat>=-90);
        }
    }
    @Test
    public void latitudeTemperatureWarmsEquatorAndCoolsPole(){
        double equator=GeoMath.latitudeTemperature(.0,0,.7);
        double pole=GeoMath.latitudeTemperature(.0,90,.7);
        assertTrue(equator>0);
        assertTrue(pole<0);
        assertEquals(0,GeoMath.latitudeTemperature(0,90,0),1e-9);
        assertEquals(GeoMath.latitudeTemperature(0,35,.7),
                     GeoMath.latitudeTemperature(0,-35,.7),1e-9);
    }
    @Test
    public void basinWarmingRequiresClosedInlandRim(){
        double[] allHigh={.6,.7,.5,.65,.7,.8,.7,.6};
        double[] open={.6,.7,-.2,.65,.7,.8,.7,.6};
        assertTrue(GeoMath.basinTemperature(.1,.1,allHigh,.6,590,.28)>.1);
        assertEquals(.1,GeoMath.basinTemperature(.1,.1,open,.6,590,.28),1e-9);
        assertEquals(.1,GeoMath.basinTemperature(.1,.1,allHigh,-.6,590,.28),1e-9);
        assertEquals(.1,GeoMath.basinTemperature(.1,.1,allHigh,.6,-20,.28),1e-9);
        assertEquals(.1,GeoMath.basinTemperature(.1,.1,allHigh,.6,590,0),1e-9);
    }
    @Test
    public void strataHasNoBiasAtZeroStrengthAndBoundedAtPositiveStrength() {
        for (int y=-60;y<=960;y+=11) {
            assertEquals(0.5,GeoMath.strata(0.5,0.23,y,0,64),1e-9);
            assertTrue(Math.abs(GeoMath.strata(0.5,0.23,y,.04,64)-.5)<=.04000001);
        }
    }
}
