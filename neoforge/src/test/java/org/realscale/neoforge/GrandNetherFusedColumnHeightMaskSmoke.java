package org.realscale.neoforge;

import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Independent original Test.29 two-step column+Y gate versus the Test.30
 * single-region-lookup gate. Retains strict raw double density fallback
 * and source region-edge semantics; no approximate density comparisons.
 */
public final class GrandNetherFusedColumnHeightMaskSmoke {
    private GrandNetherFusedColumnHeightMaskSmoke() {}

    private static long checked,oldZero,oldPositive,oldBoundary,verticalPruned,
            fallbackCompared;
    private static volatile int timingSink;
    private static final int[] HEIGHTS={-40,120,143,144,160,190,205,240,280,
            310,348,399,420,440,441,490};
    private static final double[] WARP={-1.0,-0.71,0.0,0.39,1.0};

    private static void require(boolean ok,String why) {
        if(!ok)throw new AssertionError(why);
    }
    private static long raw(double d){return Double.doubleToRawLongBits(d);}

    // Exact old Test.29 production logic: first compute the source X/Z mask,
    // then independently lookup the height table for that same cave cell.
    private static int oldTwoStepMask(int x,int y,int z) {
        int candidate=GrandNetherCaveNetwork.componentMaskForColumn(x,z);
        if(candidate==0)return 0;
        if(!GrandNetherCaveNetwork.mayContainVoidAtHeight(x,y,z,candidate))
            return 0;
        return candidate;
    }

    private static void probe(int x,int y,int z,boolean compareCave) {
        int horizontal=GrandNetherCaveNetwork.componentMaskForColumn(x,z);
        int baseline=oldTwoStepMask(x,y,z);
        int candidate=GrandNetherCaveNetwork.componentMaskForColumnAtHeight(x,y,z);
        require(baseline==candidate,
                "Original split gate versus fused gate differs at "
                +x+","+y+","+z+" horizontal="+horizontal
                +" old="+baseline+" new="+candidate);
        checked++;
        if(horizontal==0)oldZero++;
        else if(horizontal==-1)oldBoundary++;
        else {
            oldPositive++;
            if(candidate==0)verticalPruned++;
        }
        if(compareCave && y>=GrandNetherCaveNetwork.MIN_Y
                && y<=GrandNetherCaveNetwork.MAX_Y) {
            for(double displacement:WARP) {
                double wx=x+displacement*5.0;
                double wy=y+displacement*2.0;
                double wz=z-displacement*4.0;
                double full=GrandNetherCaveNetwork.strength(wx,wy,wz);
                if(candidate==0)
                    require(raw(full)==raw(0.0),
                            "Fused zero candidate omitted real full cave at "
                            +x+","+y+","+z+" shift="+displacement);
                else {
                    double optimized=
                            GrandNetherCaveNetwork.strengthFromColumnWithMask(
                                    x,z,wx,wy,wz,candidate);
                    require(raw(optimized)==raw(full),
                            "Fused masked cave strength differs from frozen"
                            +" full graph at "+x+","+y+","+z);
                }
                fallbackCompared++;
            }
        }
    }

