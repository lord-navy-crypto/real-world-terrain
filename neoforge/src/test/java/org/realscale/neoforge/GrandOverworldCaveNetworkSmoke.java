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
        System.out.println("TVA_GRAND_OVERWORLD_CAVE_PASS halls="+halls+
                " large_or_wide_tunnels="+longLinks+
                " high_halls="+highAltitudeHalls+
                " rare_epic_or_chamber_count="+giant+
                " threads="+threads.length);
    }
}
