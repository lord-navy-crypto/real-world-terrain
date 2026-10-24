package org.realscale.neoforge;

import java.util.Random;

/**
 * Test.27: prove the new vertical pre-warp reject is conservative.
 * When it rejects a positive-mask source column, the original FULL cave
 * evaluator must be exactly zero for every tested seed-warp displacement.
 * No tolerance-based cave carving differences are permitted.
 */
public final class GrandNetherWarpHeightPruneSmoke {
    private GrandNetherWarpHeightPruneSmoke(){}
    private static long total,activeMask,prunedActive,reachedActive,borders,
            provedWarpSamples,positiveFull;
    private static final double[] SHIFTS={-1.0,-0.875,-0.5,-0.125,0,0.125,0.5,0.875,1.0};

    private static void yes(boolean ok,String reason){
        if(!ok)throw new AssertionError(reason);
    }
    private static void at(int x,int y,int z){
        int mask=GrandNetherCaveNetwork.componentMaskForColumn(x,z);
        boolean may=GrandNetherCaveNetwork.mayContainVoidAtHeight(x,y,z,mask);
        total++;
        if(mask==-1) {
            borders++;
            yes(may,"Border must not prune displaced neighbor graph");
        }
        if(mask==0)yes(!may,"Empty mask must reject every height");
        if(mask>0) {
            activeMask++;
            if(may)reachedActive++;
            else prunedActive++;
        }
        // Critical proof on positive-mask *and* false vertical gate.
        // Other cases retain the identical original density evaluator.
        if(!may && mask>0){
            for(double shift:SHIFTS){
                double wx=x+shift*5.0;
                double wy=y+shift*2.0;
                double wz=z-shift*4.0;
                double original=GrandNetherCaveNetwork.strength(wx,wy,wz);
                double masked=GrandNetherCaveNetwork.strengthFromColumnWithMask(
                        x,z,wx,wy,wz,mask);
                yes(Double.doubleToRawLongBits(original)==
                                Double.doubleToRawLongBits(0.0),
                        "Unsafe Y prune deleted Nether cave at "
                        +x+","+y+","+z+" warp="+shift+" density="+original);
                yes(Double.doubleToRawLongBits(masked)==
                                Double.doubleToRawLongBits(original),
                        "Original 19-component column mask differs from full cave");
                provedWarpSamples++;
            }
        } else if(mask>0 && may && (total&15)==0){
            // Include true gates: ordinary cave strength remains unchanged.
            double cave=GrandNetherCaveNetwork.strength(x,y,z);
            double masked=GrandNetherCaveNetwork.strengthFromColumnWithMask(
                    x,z,x,y,z,mask);
            yes(Double.doubleToRawLongBits(cave)==
                            Double.doubleToRawLongBits(masked),
                    "Active altitude changed Nether cave density");
            if(cave>0)positiveFull++;
        }
    }
    public static void main(String[] args){
        Random random=new Random(0x20261027L);
        // Signed and far coordinates, regular chunk probes, and the exact
        // 256-block cell edge where a noise displacement can cross region.
        for(int i=0;i<1250;i++){
            int x,z;
            if(i%13==0){
                int gx=random.nextInt(41)-20;
                x=gx*256+(i%2==0?2:253);
                z=random.nextInt(12001)-6000;
            }else if(i%13==1){
                x=random.nextInt(12001)-6000;
                int gz=random.nextInt(41)-20;
                z=gz*256+(i%2==0?2:253);
            }else if(i%13==2){
                x=29_999_000-random.nextInt(6000);
                z=-29_999_000+random.nextInt(6000);
            }else{
                x=random.nextInt(16001)-8000;
                z=random.nextInt(16001)-8000;
            }
            for(int y=144;y<=440;y+=12)at(x,y,z);
        }
        // Deliberately cave-rich cores of BOTH cavern layers. Also sample
        // the full altitude spread to find safe empty heights above/below.
        for(int gx=-4;gx<=4;gx++)for(int gz=-4;gz<=4;gz++)
            for(int layer=0;layer<2;layer++){
                var hall=GrandNetherCaveNetwork.hall(gx,gz,layer);
                int x=(int)hall.x(),z=(int)hall.z();
                for(int y=144;y<=440;y+=7)at(x,y,z);
                at(x,(int)hall.y(),z);
            }
        // The edge mask always preserves full evaluation; no false negative.
        for(int gx=-6;gx<=6;gx++)for(int gz=-6;gz<=6;gz++)
            for(int offset:new int[]{0,1,2,3,252,253,254,255}){
                at(gx*256+offset,205,gz*256+127);
                at(gx*256+127,348,gz*256+offset);
            }
        yes(total>35_000 && activeMask>2500
                    && prunedActive>1000 && reachedActive>1000
                    && borders>500 && provedWarpSamples>9000,
            "Insufficient altitude reach proof: total="+total
            +" active="+activeMask+" safeSkip="+prunedActive
            +" potentiallyCarved="+reachedActive+" borders="+borders
            +" validWarpChecks="+provedWarpSamples);
        System.out.println("NETHER_PRE_WARP_HEIGHT_REJECT_EXACT_PASS:"
                +" columnsHeights="+total+" activeMask="+activeMask
                +" safelyPruned="+prunedActive+" potential="+reachedActive
                +" borderFallback="+borders+" zeroStrengthProofs="+provedWarpSamples
                +" trueGatePositiveChecks="+positiveFull);
    }
}
