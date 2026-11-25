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
        // The upper-cavern fast path is supported by the actual immutable
        // hall, satellite and passage geometry, not merely by strength()
        // returning zero after its new early exit. Test the highest possible
        // endpoint + vertical tube allowance for every generated path kind.
        int altitudeProofs=0;
        for(int gx=-20;gx<=20;gx++)for(int gz=-20;gz<=20;gz++)
            for(int layer=0;layer<GrandOverworldCaveNetwork.LAYERS;layer++) {
                var a=GrandOverworldCaveNetwork.hall(gx,gz,layer);
                require(a.y()+a.radiusY()<GrandOverworldCaveNetwork.MAX_GEOMETRY_Y,
                        "Upper cave chamber unexpectedly exceeds geometry bound");
                altitudeProofs++;
                for(int direction=0;direction<4;direction++) {
                    int dx=direction==0?1:direction==1?-1:0;
                    int dz=direction==2?1:direction==3?-1:0;
                    var b=GrandOverworldCaveNetwork.hall(
                            gx+dx,gz+dz,layer);
                    var passage=GrandOverworldCaveNetwork.corridor(
                            gx,gz,layer,direction);
                    double reach=passage.radius()*passage.verticalScale()+16;
                    require(Math.max(a.y(),b.y())+reach
                                    <GrandOverworldCaveNetwork.MAX_GEOMETRY_Y,
                            "Upper cave passage unexpectedly exceeds geometry bound");
                    altitudeProofs++;
                }
                for(int gallery=0;gallery<2;gallery++) {
                    var side=GrandOverworldCaveNetwork.satellite(
                            gx,gz,layer,gallery);
                    require(side.y()+side.radiusY()
                                    <GrandOverworldCaveNetwork.MAX_GEOMETRY_Y,
                            "Upper cave satellite exceeds geometry bound");
                    altitudeProofs++;
                }
            }
        // Shaft maximum: radius 20..36 with verticalScale 1, plus reach
        // 16. Artery upper bound (tier 2 max altitude 879) is far lower.
        require(1439+36+16<GrandOverworldCaveNetwork.MAX_GEOMETRY_Y,
                "Inter-layer vertical shaft exceeds upper cave bound");
        require(879+(47+36)*1.65+16<GrandOverworldCaveNetwork.MAX_GEOMETRY_Y,
                "Kilometer arterial passage exceeds upper cave bound");
        // Cave entrances are independent and may extend toward Y1920:
        // the high-altitude optimization must NEVER hide these shafts.
        int highEntrances=0;
        for(int gx=-18;gx<=18;gx++)for(int gz=-18;gz<=18;gz++) {
            var low=GrandOverworldCaveNetwork.hall(gx,gz,0);
            double y=1750;
            double rise=y-low.y();
            double x=low.x()+Math.sin(rise/92.0)*5;
            double z=low.z()+Math.sin(rise/119.0)*4;
            if(GrandOverworldCaveNetwork.entranceStrength(x,y,z)>0.9)
                highEntrances++;
            require(GrandOverworldCaveNetwork.strength(x,y,z)==0.0,
                    "Upper altitude must be free of giant cave network");
        }
        require(highEntrances>0,"Highland entrances must remain above cave graph ceiling");
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
        // Verify all 47 masked components against the unmodified 3D graph.
        // Equal raw bits, not just equivalent cave shapes, are required.
        // This covers signed cell edges, three-dimensional altitude changes,
        // extreme +/-6 X +/-5 Z warps and arterial bridges.
        long maskParitySamples=0,maskedEmpty=0,maskedPresent=0;
        final int[] compareY={-40,135,395,790,1330,1540,1700,1703};
        final double[] compareWarp={-1.0,-0.55,0.0,0.55,1.0};
        for(int x=-2570;x<=2570;x+=57)
            for(int z=-2440;z<=2440;z+=63) {
                boolean near=GrandOverworldCaveNetwork.mayContainVoidNearColumn(x,z);
                if(near)maskedPresent++;else maskedEmpty++;
                for(int y:compareY)for(double warp:compareWarp) {
                    double wx=x+warp*6.0,wy=y+warp*2.0,wz=z-warp*5.0;
                    long normal=Double.doubleToRawLongBits(
                            GrandOverworldCaveNetwork.strength(wx,wy,wz));
                    long optimized=Double.doubleToRawLongBits(
                            GrandOverworldCaveNetwork.strengthFromColumn(
                                    x,z,wx,wy,wz));
                    require(normal==optimized,
                            "Masked cave components changed density at "+
                            x+","+y+","+z+" warp="+warp+
                            " expected="+Double.longBitsToDouble(normal)+
                            " actual="+Double.longBitsToDouble(optimized));
                    maskParitySamples++;
                }
            }
        require(maskParitySamples>150000 && maskedEmpty>0 && maskedPresent>0,
                "Masked cave regression failed to test diverse real columns");
        // Deliberately feed shifts larger than the supported warp: this must
        // fall back to the original graph, never trust a stale cached mask.
        for(int x=-1300;x<=1300;x+=103)for(int z=-900;z<=900;z+=109)
            for(int y:new int[]{140,410,800,1360}) {
                double wx=x+31.0,wz=z-26.0;
                require(Double.doubleToRawLongBits(
                        GrandOverworldCaveNetwork.strengthFromColumn(
                                x,z,wx,y,wz))==Double.doubleToRawLongBits(
                        GrandOverworldCaveNetwork.strength(wx,y,wz)),
                        "Unsupported displacement must fall back to full cave graph");
            }
        // Jigsaw structure height probes revisit many neighboring cave cells
        // in changing order. Verify per-region memo retention cannot cause
        // stale geometry, invalid masks, or changed density raw bits.
        int[] churnX={-2350,-1600,-780,-20,18,706,785,1590,2350};
        int[] churnZ={-1701,-770,-23,16,764,1547,2280};
        int[] churnY={135,395,790,1330,1530};
        long[] expected=new long[churnX.length*churnZ.length*churnY.length];
        int offset=0;
        for(int x:churnX)for(int z:churnZ)for(int y:churnY)
            expected[offset++]=Double.doubleToRawLongBits(
                    GrandOverworldCaveNetwork.strength(x,y,z));
        long extraChecks=0;
        for(int pass=0;pass<7;pass++) {
            for(int xi=0;xi<churnX.length;xi++)
                for(int zi=0;zi<churnZ.length;zi++)
                    for(int yi=0;yi<churnY.length;yi++) {
                        int ax=(xi+pass*3)%churnX.length;
                        int az=(zi+pass*2)%churnZ.length;
                        int x=churnX[ax],z=churnZ[az],y=churnY[yi];
                        int originalIndex=(ax*churnZ.length+az)*churnY.length+yi;
                        require(Double.doubleToRawLongBits(
                                GrandOverworldCaveNetwork.strength(x,y,z))
                                ==expected[originalIndex],
                                "Cave geometry mutated after region switching");
                        double warp=(pass%3-1)*0.7;
                        double wx=x+6*warp, wz=z-5*warp, wy=y+2*warp;
                        require(Double.doubleToRawLongBits(
                                GrandOverworldCaveNetwork.strengthFromColumn(
                                        x,z,wx,wy,wz))
                                ==Double.doubleToRawLongBits(
                                        GrandOverworldCaveNetwork.strength(
                                                wx,wy,wz)),
                                "Cave mask changed after 8-region churn");
                        extraChecks++;
                    }
        }
        require(extraChecks>2000,"Region-churn regression did not run");
        System.out.println("TVA_GRAND_OVERWORLD_CAVE_PASS halls="+halls+
                " large_or_wide_tunnels="+longLinks+
                " high_halls="+highAltitudeHalls+
                " rare_epic_or_chamber_count="+giant+
                " threads="+threads.length+" fast_path_empty_columns="+negativeColumns+
                " exact_mask_parity_samples="+maskParitySamples);
    }
}
