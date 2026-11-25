package org.realscale.neoforge;

import java.util.concurrent.atomic.AtomicReference;

/** Geometry, conservative-mask parity, bounded cache and worker isolation. */
public final class GrandOverworldCaveDesignV2Smoke {
    private GrandOverworldCaveDesignV2Smoke() {}
    private static void require(boolean value,String why) {
        if(!value)throw new AssertionError(why);
    }
    private static void same(double expected,double actual,String where) {
        if(Double.doubleToLongBits(expected)!=Double.doubleToLongBits(actual))
            throw new AssertionError(where+" expected="+expected+" actual="+actual);
    }
    public static void main(String[] args) {
        int annexes=0,expanded=0,positiveLoops=0,negativeColumns=0;
        int paritySamples=0,nonzeroMasks=0;
        for(int gx=-4;gx<=4;gx++) for(int gz=-4;gz<=4;gz++)
            for(int layer=0;layer<GrandOverworldCaveNetwork.LAYERS;layer++) {
                require(GrandOverworldCaveDesignV2.geometryFitsInsideCell(
                        gx,gz,layer),"Extra cave would cross a 768-cell seam: "
                        +gx+","+gz+","+layer);
                var room=GrandOverworldCaveDesignV2.annexAt(gx,gz,layer);
                double v=GrandOverworldCaveDesignV2.strength(room.x(),room.y(),room.z());
                require(v>=0.95,"Detached annex is not carved: "+gx+","+gz+","+layer);
                annexes++;
                double existing=GrandOverworldCaveNetwork.strength(
                        room.x(),room.y(),room.z());
                if(v>existing+0.05)expanded++;
                var a=GrandOverworldCaveNetwork.satellite(gx,gz,layer,0);
                var b=GrandOverworldCaveNetwork.satellite(gx,gz,layer,1);
                for(int step=1;step<10;step++) {
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
        // Exercise the exact cached X/Z mask against the original full
        // evaluator at every legal warp in a dense sample around all feature
        // anchors, including negative world coordinates and cell seams.
        for(int gx=-3;gx<=3;gx++)for(int gz=-3;gz<=3;gz++)
            for(int tier=0;tier<GrandOverworldCaveNetwork.LAYERS;tier++) {
                var a=GrandOverworldCaveNetwork.satellite(gx,gz,tier,0);
                var b=GrandOverworldCaveNetwork.satellite(gx,gz,tier,1);
                var annex=GrandOverworldCaveDesignV2.annexAt(gx,gz,tier);
                for(double t:new double[]{0.0,0.5,1.0}) {
                    double px=a.x()+(b.x()-a.x())*t;
                    double py=a.y()+(b.y()-a.y())*t;
                    double pz=a.z()+(b.z()-a.z())*t;
                    for(int dx:new int[]{-12,0,12})
                        for(int dz:new int[]{-12,0,12})
                            for(int dy:new int[]{-24,0,24}) {
                                int sx=(int)Math.floor(px)+dx,sz=(int)Math.floor(pz)+dz;
                                int sy=(int)Math.floor(py)+dy;
                                int mask=GrandOverworldCaveDesignV2.componentMaskForColumn(sx,sz);
                                if(mask!=0)nonzeroMasks++;
                                for(double wx:new double[]{-6,0,6})
                                    for(double wz:new double[]{-5,0,5})
                                        for(double wy:new double[]{-2,0,2}) {
                                            double x=sx+wx,y=sy+wy,z=sz+wz;
                                            double expected=GrandOverworldCaveDesignV2.strength(x,y,z);
                                            double actual=GrandOverworldCaveDesignV2.strengthFromColumn(
                                                    sx,sz,x,y,z,mask);
                                            same(expected,actual,"cached-mask parity "
                                                    +sx+","+sy+","+sz+" warp="+wx+","+wy+","+wz);
                                            paritySamples++;
                                        }
                            }
                }
                // Terminal-room center must always be present in its mask.
                int sx=(int)Math.floor(annex.x()),sz=(int)Math.floor(annex.z());
                int mask=GrandOverworldCaveDesignV2.componentMaskForColumn(sx,sz);
                require(mask!=0,"Annex center omitted from X/Z candidate mask");
            }
        require(paritySamples>50000,"Insufficient optimized/reference parity coverage");
        require(nonzeroMasks>100,"Candidate mask did not select expected geometry");
        // The old coarse local<24 / local>=744 rejection could discard a
        // legal warped query near a cell edge. Probe the full warp envelope.
        for(int gx=-5;gx<=5;gx++)for(int gz=-5;gz<=5;gz++)
            for(int xoff:new int[]{18,19,20,21,22,23,24,25,742,743,744,745,746,747,748,749})
                for(int zoff:new int[]{18,19,20,21,22,23,24,25,742,743,744,745,746,747,748,749}) {
                    int x=gx*768+xoff,z=gz*768+zoff;
                    int mask=GrandOverworldCaveDesignV2.componentMaskForColumn(x,z);
                    for(double wx:new double[]{-6,0,6})
                        for(double wz:new double[]{-5,0,5})
                            for(int y:new int[]{125,375,760,1300,1575}) {
                                double expected=GrandOverworldCaveDesignV2.strength(
                                        x+wx,y,z+wz);
                                double actual=GrandOverworldCaveDesignV2.strengthFromColumn(
                                        x,z,x+wx,y,z+wz,mask);
                                same(expected,actual,"edge-mask parity "+x+","+y+","+z);
                            }
                }
        for(int gx=-3;gx<=3;gx++)for(int gz=-3;gz<=3;gz++)
            for(int off:new int[]{0,1,8,16,23,744,752,767})
                for(int y:new int[]{135,395,790,1330}) {
                    double x=(long)gx*768+off,z=(long)gz*768+2;
                    require(GrandOverworldCaveDesignV2.strength(x,y,z)==0,
                            "V2 features leaked across cell boundaries");
                    require(!GrandOverworldCaveDesignV2.mayTouchColumn(
                            (int)x,y,(int)z),"Far edge column wrongly included");
                }
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
        var bad=new AtomicReference<Throwable>();
        Thread[] workers=new Thread[3];
        for(int worker=0;worker<workers.length;worker++) {
            final int shift=worker*31;
            workers[worker]=new Thread(()->{
                try {
                    for(int i=0;i<1200;i++) {
                        int gx=(i*37+shift)%9-4,gz=(i*23+shift)%9-4;
                        int tier=i%4;
                        var h=GrandOverworldCaveDesignV2.annexAt(gx,gz,tier);
                        double expected=GrandOverworldCaveDesignV2.strength(h.x(),h.y(),h.z());
                        int sx=(int)Math.floor(h.x()),sz=(int)Math.floor(h.z());
                        int mask=GrandOverworldCaveDesignV2.componentMaskForColumn(sx,sz);
                        double actual=GrandOverworldCaveDesignV2.strengthFromColumn(
                                sx,sz,h.x(),h.y(),h.z(),mask);
                        same(expected,actual,"worker cache parity");
                        require(actual>=0.95,"Worker cache changed annex coordinates");
                    }
                }catch(Throwable t){bad.compareAndSet(null,t);}
            },"cave-v2-smoke-"+worker);
            workers[worker].start();
        }
        for(Thread w:workers)try{w.join();}catch(InterruptedException e) {
            Thread.currentThread().interrupt();throw new AssertionError(e);
        }
        if(bad.get()!=null)throw new AssertionError("Cave cache not deterministic",bad.get());
        System.out.println("REALSCALE_CAVE_DESIGN_V2_PASS annexes="+annexes
                +" expanded_annexes="+expanded+" loop_samples="+positiveLoops
                +" parity_samples="+paritySamples+" nonzero_masks="+nonzeroMasks
                +" empty_columns="+negativeColumns);
    }
}
