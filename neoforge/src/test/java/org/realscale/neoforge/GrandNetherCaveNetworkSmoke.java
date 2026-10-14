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
        System.out.println("PASS: connected 3D Grand Nether cave network, "+samples
                +" signed-coordinate and vanilla-protection samples");
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
