package org.realscale.neoforge;

public final class CinematicLandformMathSmoke {
    private static void yes(boolean ok,String what) {
        if(!ok)throw new AssertionError(what);
    }
    private static boolean near(double a,double b) {
        return Math.abs(a-b)<1.0e-9;
    }
    /**
     * Reference implementation before the squared-distance optimization.
     * Compare outputs on positive/negative world-relative coordinates, lattice
     * boundaries, and many district seeds to detect accidental terrain drift.
     */
    private static double historicalTowerDistance(double u,double v,long h,double spacing) {
        int cx=(int)Math.floor(u/spacing),cz=(int)Math.floor(v/spacing);
        double best=Double.POSITIVE_INFINITY;
        for(int i=-1;i<=1;i++)for(int j=-1;j<=1;j++) {
            int xx=cx+i,zz=cz+j;
            long k=CinematicLandformMath.mix(h ^ (long)xx*0x9e3779b97f4a7c15L
                    ^ (long)zz*0xc2b2ae3d27d4eb4fL);
            double px=(xx+0.5)*spacing
                    +(Math.floorMod(k>>>17,1001L)/1000.0-0.5)*spacing*0.47;
            double pz=(zz+0.5)*spacing
                    +(Math.floorMod(k>>>38,1001L)/1000.0-0.5)*spacing*0.47;
            best=Math.min(best,Math.hypot(u-px,v-pz));
        }
        return best;
    }
    private static void testOptimizedDistanceParity() {
        for(int gx=-4;gx<=4;gx++)for(int gz=-4;gz<=4;gz++) {
            long h=CinematicLandformMath.hash(gx,gz);
            for(int i=-35;i<=35;i++)for(int j=-35;j<=35;j++) {
                double u=i*27.135,v=j*31.875;
                for(double spacing:new double[]{205.0,300.0}) {
                    double reference=historicalTowerDistance(u,v,h,spacing);
                    double current=CinematicLandformMath.towerDistance(u,v,h,spacing);
                    yes(Math.abs(reference-current)<1.0e-9,
                            "Squared distance optimization changed tower geometry");
                }
            }
        }
    }
    public static void main(String[] args) {
        testOptimizedDistanceParity();
        int[] terrain={
            CinematicLandformMath.SANDSTONE_TOWERS,
            CinematicLandformMath.KARST_TOWERS,
            CinematicLandformMath.CAPROCK_MESA,
            CinematicLandformMath.INCISED_CANYON,
            CinematicLandformMath.VOLCANIC_CALDERA,
            CinematicLandformMath.GLACIAL_U_TROUGH};
        long h=CinematicLandformMath.hash(0,0);
        yes(CinematicLandformMath.kind(h,0.4,0.5)<2,
                "Wet warm landscapes should make towers");
        yes(CinematicLandformMath.kind(h,0.4,-0.3)==2,
                "Dry hot regions should make caprock mesas");
        yes(CinematicLandformMath.kind(h,-0.1,-0.1)==3,
                "Dry temperate regions should make incised canyons");
        yes(CinematicLandformMath.kind(h,-0.7,0.5)==5,
                "Cold wet highlands form broad glacial U valleys");
        yes(CinematicLandformMath.kind(h,-0.7,-0.5)==4,
                "Cold dry uplands can form extinct caldera basins");
        yes(CinematicLandformMath.kind(h,-0.1,0.5)==6,
                "Moderate cool wet districts preserve baseline");
        yes(CinematicLandformMath.gate(-0.2,3.5)==0,
                "Seafloor and offshore continents are untouched");
        yes(CinematicLandformMath.gate(0.6,0.2)==0,
                "Lowlands remain untouched");
        yes(CinematicLandformMath.gate(0.6,2.2)>0.9,
                "High interior landscapes can generate");
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++) {
            long hash=CinematicLandformMath.hash(x,z);
            double radius=CinematicLandformMath.radius(hash);
            int sx=CinematicLandformMath.siteX(x,hash);
            int sz=CinematicLandformMath.siteZ(z,hash);
            yes(sx-radius>=((long)x)*CinematicLandformMath.CELL
                    &&sx+radius<=((long)x+1)*CinematicLandformMath.CELL,
                    "Footprint must never cross a region boundary in X");
            yes(sz-radius>=((long)z)*CinematicLandformMath.CELL
                    &&sz+radius<=((long)z+1)*CinematicLandformMath.CELL,
                    "Footprint must never cross a region boundary in Z");
            for(int kind:terrain) {
                yes(near(CinematicLandformMath.relief(kind,radius,0,radius,hash),0),
                        "Region boundary must be zero");
                yes(near(CinematicLandformMath.apply(kind,2.5,0,0,radius,hash,-0.2),2.5),
                        "Ocean gate must preserve input exactly");
                yes(near(CinematicLandformMath.apply(kind,0.1,0,0,radius,hash,0.8),0.1),
                        "Lowland gate must preserve input exactly");
                for(int a=-8;a<=8;a++)for(int b=-8;b<=8;b++) {
                    double dx=a*radius/8.0,dz=b*radius/8.0;
                    double effect=CinematicLandformMath.relief(kind,dx,dz,radius,hash);
                    yes(Double.isFinite(effect),"No NaN / infinite relief");
                    yes(effect<=3.8&&effect>=-1.6,"Height delta bounded");
                    yes(near(effect,CinematicLandformMath.relief(kind,dx,dz,radius,hash)),
                            "Same seed and absolute position is deterministic");
                }
            }
        }
        double radius=CinematicLandformMath.radius(h);
        double mesa=CinematicLandformMath.relief(2,0,0,radius,h);
        double mesaEdge=CinematicLandformMath.relief(2,radius*0.60,0,radius,h);
        yes(mesa-mesaEdge>2.5,"Mesa must form a large flat-topped escarpment");
        double canyon=CinematicLandformMath.relief(3,0,0,radius,h);
        double canyonBank=CinematicLandformMath.relief(3,0,radius*0.35,radius,h);
        yes(canyon<0&&canyonBank>0,"Canyon needs incised floor and high shoulders");
        double calderaFloor=CinematicLandformMath.relief(4,0,0,radius,h);
        double calderaRim=CinematicLandformMath.relief(4,0.59*radius,0,radius,h);
        yes(calderaFloor<0&&calderaRim>1.8,
                "Volcanic caldera needs lowered floor and raised broken rim");
        double glacierFloor=CinematicLandformMath.relief(5,0,0,radius,h);
        double siteAngle=Math.floorMod(h>>>45,6284L)/1000.0;
        double glacierShoulder=CinematicLandformMath.relief(5,
                -550*Math.sin(siteAngle),550*Math.cos(siteAngle),radius,h);
        yes(glacierFloor<0&&glacierShoulder>0.7,
                "U valley needs broad trough floor and lifted shoulders");
        double towerMax=0;
        for(int x=-900;x<=900;x+=24)for(int z=-900;z<=900;z+=24)
            towerMax=Math.max(towerMax,CinematicLandformMath.relief(0,x,z,radius,h));
        yes(towerMax>2.1,"Sandstone macro pillars must rise ~250 blocks, not five");
        System.out.println("CINEMATIC_MACRO_RELIEF_PASS: six forms, climate, towers, karst, mesas, gorges, calderas, glacier valleys, seams and ocean safety");
    }
}
