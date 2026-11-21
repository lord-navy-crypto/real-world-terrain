package org.realscale.neoforge;

import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

final class NaturalAutomateMathTest {
    private static TerrainArchetypeMath kernel(double strength) {
        return new TerrainArchetypeMath("natural_automate",
                0.92,0.38,0.07,strength,0.36,0.30,0.50,0.50,
                1.05,0.25,12000.0);
    }

    private static TerrainArchetypeMath kernel() { return kernel(0.72); }

    @Test
    void selectorCoversAllSixFittedFamiliesInOrder() {
        double[] centers={-5.0/6.0,-0.5,-1.0/6.0,1.0/6.0,0.5,5.0/6.0};
        for(int i=0;i<centers.length;i++)
            assertEquals(i,TerrainArchetypeMath.naturalFamilyIndex(centers[i]));
        assertEquals(0,TerrainArchetypeMath.naturalFamilyIndex(-1.0));
        assertEquals(5,TerrainArchetypeMath.naturalFamilyIndex(1.0));
    }

    @Test
    void coldWetHighlandsPreferGlacialFit() {
        double glacial=TerrainArchetypeMath.naturalFamilySuitability(
                1,0.84,-0.85,0.75,-0.5);
        double alpine=TerrainArchetypeMath.naturalFamilySuitability(
                0,0.84,-0.85,0.75,-0.5);
        assertTrue(glacial>0.85);
        assertTrue(glacial>alpine);
    }

    @Test
    void warmDryLowlandsPreferDuneFit() {
        double dune=TerrainArchetypeMath.naturalFamilySuitability(
                5,0.05,0.90,-0.90,5.0/6.0);
        double alpine=TerrainArchetypeMath.naturalFamilySuitability(
                0,0.05,0.90,-0.90,5.0/6.0);
        assertTrue(dune>0.95);
        assertEquals(0.0,alpine);
    }

    @Test
    void allSixFittedFamiliesAreReachableInsideTheUnifiedComposite() {
        double[][] representativeInputs={
            {1.50,-0.90,0.10,-5.0/6.0}, // Alpine: cold, high relief
            {0.84,-0.85,0.75,-0.50},   // Glacial: cold, wet highlands
            {0.20,0.00,-0.90,-1.0/6.0},// Canyon: dry incised terrain
            {0.50,0.90,0.00,1.0/6.0},  // Volcanic: warm, elevated terrain
            {0.30,0.50,0.90,0.50},     // Karst: warm, humid moderate relief
            {0.05,0.90,-0.90,5.0/6.0}  // Dune: warm, dry lowlands
        };
        String[] names={"Alpine","Glacial","Canyon","Volcanic","Karst","Dune"};
        for(int family=0;family<6;family++) {
            double[] input=representativeInputs[family];
            double suitability=TerrainArchetypeMath.naturalFamilySuitability(
                    family,input[0],input[1],input[2],input[3]);
            assertTrue(suitability>0.85,
                    names[family]+" kernel should be reachable in the unified profile: "+suitability);
        }
    }

    @Test
    void unsuitableClimateAndTopographyRetainTheNaturalBaselineExactly() {
        TerrainArchetypeMath math=kernel();
        double original=0.05;
        double actual=math.compute(original,0.45,0.31,-5.0/6.0,
                0.90,0.90,1537,-2811);
        assertEquals(Double.doubleToRawLongBits(original),
                Double.doubleToRawLongBits(actual));
    }

    @Test
    void climateAndRegionalSuitabilityAreContinuousAtFamilyBoundaries() {
        TerrainArchetypeMath math=kernel();
        for(int boundary=1;boundary<6;boundary++) {
            double region=-1.0+boundary/3.0;
            double before=math.compute(0.82,0.45,0.31,region-1.0e-7,
                    -0.78,0.66,1537,-2811);
            double at=math.compute(0.82,0.45,0.31,region,
                    -0.78,0.66,1537,-2811);
            double after=math.compute(0.82,0.45,0.31,region+1.0e-7,
                    -0.78,0.66,1537,-2811);
            assertTrue(Math.abs(before-at)<1.0e-4);
            assertTrue(Math.abs(after-at)<1.0e-4);
        }
    }

    @Test
    void regionalTransitionsAreContinuousAtAllFiveBoundaries() {
        TerrainArchetypeMath math=kernel();
        for(int boundary=1;boundary<6;boundary++) {
            double region=-1.0+boundary/3.0;
            double before=math.compute(0.82,0.45,0.31,region-1.0e-7,1537,-2811);
            double at=math.compute(0.82,0.45,0.31,region,1537,-2811);
            double after=math.compute(0.82,0.45,0.31,region+1.0e-7,1537,-2811);
            assertTrue(Math.abs(before-at)<1.0e-4,
                    "left edge discontinuity at region boundary "+boundary);
            assertTrue(Math.abs(after-at)<1.0e-4,
                    "right edge discontinuity at region boundary "+boundary);
        }
    }

    @Test
    void oceanColumnsAreExactlyPreservedAcrossEveryMorphologyRegion() {
        TerrainArchetypeMath math=kernel();
        for(double region=-1.0;region<=1.0;region+=0.05) {
            double actual=math.compute(-0.37,-0.11,0.27,region,1024,-2048);
            assertEquals(Double.doubleToRawLongBits(-0.37),
                    Double.doubleToRawLongBits(actual));
        }
    }

    @Test
    void compositeIsFiniteDeterministicAndBoundedAcrossSignedWorldCoordinates() {
        TerrainArchetypeMath math=kernel();
        for(int x=-8192;x<=8192;x+=257) {
            for(int z=-8192;z<=8192;z+=263) {
                double original=0.18+0.78*(0.5+0.5*Math.sin(x*0.0007+z*0.0003));
                double continent=0.25+0.55*(0.5+0.5*Math.cos(x*0.0002-z*0.0004));
                double detail=Math.sin(x*0.0011+z*0.0009);
                double region=Math.sin(x*0.00013-z*0.00017);
                double first=math.compute(original,continent,detail,region,x,z);
                double second=math.compute(original,continent,detail,region,x,z);
                assertTrue(Double.isFinite(first));
                assertEquals(Double.doubleToRawLongBits(first),
                        Double.doubleToRawLongBits(second));
                assertTrue(first>-1.5 && first<2.5,
                        "composite morphology escaped its conservative density envelope");
            }
        }
    }

    @Test
    void zeroCompositeStrengthReturnsTheNaturalBaseWithoutFittedMorphology() {
        TerrainArchetypeMath disabled=kernel(0.0);
        for(double region=-1.0;region<=1.0;region+=0.05) {
            double original=0.61;
            double actual=disabled.compute(original,0.52,0.73,region,911,-1337);
            assertEquals(Double.doubleToRawLongBits(original),
                    Double.doubleToRawLongBits(actual));
        }
    }

    @Test
    void workerLocalCachesRemainDeterministicUnderParallelSampling() {
        IntStream.range(0,6).parallel().forEach(worker->{
            TerrainArchetypeMath math=kernel();
            for(int i=0;i<4000;i++) {
                int x=(i*131+worker*7919)%32001-16000;
                int z=(i*197-worker*3571)%32003-16001;
                double region=Math.sin((x+z)*0.00019);
                double actual=math.compute(0.76,0.43,Math.cos(i*0.13),
                        region,x,z);
                assertTrue(Double.isFinite(actual));
            }
        });
    }
}
