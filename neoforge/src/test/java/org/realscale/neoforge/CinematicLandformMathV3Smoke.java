package org.realscale.neoforge;

/** Standalone Java 21 smoke test for ten versioned regional landform families. */
public final class CinematicLandformMathV3Smoke {
    private CinematicLandformMathV3Smoke(){}
    private static void require(boolean ok,String what){
        if(!ok)throw new AssertionError(what);
    }
    private static double localEffect(int kind,double u,double v,double r,long hash){
        double a=Math.floorMod(hash>>>45,6284L)/1000.0;
        return CinematicLandformMathV3.relief(kind,
                u*Math.cos(a)-v*Math.sin(a),
                u*Math.sin(a)+v*Math.cos(a),r,hash);
    }
    public static void main(String[] args) {
        boolean[] cold=new boolean[CinematicLandformMathV3.NONE+1];
        boolean[] hotWet=new boolean[CinematicLandformMathV3.NONE+1];
        boolean[] hotDry=new boolean[CinematicLandformMathV3.NONE+1];
        for(int x=-22;x<=22;x++)for(int z=-22;z<=22;z++){
            long h=CinematicLandformMathV3.hash(x,z);
            cold[CinematicLandformMathV3.kind(h,-0.7,0.5)]=true;
            hotWet[CinematicLandformMathV3.kind(h,0.5,0.6)]=true;
            hotDry[CinematicLandformMathV3.kind(h,0.5,-0.5)]=true;
        }
        require(cold[CinematicLandformMath.GLACIAL_U_TROUGH],
                "Cold climate must keep U trough");
        require(cold[CinematicLandformMathV3.CIRQUE_BASIN],
                "Northern mountains must produce ice-carved cirques");
        require(cold[CinematicLandformMathV3.MORAINE_RIDGES],
                "Northern mountains must produce glacial till ridges");
        require(hotWet[CinematicLandformMath.SANDSTONE_TOWERS]
                &&hotWet[CinematicLandformMath.KARST_TOWERS]
                &&hotWet[CinematicLandformMathV3.TEPUI_TABLELANDS],
                "Warm humid regions retain prior three geology families");
        require(hotDry[CinematicLandformMath.CAPROCK_MESA]
                &&hotDry[CinematicLandformMathV3.STAR_DUNE_FIELD],
                "Hot arid land retains mesas and dune fields");
        require(CinematicLandformMathV3.kind(0,-0.7,-0.5)
                ==CinematicLandformMath.VOLCANIC_CALDERA,
                "Caldera still belongs to cold dry inland terrain");
        require(CinematicLandformMathV3.kind(0,-0.1,0.5)
                ==CinematicLandformMathV3.NONE,
                "Unmatched climate leaves base terrain unchanged");
        double maxDifference=0;
        for(int cx=-3;cx<=3;cx++)for(int cz=-3;cz<=3;cz++) {
            long h=CinematicLandformMathV3.hash(cx,cz);
            double r=CinematicLandformMathV3.radius(h);
            for(int kind=0;kind<CinematicLandformMathV3.NONE;kind++) {
                require(CinematicLandformMathV3.relief(kind,r,0,r,h)==0,
                        "Seam must reach exactly zero at rim");
                require(CinematicLandformMathV3.apply(kind,3.1,0,0,r,h,-0.3)==3.1,
                        "Coastal and marine continentalness never changed");
                require(CinematicLandformMathV3.apply(kind,0.2,0,0,r,h,0.7)==0.2,
                        "Lowland terrain never changed");
                for(int i=-8;i<=8;i++)for(int j=-8;j<=8;j++) {
                    double dx=i*r/8.0,dz=j*r/8.0;
                    double d=CinematicLandformMathV3.relief(kind,dx,dz,r,h);
                    require(Double.isFinite(d)&&d>=-1.6&&d<=4.0,
                            "All geometry finite and bounded");
                    require(d==CinematicLandformMathV3.relief(kind,dx,dz,r,h),
                            "Same seed and coordinates deterministic");
                    if(kind<8) {
                        double prior=CinematicLandformMathV2.relief(kind,dx,dz,r,h);
                        maxDifference=Math.max(maxDifference,Math.abs(prior-d));
                    }
                }
            }
        }
        require(maxDifference==0,"All eight v2 landform relief equations must be exactly preserved in v3");
        long h=CinematicLandformMathV3.hash(2,1);
        double r=CinematicLandformMathV3.radius(h);
        double cirqueFloor=localEffect(CinematicLandformMathV3.CIRQUE_BASIN,0,-185,r,h);
        double cirqueWall=localEffect(CinematicLandformMathV3.CIRQUE_BASIN,0,-520,r,h);
        double cirqueExit=localEffect(CinematicLandformMathV3.CIRQUE_BASIN,0,400,r,h);
        require(cirqueFloor<-0.6&&cirqueWall>1.1&&cirqueExit<0,
                "Cirque needs a depressed bowl, exposed headwall and open valley exit");
        double moraineSide=localEffect(CinematicLandformMathV3.MORAINE_RIDGES,230,0,r,h);
        double moraineFloor=localEffect(CinematicLandformMathV3.MORAINE_RIDGES,0,0,r,h);
        require(moraineSide>0.75&&moraineFloor<0,
                "Moraine needs raised side ridges and a low middle");
        System.out.println("CINEMATIC_NORTH_V3_PASS: ten morphologies; cirques and moraines; climate; archived v2 parity; inland-only; seam safety");
    }
}
