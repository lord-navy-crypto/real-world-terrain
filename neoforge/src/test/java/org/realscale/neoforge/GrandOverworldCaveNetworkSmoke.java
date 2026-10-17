package org.realscale.neoforge;

import java.util.concurrent.atomic.AtomicReference;

/** Standalone Java 21 regression: real giant void sizes, topology and seams. */
public final class GrandOverworldCaveNetworkSmoke {
    private GrandOverworldCaveNetworkSmoke(){}
    static void require(boolean ok,String why) {if(!ok)throw new AssertionError(why);}
    public static void main(String[] args) {
        int broad=0,tall=0,giant=0,narrow=0;
        int halls=0,highAltitudeHalls=0;
        for(int gx=-7;gx<=7;gx++)for(int gz=-7;gz<=7;gz++) {
            for(int layer=0;layer<GrandOverworldCaveNetwork.LAYERS;layer++) {
                var chamber=GrandOverworldCaveNetwork.hall(gx,gz,layer);
                require(GrandOverworldCaveNetwork.strength(
                        chamber.x(),chamber.y(),chamber.z())>0.95,
                        "Connected cave hall missing at "+gx+","+gz+" layer "+layer);
                require(chamber.radiusX()>=78 && chamber.radiusY()>=44,
                        "Chamber too small for giant world");
                if(chamber.radiusX()>140)giant++;
                if(chamber.y()>1000) highAltitudeHalls++;
                halls++;
                for(int direction=0;direction<4;direction++) {
                    var passage=GrandOverworldCaveNetwork.corridor(gx,gz,layer,direction);
                    if(passage.kind()==0 && passage.radius()<=20)narrow++;
                    else if(passage.kind()==1)tall++;
                    else if(passage.kind()==2)giant++;
                    else broad++;
                    if(passage.kind()==1)
                        require(passage.verticalScale()>=2.0,
                                "Stream-incised canyon passage must be tall");
                    if(passage.kind()==2)
                        require(passage.radius()>=61,
                                "Epic avenue radius must be substantial");
                }
            }
        }
        require(halls==900,"Not enough large chambers sampled");
        require(highAltitudeHalls>=200,"Giant mountain strata are missing cave halls");
        require(narrow>0&&broad>0&&tall>0&&giant>0,
                "Passage cross-section variety missing");

        // Main chamber-to-chamber routes need genuinely wide interior air:
        // all mids must produce positive cave values for some passages.
        int longLinks=0;
        for(int gx=-6;gx<=6;gx++)for(int gz=-6;gz<=6;gz++) {
            for(int layer=0;layer<4;layer++) {
                var a=GrandOverworldCaveNetwork.hall(gx,gz,layer);
                var b=GrandOverworldCaveNetwork.hall(gx+1,gz,layer);
                var section=GrandOverworldCaveNetwork.corridor(gx,gz,layer,0);
                if(section.radius()<28)continue;
                double x=(a.x()+b.x())*0.5;
                double y=(a.y()+b.y())*0.5;
                double z=(a.z()+b.z())*0.5;
                // The smooth seeded centerline can bend up to 14 blocks.
                // Wide passages must leave open space near the linear midpoint.
                require(GrandOverworldCaveNetwork.strength(x,y,z)>0,
                        "Broad connecting tunnel disconnected");
                longLinks++;
            }
        }
        require(longLinks>300,"Long wide tunnels unexpectedly uncommon");

        // Rare arteries must be REAL continuous kilometer-scale voids,
        // not just a declared 1536m constant in a source file.
        int arterialRoutes=0;
        for(int gx=-9;gx<=9;gx+=3) for(int gz=-6;gz<=6;gz++)
            for(int layer=1;layer<=2;layer++) {
                if(!GrandOverworldCaveNetwork.arterialActive(gx,gz,layer,true))continue;
                var a=GrandOverworldCaveNetwork.hall(gx,gz,layer);
                var b=GrandOverworldCaveNetwork.hall(gx+3,gz,layer);
                double length=Math.hypot(b.x()-a.x(),b.z()-a.z());
                require(length>1300,"Arterial should span more than a kilometer");
                double x=(a.x()+b.x())*.5, y=(a.y()+b.y())*.5, z=(a.z()+b.z())*.5;
                require(GrandOverworldCaveNetwork.strength(x,y,z)>0.01,
                        "Arterial has no traversable midpoint");
                arterialRoutes++;
            }
        require(arterialRoutes>=20,"Kilometer arterial cave corridors are too rare");

        // Caves should be FINDABLE: rare natural highland shafts start in
        // an existing hall and extend toward the actual mountain surface.
        int entrances=0;
        for(int gx=-12;gx<=12;gx++)for(int gz=-12;gz<=12;gz++) {
            var low=GrandOverworldCaveNetwork.hall(gx,gz,0);
            var middle=GrandOverworldCaveNetwork.hall(gx,gz,1);
            double yl=low.y()+220, ym=middle.y()+220;
            // Pick the candidate whose entrance actually exists.
            for(var hall:new GrandOverworldCaveNetwork.Hall[]{low,middle}) {
                double y=hall.y()+220;
                double x=hall.x()+Math.sin(220/92.0)*5;
                double z=hall.z()+Math.sin(220/119.0)*4;
                if(GrandOverworldCaveNetwork.entranceStrength(x,y,z)>0.95) {
                    entrances++;
                    break;
                }
            }
        }
        require(entrances>=10,"Entrances should occasionally lead to mountain halls");

        // Estimate physical excavation candidates over a representative
        // 1536x1536 mountain block and its vertical strata. This was missing
        // from old tests (which only checked hall existence), allowing an
        // excessive density of simultaneously active giant caves.
        int sampled=0, excavated=0;
        for(int x=-768;x<768;x+=32)for(int z=-768;z<768;z+=32)
            for(int y=256;y<=1504;y+=32) {
                sampled++;
                if(GrandOverworldCaveNetwork.strength(x+0.5,y+0.5,z+0.5)>0.0)
                    excavated++;
            }
        double candidateFraction=excavated/(double)sampled;
        require(candidateFraction>0.002 && candidateFraction<0.20,
                "Giant caves must be discoverable without hollowing most of "
                +"the mountain: "+excavated+"/"+sampled);
        // Radius and corridor length need not shrink just to reduce
        // coverage: chamber spacing is larger (CELL=768).
        require(GrandOverworldCaveNetwork.CELL>=768,
                "Grand rooms must not be densely tiled at legacy 512 spacing");

        // The same exact world coordinate must evaluate identically across
        // any adjacent chunk/task boundary, independent of evaluation order.
        final double[][] samples=new double[400][3];
        for(int i=0;i<samples.length;i++) {
            samples[i][0]=(i-200)*16+15.5;
            samples[i][1]=130+(i%4)*410;
            samples[i][2]=(i%33-16)*16+0.5;
        }
        double[] baseline=new double[samples.length];
        for(int i=0;i<samples.length;i++)baseline[i]=GrandOverworldCaveNetwork.strength(
                samples[i][0],samples[i][1],samples[i][2]);
        AtomicReference<Throwable> error=new AtomicReference<>();
        Thread[] threads=new Thread[4];
        for(int w=0;w<threads.length;w++) {
            final int worker=w;
            threads[w]=new Thread(()->{
                try{
                    for(int i=samples.length-1-worker;i>=0;i-=4) {
                        double value=GrandOverworldCaveNetwork.strength(
                                samples[i][0],samples[i][1],samples[i][2]);
                        require(Double.doubleToRawLongBits(value)==
                                Double.doubleToRawLongBits(baseline[i]),
                                "Worldgen thread changed absolute cave output");
                    }
                } catch(Throwable ex){error.compareAndSet(null,ex);}
            },"cave-seam-worker-"+w);
            threads[w].start();
        }
        for(Thread t:threads)try{t.join();}catch(InterruptedException e){
            Thread.currentThread().interrupt();throw new AssertionError(e);}
        if(error.get()!=null)throw new AssertionError("Concurrent cave seam",error.get());
        require(GrandOverworldCaveNetwork.strength(0,-61,0)==0,
                "Bedrock protection failed");
        require(GrandOverworldCaveNetwork.strength(0,1980,0)==0,
                "High world ceiling protection failed");
        // Performance fast path must NEVER reject a real voxel, even when
        // the caller's +/-6 X, +/-5 Z and +/-2 Y noise warp is at an extreme.
        // Across negative and positive cells, test vertical shafts, halls,
        // inter-layer galleries and kilometer-scale arterial routes together.
        int negativeColumns=0, checkedFastPath=0;
        final int[] probeY={128,340,700,1200,1520,1780};
        final double[] warpSamples={-1.0,0.0,1.0};
        for(int x=-2250;x<=2250;x+=47)for(int z=-2100;z<=2100;z+=53) {
            boolean may=GrandOverworldCaveNetwork.mayContainVoidNearColumn(x,z);
            if(!may) {
                negativeColumns++;
                for(int y:probeY)for(double warp:warpSamples) {
                    double result=GrandOverworldCaveNetwork.strength(
                            x+warp*6.0,y+warp*2.0,z-warp*5.0);
                    require(result==0.0,
                            "Conservative cave column fast path hid geometry at "
                            +x+","+y+","+z+" warp="+warp+" strength="+result);
                    checkedFastPath++;
                }
            }
        }
        require(negativeColumns>0 && checkedFastPath>0,
                "Cave fast path must prune at least some truly empty columns");
        for(int gx=-3;gx<=3;gx++)for(int gz=-3;gz<=3;gz++)
            for(int layer=0;layer<GrandOverworldCaveNetwork.LAYERS;layer++) {
                var room=GrandOverworldCaveNetwork.hall(gx,gz,layer);
                require(GrandOverworldCaveNetwork.mayContainVoidNearColumn(
                        (int)room.x(),(int)room.z()),
                        "Giant room column erroneously rejected");
            }
        // Probe exact boundary margins where a bounded warp can switch the
        // active cell. These must never reject without checking both cells.
        for(int k=-2;k<=2;k++) {
            require(GrandOverworldCaveNetwork.mayContainVoidNearColumn(
                    k*GrandOverworldCaveNetwork.CELL+2,100),
                    "Cross-cell X warp protection missing");
            require(GrandOverworldCaveNetwork.mayContainVoidNearColumn(
                    100,k*GrandOverworldCaveNetwork.CELL+3),
                    "Cross-cell Z warp protection missing");
        }
        System.out.println("TVA_GRAND_OVERWORLD_CAVE_PASS halls="+halls+
                " large_or_wide_tunnels="+longLinks+
                " high_halls="+highAltitudeHalls+
                " rare_epic_or_chamber_count="+giant+
                " threads="+threads.length+" fast_path_empty_columns="+negativeColumns);
    }
}
