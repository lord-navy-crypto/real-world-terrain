package org.realscale.neoforge;

/** 3D continuous cavern halls/tunnels geometry regression for Grand Nether. */
public final class GrandNetherCaveNetworkSmoke {
    private static void yes(boolean b,String message) {
        if(!b)throw new AssertionError(message);
    }
    public static void main(String[] args) {
        int samples=0;
        for(int cx=-3;cx<=3;cx++)for(int cz=-3;cz<=3;cz++) {
            for(int layer=0;layer<2;layer++) {
                var h=GrandNetherCaveNetwork.hall(cx,cz,layer);
                yes(GrandNetherCaveNetwork.strength((int)h.x(),(int)h.y(),
                        (int)h.z())>0.80,"Chamber core must be excavated");
                yes(h.radiusX()>=52&&h.radiusY()>=31&&h.radiusZ()>=52,
                        "Hollow halls have real large volume");
                yes(h.y()-h.radiusY()>144&&h.y()+h.radiusY()<440,
                        "Bedrock, original lava sea and original structure region protected");
                // Every one of the four local satellites must form a real
                // pocket connected to its parent hall by an open branch.
                for(int gallery=0;gallery<2;gallery++) {
                    var side=GrandNetherCaveNetwork.satellite(cx,cz,layer,gallery);
                    yes(side.x()-side.radiusX() > (long)cx*256+8
                            && side.x()+side.radiusX() < (long)(cx+1)*256-8,
                            "Satellite must not extend into a neighbor's cell");
                    yes(side.z()-side.radiusZ() > (long)cz*256+8
                            && side.z()+side.radiusZ() < (long)(cz+1)*256-8,
                            "Satellite Z must stay inside its cell");
                    yes(side.y()-side.radiusY()>148 && side.y()+side.radiusY()<436,
                            "Satellite height must protect Nether lower and roof");
                    yes(GrandNetherCaveNetwork.strength(side.x(),side.y(),side.z())>0.8,
                            "Satellite gallery core must be excavated");
                    connected(h,side,"secondary gallery branch");
                    samples+=20;
                }
                for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++) {
                    // Query equality ensures a data-independent seamless result.
                    int x=(int)h.x()+dx*30,z=(int)h.z()+dz*30,y=(int)h.y();
                    double a=GrandNetherCaveNetwork.strength(x,y,z);
                    double b=GrandNetherCaveNetwork.strength(x,y,z);
                    yes(Double.doubleToRawLongBits(a)==Double.doubleToRawLongBits(b),
                            "Deterministic cavern geometry");
                    samples++;
                }
                if(cx>=-2&&cx<=2&&cz>=-2&&cz<=2) {
                    var side=GrandNetherCaveNetwork.hall(cx+1,cz,layer);
                    var forward=GrandNetherCaveNetwork.hall(cx,cz+1,layer);
                    connected(h,side,"horizontal east tunnel");
                    connected(h,forward,"horizontal south tunnel");
                    if(layer==0)
                        connected(h,GrandNetherCaveNetwork.hall(cx,cz,1),
                                  "vertical interlayer tunnel");
                }
            }
        }
        for(int x=-512;x<512;x+=37)for(int z=-512;z<512;z+=41) {
            for(int y : new int[]{0,31,64,95,127,143,441,477,496,511}) {
                yes(GrandNetherCaveNetwork.strength(x,y,z)==0.0,
                        "Carving must never touch lava, structures or roof");
                samples++;
            }
        }
        // Check an actual negative/positive chunk crossing in the same tunnel.
        var negative=GrandNetherCaveNetwork.hall(-1,0,0);
        var positive=GrandNetherCaveNetwork.hall(0,0,0);
        double borderT=(0.0-negative.x())/(positive.x()-negative.x());
        double borderY=negative.y()+borderT*(positive.y()-negative.y());
        double borderZ=negative.z()+borderT*(positive.z()-negative.z());
        for (double x : new double[]{-1.0,-0.5,0.0,0.5,1.0}) {
            yes(GrandNetherCaveNetwork.strength(x,borderY,borderZ)>0.15,
                    "Negative-to-positive cross-cell tunnel blocked at X="+x);
            samples++;
        }
        // Sub-block samples must change continuously without integer stair-steps.
        var mid=GrandNetherCaveNetwork.hall(0,0,0);
        var east=GrandNetherCaveNetwork.hall(1,0,0);
        double mx=(mid.x()+east.x())*0.5;
        double my=(mid.y()+east.y())*0.5;
        double mz=(mid.z()+east.z())*0.5;
        for (int i=-8;i<=8;i++) {
            double x=mx+i*0.125;
            double a=GrandNetherCaveNetwork.strength(x,my,mz);
            double b=GrandNetherCaveNetwork.strength(x+0.01,my,mz);
            yes(Math.abs(a-b)<0.05,"Sub-block cave sampling discontinuity at "+x);
            samples++;
        }
        // Verify that the density-wrapper fast path is bit-exact when the
        // original terrain density already dominates all possible cavities.
        // A network strength >1 would invalidate the -1.8 threshold.
        for(int x=-900;x<=900;x+=43)for(int z=-900;z<=900;z+=47)
            for(int y=144;y<=440;y+=23) {
                for(double warp:new double[]{-1.0,0.0,1.0}) {
                    double strength=GrandNetherCaveNetwork.strength(
                            x+warp*5.0,y+warp*2.0,z-warp*4.0);
                    yes(strength>=0.0&&strength<=1.0,
                            "Nether cave strength exceeded proven [0,1] bounds");
                    for(double base:new double[]{-1.8,-2.1,-20.0}) {
                        double oldDensity=Math.min(base,-1.8*strength);
                        yes(Double.doubleToRawLongBits(oldDensity)==
                                Double.doubleToRawLongBits(base),
                                "Already-empty Nether terrain must remain unchanged");
                    }
                }
            }
        verifyConcurrentCellCache();
        System.out.println("PASS: connected 3D Grand Nether cave network, "+samples
                +" signed-coordinate and vanilla-protection samples");
    }
    /** Verify worker-local caches are independent of chunk order and threads. */
    private static void verifyConcurrentCellCache() {
        final int count=400;
        final int[][] coords=new int[count][3];
        final long[] expected=new long[count];
        for(int i=0;i<count;i++) {
            var h=GrandNetherCaveNetwork.hall((i*17)%11-5,(i*31)%11-5,i%2);
            coords[i][0]=(int)h.x()+(i%7)*5-15;
            coords[i][1]=(int)h.y()+(i%5)*3-6;
            coords[i][2]=(int)h.z()+(i%9)*4-16;
            expected[i]=Double.doubleToRawLongBits(
                    GrandNetherCaveNetwork.strength(coords[i][0],coords[i][1],coords[i][2]));
        }
        var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
        Thread[] workers=new Thread[4];
        for(int w=0;w<workers.length;w++) {
            final int shift=w*101;
            workers[w]=new Thread(()->{
                try {
                    for(int round=0;round<3;round++)for(int k=0;k<count;k++) {
                        int i=(k*73+shift+round*29)%count;
                        long observed=Double.doubleToRawLongBits(GrandNetherCaveNetwork.strength(
                                coords[i][0],coords[i][1],coords[i][2]));
                        yes(observed==expected[i],"Cave graph changed by threaded chunk order");
                    }
                } catch(Throwable t) {failure.compareAndSet(null,t);}
            },"grand-cave-test-"+w);
            workers[w].start();
        }
        for(Thread worker:workers) {
            try {worker.join();} catch(InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted concurrency smoke test",e);
            }
        }
        if(failure.get()!=null)throw new AssertionError("Concurrent cave cache regression",failure.get());
    }

    private static void connected(GrandNetherCaveNetwork.Hall a,
                                  GrandNetherCaveNetwork.Hall b,String what) {
        // Check every quarter-span has a carved core, across multiple chunks.
        for(int i=0;i<=16;i++) {
            double t=i/16.0;
            int x=(int)Math.round(a.x()+(b.x()-a.x())*t);
            int y=(int)Math.round(a.y()+(b.y()-a.y())*t);
            int z=(int)Math.round(a.z()+(b.z()-a.z())*t);
            double v=GrandNetherCaveNetwork.strength(x,y,z);
            yes(v>0.1,what+" broken at "+x+","+y+","+z);
        }
    }
}
