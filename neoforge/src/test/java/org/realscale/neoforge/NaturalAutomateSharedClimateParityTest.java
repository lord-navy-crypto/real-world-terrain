package org.realscale.neoforge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Raw-bit regression oracle for the Natural Automate hot-path refactor.
 * The reference deliberately retains the former per-family climate
 * calculations and calls each fitted kernel through its original compute()
 * entry point. The production path shares the climate gates and land gate.
 */
final class NaturalAutomateSharedClimateParityTest {
    private static TerrainArchetypeMath productionKernel() {
        return new TerrainArchetypeMath("natural_automate",
                0.92,0.38,0.07,0.72,0.36,0.30,0.50,0.50,
                1.05,0.25,12000.0);
    }

    private static TerrainArchetypeMath[] referenceKernels() {
        return new TerrainArchetypeMath[] {
            new TerrainArchetypeMath("alpine",0.920334,0.388031,0.04917,0.965848,0.353898,0.442335,0.493463,0.506537,1.018168,-3.141592653589793,18151.56),
            new TerrainArchetypeMath("glacial",0.976883,0.211697,0.029717,0.713694,0.347762,0.176078,0.502144,0.497856,0.990941,1.570796,17311.0169),
            new TerrainArchetypeMath("canyon",0.918247,0.387271,0.082729,0.363634,0.597412,0.291044,0.520377,0.479623,1.045483,-2.243785,34289.3669),
            new TerrainArchetypeMath("volcanic",0.993788,0.111131,0.005939,0.735566,0.129572,0.0,0.496722,0.503278,1.058617,-1.800358,25035.3016),
            new TerrainArchetypeMath("karst",0.860842,0.497984,0.104702,0.263639,0.30154,0.19569,0.483278,0.516722,1.095601,1.863521,7936.9415),
            new TerrainArchetypeMath("dune",0.972392,0.222451,0.070499,0.147684,0.211133,0.52343,0.47657,1.160985,1.570796,4775.6305)
        };
    }

    private static double reference(double original,double continent,
            double rawSignal,double rawRegion,double rawTemperature,
            double rawHumidity,int x,int z,TerrainArchetypeMath[] kernels) {
        double landGate=smoothstep(-0.10,0.22,continent);
        if(landGate<=0.0)return original;

        double signal=unitNoise(rawSignal);
        double region=unitNoise(rawRegion);
        double temperature=unitNoise(rawTemperature);
        double humidity=unitNoise(rawHumidity);

        // Former implementation: each family recomputed the same four
        // climate factors inside naturalFamilySuitabilityNormalized().
        double s0=TerrainArchetypeMath.naturalFamilySuitability(
                0,original,temperature,humidity,region);
        double s1=TerrainArchetypeMath.naturalFamilySuitability(
                1,original,temperature,humidity,region);
        double s2=TerrainArchetypeMath.naturalFamilySuitability(
                2,original,temperature,humidity,region);
        double s3=TerrainArchetypeMath.naturalFamilySuitability(
                3,original,temperature,humidity,region);
        double s4=TerrainArchetypeMath.naturalFamilySuitability(
                4,original,temperature,humidity,region);
        double s5=TerrainArchetypeMath.naturalFamilySuitability(
                5,original,temperature,humidity,region);
        double total=s0+s1+s2+s3+s4+s5;
        if(total<=1.0e-12)return original;

        double maxSuitability=Math.max(Math.max(Math.max(s0,s1),
                Math.max(s2,s3)),Math.max(s4,s5));
        double coverage=smoothstep(0.22,0.58,maxSuitability);
        if(coverage<=0.0)return original;

        double weightedDelta=0.0;
        if(s0>0.01)weightedDelta+=(kernels[0].compute(
                original,continent,signal,x,z)-original)*(s0/total);
        if(s1>0.01)weightedDelta+=(kernels[1].compute(
                original,continent,signal,x,z)-original)*(s1/total);
        if(s2>0.01)weightedDelta+=(kernels[2].compute(
                original,continent,signal,x,z)-original)*(s2/total);
        if(s3>0.01)weightedDelta+=(kernels[3].compute(
                original,continent,signal,x,z)-original)*(s3/total);
        if(s4>0.01)weightedDelta+=(kernels[4].compute(
                original,continent,signal,x,z)-original)*(s4/total);
        if(s5>0.01)weightedDelta+=(kernels[5].compute(
                original,continent,signal,x,z)-original)*(s5/total);
        return original+weightedDelta;
    }

    private static double smoothstep(double edge0,double edge1,double value) {
        if(edge0==edge1)return value<edge0?0.0:1.0;
        double t=Math.max(0.0,Math.min(1.0,(value-edge0)/(edge1-edge0)));
        return t*t*(3.0-2.0*t);
    }

    private static double unitNoise(double value) {
        return Math.max(-1.0,Math.min(1.0,value));
    }

    @Test
    void sharedClimateAndLandGatePathIsRawBitIdenticalAcrossWorldCoordinates() {
        TerrainArchetypeMath production=productionKernel();
        TerrainArchetypeMath[] referenceKernels=referenceKernels();

        for(int i=0;i<12000;i++) {
            int x=Math.floorMod(i*7919,131071)-65535;
            int z=Math.floorMod(i*104729,131101)-65550;
            double original=-0.35+2.1*(0.5+0.5*Math.sin(i*0.031));
            double continent=-0.35+1.55*(0.5+0.5*Math.sin(i*0.017+0.4));
            double signal=1.35*Math.sin(i*0.113);
            double region=1.40*Math.sin(i*0.0071+0.8);
            double temperature=1.25*Math.sin(i*0.019+1.2);
            double humidity=1.30*Math.cos(i*0.023-0.3);

            double expected=reference(original,continent,signal,region,
                    temperature,humidity,x,z,referenceKernels);
            double actual=production.compute(original,continent,signal,region,
                    temperature,humidity,x,z);
            assertEquals(Double.doubleToRawLongBits(expected),
                    Double.doubleToRawLongBits(actual),
                    "Natural Automate raw-bit mismatch at sample "+i
                            +" world column "+x+"/"+z);
        }
    }
}