    public static void main(String[] args)throws Exception {
        Random random=new Random(0x30261024L);
        for(int i=0;i<2600;i++) {
            final int x,z;
            if(i%11==0) {
                int gx=random.nextInt(61)-30;
                x=gx*256+(i%2==0?1:253);
                z=random.nextInt(12001)-6000;
            } else if(i%11==1) {
                x=random.nextInt(12001)-6000;
                z=(random.nextInt(61)-30)*256+(i%2==0?3:254);
            } else if(i%11==2) {
                x=29_998_000-random.nextInt(1000);
                z=-29_998_000+random.nextInt(1000);
            } else if(i%11==3) {
                x=-29_998_000+random.nextInt(1000);
                z=29_998_000-random.nextInt(1000);
            } else {
                x=random.nextInt(14001)-7000;
                z=random.nextInt(14001)-7000;
            }
            for(int y:HEIGHTS)
                probe(x,y,z,(i%13)==0);
        }
        // Deliberately hit both halls and the full vertical band: sparse
        // random probes alone would mostly miss candidate cave components.
        for(int gx=-5;gx<=5;gx++)for(int gz=-5;gz<=5;gz++)
            for(int layer=0;layer<2;layer++) {
                var hall=GrandNetherCaveNetwork.hall(gx,gz,layer);
                int x=(int)hall.x(),z=(int)hall.z();
                for(int y=144;y<=440;y+=17)
                    probe(x,y,z,true);
                probe(x,(int)hall.y(),z,true);
            }
        require(checked>40_000 && oldZero>1000
                        && oldPositive>3000 && oldBoundary>500
                        && verticalPruned>1000 && fallbackCompared>12000,
                "Missing horizontal empty/nonempty, height exclusion, "
                        +"region edge or cave-strength comparison coverage: "
                        +"checked="+checked+" zero="+oldZero
                        +" positive="+oldPositive+" border="+oldBoundary
                        +" prune="+verticalPruned+" strength="+fallbackCompared);
        // Run a short informational, single-JVM/JIT warmed gate A/B.
        // This is not a seed-matched Minecraft chunk-generation benchmark.
        final int size=512;
        int[] xx=new int[size],zz=new int[size],yy=new int[size];
        for(int i=0;i<size;i++) {
            int gx=(i%8)-4,gz=((i/8)%8)-4;
            var hall=GrandNetherCaveNetwork.hall(gx,gz,i&1);
            xx[i]=(int)hall.x();zz[i]=(int)hall.z();
            yy[i]=i%3==0?144:i%3==1?205:348;
        }
        for(int warm=0;warm<12;warm++)
            for(int i=0;i<size;i++) {
                timingSink^=oldTwoStepMask(xx[i],yy[i],zz[i]);
                timingSink^=GrandNetherCaveNetwork.componentMaskForColumnAtHeight(
                        xx[i],yy[i],zz[i]);
            }
        long oldNanos=0,fusedNanos=0;
        for(int pass=0;pass<16;pass++) {
            long start=System.nanoTime();
            for(int i=0;i<size;i++)
                timingSink^=oldTwoStepMask(xx[i],yy[i],zz[i]);
            oldNanos+=System.nanoTime()-start;
            start=System.nanoTime();
            for(int i=0;i<size;i++)
                timingSink^=GrandNetherCaveNetwork.componentMaskForColumnAtHeight(
                        xx[i],yy[i],zz[i]);
            fusedNanos+=System.nanoTime()-start;
        }
        System.out.println("NETHER_FUSED_XYZ_CAVE_GATE_EXACT_PASS checked="+checked
                +" horizontalEmpty="+oldZero+" positive="+oldPositive
                +" boundary="+oldBoundary+" heightPruned="+verticalPruned
                +" exactCaveDensityProbes="+fallbackCompared
                +" split_gate_ns="+oldNanos+" fused_gate_ns="+fusedNanos
                +" gateMicrobenchmarkOnly=true");
        // Cross-worker region graphs may never share a column or Y table.
        AtomicReference<Throwable> issue=new AtomicReference<>();
        Thread[] threads=new Thread[4];
        for(int w=0;w<threads.length;w++) {
            final int worker=w;
            threads[w]=new Thread(()->{
                try {
                    for(int i=0;i<1500;i++) {
                        int x=(i*37+worker*411)%6000-3000;
                        int z=(i*71-worker*193)%6000-3000;
                        int y=144+(i*29)%297;
                        int old=oldTwoStepMask(x,y,z);
                        int fused=GrandNetherCaveNetwork.componentMaskForColumnAtHeight(
                                x,y,z);
                        require(old==fused,"Cross-thread fused mask differs");
                    }
                }catch(Throwable e){issue.compareAndSet(null,e);}
            },"nether-fused-gate-"+w);
            threads[w].start();
        }
        for(Thread worker:threads)worker.join();
        if(issue.get()!=null)
            throw new AssertionError("Fused cave gate worker isolation",issue.get());
    }
}
