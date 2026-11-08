package org.realscale.neoforge;

import java.util.concurrent.atomic.AtomicReference;

/** Generated side-vault topology is deterministic and never clips cell seams. */
public final class GrandOverworldCaveDesignV2Smoke {
    private GrandOverworldCaveDesignV2Smoke() {}
    private static void require(boolean value,String why) {
        if(!value)throw new AssertionError(why);
    }
    public static void main(String[] args) {
        int annexes=0,expanded=0,positiveLoops=0,negativeColumns=0;
        for(int gx=-4;gx<=4;gx++) for(int gz=-4;gz<=4;gz++)
            for(int layer=0;layer<GrandOverworldCaveNetwork.LAYERS;layer++) {
                require(GrandOverworldCaveDesignV2.geometryFitsInsideCell(
                        gx,gz,layer),"Extra cave would cross a 768-cell seam: "
                        +gx+","+gz+","+layer);
                var room=GrandOverworldCaveDesignV2.annexAt(gx,gz,layer);
                double v=GrandOverworldCaveDesignV2.strength(
                        room.x(),room.y(),room.z());
                require(v>=0.95,"Detached annex is not carved: "
                        +gx+","+gz+","+layer);
                annexes++;
                double existing=GrandOverworldCaveNetwork.strength(
                        room.x(),room.y(),room.z());
                if(v>existing+0.05)expanded++;
                var a=GrandOverworldCaveNetwork.satellite(gx,gz,layer,0);
                var b=GrandOverworldCaveNetwork.satellite(gx,gz,layer,1);
                // Along the old satellite pair, a new loop must carve a
                // corridor. The centerline bends, so probe a bounded
                // neighborhood at each tenth of the direct segment.
                for(int step=1;step<10;step++){
                    double t=step/10.0;
                    double x=a.x()+(b.x()-a.x())*t;
                    double y=a.y()+(b.y()-a.y())*t;
                    double z=a.z()+(b.z()-a.z())*t;
                    double best=0;
                    for(int dx=-16;dx<=16;dx+=8)
                        for(int dy=-16;dy<=16;dy+=8)
                            for(int dz=-16;dz<=16;dz+=8)
                                best=Math.max(best,GrandOverworldCaveDesignV2.strength(
                                        x+dx,y+dy,z+dz));
                    require(best>0.02,"Loop passage disconnected: "
                            +gx+","+gz+","+layer+" t="+t);
                    positiveLoops++;
                }
            }
        require(expanded>=50,"Additional annexes do not meaningfully extend old caves");
        for(int gx=-3;gx<=3;gx++)for(int gz=-3;gz<=3;gz++)
            for(int off:new int[]{0,1,8,16,23,744,752,767})
                for(int y:new int[]{135,395,790,1330}) {
                    double x=(long)gx*768+off,z=(long)gz*768+2;
                    require(GrandOverworldCaveDesignV2.strength(x,y,z)==0,
                            "V2 features leaked across cell boundaries");
                    require(!GrandOverworldCaveDesignV2.mayTouchColumn(
                            (int)x,y,(int)z),"Far edge column wrongly included");
                }
        // The mask fast-path must never return false when any legal noise
        // displacement can touch the new geometry.
        for(int x=-1400;x<=1400;x+=37)for(int z=-1400;z<=1400;z+=41)
            for(int y:new int[]{45,95,135,205,395,790,1160,1330,1480,1595}) {
                if(GrandOverworldCaveDesignV2.mayTouchColumn(x,y,z))continue;
                negativeColumns++;
                for(double warp:new double[]{-1.0,-0.5,0,0.5,1.0}) {
                    double strength=GrandOverworldCaveDesignV2.strength(
                            x+warp*6.0,y+warp*2.0,z-warp*5.0);
                    require(strength==0,"V2 broad phase omitted a cave at "
                            +x+","+y+","+z+" warp="+warp);
                }
            }
        require(negativeColumns>500,"Broad phase never excludes empty columns");
        // Worker-local caches cannot change cave strength under query
        // interleaving, including two independent sampler threads.
        var bad=new AtomicReference<Throwable>();
        Thread[] workers=new Thread[3];
        for(int worker=0;worker<workers.length;worker++){
            final int shift=worker*31;
            workers[worker]=new Thread(()->{
                try {
                    for(int i=0;i<1200;i++) {
                        int gx=(i*37+shift)%9-4,gz=(i*23+shift)%9-4;
                        int tier=i%4;
                        var h=GrandOverworldCaveDesignV2.annexAt(gx,gz,tier);
                        require(GrandOverworldCaveDesignV2.strength(
                                h.x(),h.y(),h.z())>=0.95,
                                "Worker cache changed annex coordinates");
                    }
                }catch(Throwable t){bad.compareAndSet(null,t);}
            },"cave-v2-smoke-"+worker);
            workers[worker].start();
        }
        for(Thread w:workers)try{w.join();}catch(InterruptedException e){
            Thread.currentThread().interrupt();throw new AssertionError(e);
        }
        if(bad.get()!=null)throw new AssertionError("Cave cache not deterministic",bad.get());
        System.out.println("REALSCALE_CAVE_DESIGN_V2_PASS annexes="+annexes+
                " expanded_annexes="+expanded+" loop_samples="+positiveLoops+
                " empty_columns="+negativeColumns);
    }
}
