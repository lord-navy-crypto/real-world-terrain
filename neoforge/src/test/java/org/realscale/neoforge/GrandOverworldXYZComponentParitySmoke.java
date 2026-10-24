package org.realscale.neoforge;

import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test.33: strict baseline X/Z-only 47-bit candidate mask versus the
 * intersection of that mask and Test.31's conservative warped-Y envelope.
 * ALL strength calculations use unchanged original Hall/Tube double math.
 *
 * Count work avoided (eligible component bit checks), not Minecraft TPS.
 */
public final class GrandOverworldXYZComponentParitySmoke {
    private GrandOverworldXYZComponentParitySmoke() {}
    private static final Random RANDOM=new Random(0x33261024L);
    private static final double[] WARP={-1.0,-0.73,0.0,0.43,1.0};
    private static long samples,fullChecks,partial,empty,edge,positive,
            bitsBefore,bitsAfter,carved,workerChecks;
    private static volatile long timingSink;

    private static void yes(boolean ok,String message) {
        if(!ok)throw new AssertionError(message);
    }
    private static long raw(double value) {
        return Double.doubleToRawLongBits(value);
    }

    private static void check(int x,int y,int z,boolean proveDensity) {
        final long horizontal=y>GrandOverworldCaveNetwork.MAX_GEOMETRY_Y+2
                ? 0L : GrandOverworldCaveNetwork.componentMaskForColumn(x,z);
        final long xyz=GrandOverworldCaveNetwork.componentMaskForColumnAtHeight(
                x,y,z);
        samples++;
        if(horizontal==-1L) {
            edge++;
            yes(xyz==-1L,"Border may select adjacent 768-block cave graph");
        } else {
            yes((xyz&~horizontal)==0L,
                    "XYZ mask introduced a cave not in horizontal candidates");
            if(horizontal==0L) {
                empty++;
                yes(xyz==0L,"Empty X/Z source column not rejected");
            } else {
                positive++;
                if(xyz==0L)empty++;
                else if(xyz!=horizontal)partial++;
                bitsBefore+=Long.bitCount(horizontal);
                bitsAfter+=Long.bitCount(xyz);
            }
        }
        if(!proveDensity || y<GrandOverworldCaveNetwork.MIN_Y
                || y>GrandOverworldCaveNetwork.MAX_GEOMETRY_Y+2)return;
        for(double shift:WARP){
            double wx=x+shift*6.0,wy=y+shift*2.0,
                    wz=z-shift*5.0;
            double old=GrandOverworldCaveNetwork.strengthFromColumnWithMask(
                    x,z,wx,wy,wz,horizontal);
            double reduced=GrandOverworldCaveNetwork.strengthFromColumnWithMask(
                    x,z,wx,wy,wz,xyz);
            double original=GrandOverworldCaveNetwork.strength(wx,wy,wz);
            yes(raw(old)==raw(reduced),
                    "Reduced XYZ mask changed frozen horizontal-mask density"
                    +" at "+x+","+y+","+z+" shift="+shift
                    +" old="+old+" reduced="+reduced
                    +" candidateBits="+Long.toBinaryString(xyz));
            yes(raw(original)==raw(reduced),
                    "Reduced XYZ mask differs from complete unmasked cave"
                    +" at "+x+","+y+","+z+" shift="+shift);
            if(xyz==0L)yes(raw(reduced)==raw(0.0),
                    "A pruned giant-cave voxel must have zero strength");
            if(original>0)carved++;
            fullChecks++;
        }
    }

    public static void main(String[] args)throws Exception {
        int[] heights={-40,0,80,135,195,250,395,510,630,790,995,1150,
                1330,1430,1550,1590,1660,1698,1700,1702,1703};
        for(int i=0;i<1350;i++){
            final int x,z;
            if(i%11==0){
                x=(RANDOM.nextInt(41)-20)*768+(i%2==0?2:765);
                z=RANDOM.nextInt(20001)-10000;
            }else if(i%11==1){
                x=RANDOM.nextInt(20001)-10000;
                z=(RANDOM.nextInt(41)-20)*768+(i%2==0?4:765);
            }else if(i%11==2){
                x=29_997_000-RANDOM.nextInt(1200);
                z=-29_997_000+RANDOM.nextInt(1200);
            }else{
                x=RANDOM.nextInt(22001)-11000;
                z=RANDOM.nextInt(22001)-11000;
            }
            for(int y:heights)
                check(x,y,z,i%16==0);
        }
        // Sample hall centers in all four cave tiers, exact real Y bands,
        // plus empty heights above/below their true vertical influence.
        for(int gx=-4;gx<=4;gx++)for(int gz=-4;gz<=4;gz++)
            for(int layer=0;layer<GrandOverworldCaveNetwork.LAYERS;layer++){
                var hall=GrandOverworldCaveNetwork.hall(gx,gz,layer);
                int x=(int)hall.x(),z=(int)hall.z();
                int center=(int)hall.y();
                for(int y=-40;y<=1702;y+=125)
                    check(x,y,z,true);
                check(x,center,z,true);
            }
        yes(samples>30_000 && fullChecks>18_000 && partial>500
                        && positive>2000 && edge>400 && carved>200
                        && bitsBefore>bitsAfter && empty>1500,
            "Coverage or actual candidate-bit reduction missing. samples="
                +samples+" baseline_bits="+bitsBefore+" xyz_bits="+bitsAfter
                +" partial="+partial+" carved="+carved+" full="+fullChecks);
        System.out.println("OVERWORLD_47BIT_XYZ_MASK_FULL_STRENGTH_PARITY_PASS"
                +" coords="+samples+" independent_original_strength="+fullChecks
                +" same_horizontal_bit_probes="+bitsBefore
                +" new_xyz_bit_probes="+bitsAfter
                +" partial_masks="+partial+" border_fallback="+edge
                +" actually_carved="+carved
                +" componentWorkReductionOnly=true");

        // The new worker-local Y table and X/Z column mask must not bleed
        // across threads, regions or cache evictions.
        AtomicReference<Throwable> failure=new AtomicReference<>();
        Thread[] workers=new Thread[4];
        for(int worker=0;worker<workers.length;worker++){
            final int id=worker;
            workers[worker]=new Thread(()->{
                try{
                    Random r=new Random(0x3300+id);
                    for(int n=0;n<2400;n++){
                        int x=r.nextInt(16001)-8000;
                        int z=r.nextInt(16001)-8000;
                        int y=r.nextInt(1743)-40;
                        long h=GrandOverworldCaveNetwork.componentMaskForColumn(x,z);
                        long mask=GrandOverworldCaveNetwork.componentMaskForColumnAtHeight(
                                x,y,z);
                        if(h==-1L)yes(mask==-1L,"Worker border fallback lost");
                        else yes((mask&~h)==0L,"Worker added foreign component");
                        workerChecks++;
                    }
                }catch(Throwable t){failure.compareAndSet(null,t);}
            },"overworld-xyz-parity-"+worker);
            workers[worker].start();
        }
        for(Thread worker:workers)worker.join();
        if(failure.get()!=null)
            throw new AssertionError("Overworld 47-bit XYZ graph isolation",failure.get());
    }
}
