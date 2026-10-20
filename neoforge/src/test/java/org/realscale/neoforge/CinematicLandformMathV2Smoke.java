package org.realscale.neoforge;

/**
 * Pure JDK executable regression for the v2 geometry. No Minecraft runtime.
 * Includes explicit old-v1 API compatibility and geography profile diversity.
 */
public final class CinematicLandformMathV2Smoke {
    private static void ok(boolean b,String name){
        if(!b)throw new AssertionError(name);
    }
    private static double effect(int kind,double u,double v,double radius,long hash){
        double angle=Math.floorMod(hash>>>45,6284L)/1000.0;
        // V2 relief rotates world coords, invert this to specify a site-local
        // point independent of the district's world-absolute orientation.
        double dx=u*Math.cos(angle)-v*Math.sin(angle);
        double dz=u*Math.sin(angle)+v*Math.cos(angle);
        return CinematicLandformMathV2.relief(kind,dx,dz,radius,hash);
    }
    public static void main(String[] args) {
        boolean[] wetSeen=new boolean[8],drySeen=new boolean[8];
        for(int cx=-24;cx<=24;cx++)for(int cz=-24;cz<=24;cz++){
            long hash=CinematicLandformMathV2.hash(cx,cz);
            int wet=CinematicLandformMathV2.kind(hash,0.55,0.60);
            int dry=CinematicLandformMathV2.kind(hash,0.55,-0.55);
            wetSeen[wet]=true;drySeen[dry]=true;
        }
        ok(wetSeen[CinematicLandformMath.SANDSTONE_TOWERS]
                &&wetSeen[CinematicLandformMath.KARST_TOWERS]
                &&wetSeen[CinematicLandformMathV2.TEPUI_TABLELANDS],
                "All three wet tropical landform families must occur");
        ok(drySeen[CinematicLandformMath.CAPROCK_MESA]
                &&drySeen[CinematicLandformMathV2.STAR_DUNE_FIELD],
                "Hot arid districts need mesas and star-dune fields");
        ok(CinematicLandformMathV2.kind(0, -0.7,0.5)
                ==CinematicLandformMath.GLACIAL_U_TROUGH,
                "Cold humid U valley preserved in v2");
        ok(CinematicLandformMathV2.kind(0, -0.7,-0.5)
                ==CinematicLandformMath.VOLCANIC_CALDERA,
                "Cold dry caldera preserved in v2");
        ok(CinematicLandformMathV2.kind(0, -0.1,0.5)
                ==CinematicLandformMathV2.NONE,
                "Unmatched climate remains unchanged");
        for(int cx=-3;cx<=3;cx++)for(int cz=-3;cz<=3;cz++){
            long h=CinematicLandformMathV2.hash(cx,cz);
            double r=CinematicLandformMathV2.radius(h);
            for(int kind=0;kind<=CinematicLandformMathV2.NONE;kind++) {
                ok(CinematicLandformMathV2.relief(kind,r,0,r,h)==0.0,
                        "Landform must vanish at site rim");
                ok(CinematicLandformMathV2.apply(kind,2.2,0,0,r,h,-0.3)==2.2,
                        "Ocean is never overwritten");
                ok(CinematicLandformMathV2.apply(kind,0.35,0,0,r,h,0.8)==0.35,
                        "Coastal lowlands must remain intact");
                for(int a=-10;a<=10;a++)for(int b=-10;b<=10;b++) {
                    double dx=a*r/10.0,dz=b*r/10.0;
                    double delta=CinematicLandformMathV2.relief(kind,dx,dz,r,h);
                    ok(Double.isFinite(delta) && delta>=-1.6 && delta<=4.0,
                            "Geometry must be bounded and finite");
                    if(kind<6)
                        ok(delta==CinematicLandformMath.relief(kind,dx,dz,r,h),
                                "Historical v1 relief changed in v2!");
                }
            }
        }
        long h=CinematicLandformMathV2.hash(2,1);
        double r=CinematicLandformMathV2.radius(h);
        double left=effect(CinematicLandformMathV2.TEPUI_TABLELANDS,-450,-95,r,h);
        double right=effect(CinematicLandformMathV2.TEPUI_TABLELANDS,450,130,r,h);
        double saddle=effect(CinematicLandformMathV2.TEPUI_TABLELANDS,0,0,r,h);
        ok(left>2.9&&right>2.9&&saddle+1.0<Math.min(left,right),
                "Two isolated high tepui plateaus need a low walkable saddle");
        double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
        for(int x=-950;x<=950;x+=37)for(int z=-950;z<=950;z+=37) {
            double delta=effect(CinematicLandformMathV2.STAR_DUNE_FIELD,x,z,r,h);
            min=Math.min(min,delta);
            max=Math.max(max,delta);
        }
        ok(max>1.15&&max-min>1.05,
                "Dunes must have distinctive large-scale star ridges and low swales");
        System.out.println("CINEMATIC_V2_MACRO_PASS: eight morphologies, dune crests, twin tepui saddles, bounded seams, v1 parity");
    }
}
