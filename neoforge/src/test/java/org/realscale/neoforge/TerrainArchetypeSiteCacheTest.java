package org.realscale.neoforge;

import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Reference geometry is the original uncached fitted terrain site formula. */
final class TerrainArchetypeSiteCacheTest {
    private static TerrainArchetypeMath fitted(String family) {
        return new TerrainArchetypeMath(family,
                0.63,0.58,0.39,0.71,0.48,0.62,0.53,0.69,
                1.25,0.29,9800.0);
    }

    private static double smoothstep(double low,double high,double x){
        if(low==high)return x<low?0.0:1.0;
        double t=Math.max(0.0,Math.min(1.0,(x-low)/(high-low)));
        return t*t*(3.0-2.0*t);
    }

    private static double karstOriginal(double original,double continent,
            double rawSignal,double x,double z){
        double gate=smoothstep(-0.10,0.22,continent);
        if(gate<=0.0)return original;
        double signal=Math.max(-1.0,Math.min(1.0,rawSignal));
        int cellX=Math.floorDiv((int)x,2048),cellZ=Math.floorDiv((int)z,2048);
        long hash=MountainReliefMath.siteHash(cellX,cellZ);
        double cx=cellX*(double)2048+2048*0.5+MountainReliefMath.centerOffsetX(hash);
        double cz=cellZ*(double)2048+2048*0.5+MountainReliefMath.centerOffsetZ(hash);
        double radial=Math.hypot(x-cx,z-cz);
        double towerRadius=Math.max(80.0,Math.min(450.0,9800.0*0.025));
        double tower=1.0-smoothstep(towerRadius*0.55,towerRadius,radial);
        long sinkHash=MountainReliefMath.mix(hash^0x6a09e667f3bcc909L);
        double sx=cx+MountainReliefMath.centerOffsetX(sinkHash)*1.8;
        double sz=cz+MountainReliefMath.centerOffsetZ(sinkHash)*1.8;
        double sinkRadius=Math.max(40.0,Math.min(200.0,9800.0*0.012));
        double sink=1.0-smoothstep(sinkRadius*0.45,sinkRadius,Math.hypot(x-sx,z-sz));
        double noiseTower=Math.max(0.0,signal)*0.08*Math.max(0.12,0.63+0.5*0.39);
        return original+gate*0.71*(0.36*Math.max(0.69,0.12)*tower
                +noiseTower-0.22*Math.max(0.53,0.12)*sink);
    }

    private static double volcanoOriginal(double original,double continent,double x,double z){
        double gate=smoothstep(-0.10,0.22,continent);
        if(gate<=0.0)return original;
        int ix=(int)x,iz=(int)z;
        int cellX=Math.floorDiv(ix,2048),cellZ=Math.floorDiv(iz,2048);
        long hash=MountainReliefMath.siteHash(cellX,cellZ);
        double cx=cellX*(double)2048+2048*0.5+MountainReliefMath.centerOffsetX(hash);
        double cz=cellZ*(double)2048+2048*0.5+MountainReliefMath.centerOffsetZ(hash);
        double radius=Math.max(250.0,Math.min(800.0,9800.0*0.025));
        double radial=Math.hypot(ix-cx,iz-cz)/radius;
        if(radial>=1.0)return original;
        double shield=Math.pow(Math.max(0.0,1.0-radial),1.35);
        double caldera=0.16*Math.exp(-Math.pow(radial/0.12,2.0));
        double shape=shield-caldera;
        if(shape==0.0)return original;
        return original+gate*0.71*0.55*Math.max(0.63,0.12)*shape;
    }

    private static void checkRaw(TerrainArchetypeMath math,String family,
            double original,double continent,double signal,int x,int z){
        double expected=family.equals("karst")
                ?karstOriginal(original,continent,signal,x,z)
                :volcanoOriginal(original,continent,x,z);
        double actual=math.compute(original,continent,signal,x,z);
        assertEquals(Double.doubleToRawLongBits(expected),
                Double.doubleToRawLongBits(actual),
                "Cached fitted site diverged at "+family+" "+x+","+z+
                        " continent="+continent);
    }

    @Test void consecutiveRegionalSamplesAreBitExactAcrossSignedBorders(){
        for(String family:new String[]{"karst","volcanic"}){
            TerrainArchetypeMath math=fitted(family);
            for(int x:new int[]{-4097,-4096,-4095,-2049,-2048,-2047,-1,0,1,2047,2048,2049,4095,4096,4097}){
                for(int z:new int[]{-4097,-4096,-4095,-2049,-2048,-2047,-1,0,1,2047,2048,2049,4095,4096,4097}){
                    for(double continent:new double[]{-0.2,-0.05,0.03,0.25,0.55})
                        checkRaw(math,family,0.87,continent,0.37,x,z);
                }
            }
        }
    }

    @Test void shuffledAndMultithreadedRegionTransitionsAreBitExact(){
        IntStream.range(0,4).parallel().forEach(worker->{
            Random random=new Random(137L+worker*31);
            for(String family:new String[]{"karst","volcanic"}){
                TerrainArchetypeMath math=fitted(family);
                for(int i=0;i<9000;i++){
                    int x=random.nextInt(16384)-8192;
                    int z=random.nextInt(16384)-8192;
                    double continent=i%4==0?-0.2:0.35;
                    checkRaw(math,family,1.01,continent,0.39,x,z);
                }
            }
        });
    }
}
